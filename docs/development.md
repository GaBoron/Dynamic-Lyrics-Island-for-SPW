# 构建与验证

需要 Windows x64、JDK 21、系统 .NET Framework 4.x 的 C# 编译器和网络连接。版本号与开源地址的唯一来源为根目录 `gradle.properties`。开源地址会写入插件清单和运行时资源。Gradle Wrapper 验证发行版 SHA-256，构建不会自动安装插件或启动 SPW。

```powershell
.\gradlew.bat test plugin --no-daemon
```

输出位于 `build/distributions/`。安装使用不带 `-source` 后缀的 ZIP；它包含 `classes/`、`lib/`、许可说明及 `source/` 中的对应源码 ZIP。`buildSpectrum` 从 `native/*.cs` 编译 `classes/native/spw-spectrum.exe`，不需要另装 .NET SDK。API、Kotlin 标准库和 PF4J 由 SPW 提供，不重复打包；随包附带 JNA、JNA Platform 和用于只读元数据提取的 Jaudiotagger 3.0.1；后者的原版源码 JAR 随包放入 `source/`，LGPL-2.1 许可放入 `licenses/`。构建与运行不下载或使用 Lyricify 程序。

## 本地预览

```powershell
.\gradlew.bat preview --no-daemon
```

预览使用原创示例歌词，不连接 SPW。点击预览控制窗口可暂停、切换示例、模拟跳转，并测试胶囊、刘海和设置变化。关闭控制窗口退出预览，设置只保留在内存中。

单元测试还会把实际 Java2D 渲染结果保存到 `build/preview/`，用于检查文字裁切、翻译布局和透明边缘；这不是真实 SPW 运行截图。

Windows 上运行 `.\gradlew.bat smoke --no-daemon` 可在独立 JVM 中模拟宿主，验证隐藏窗口的两轮启停、歌词回调路由、鼠标穿透样式切换及窗口／托盘／监听的释放，不会安装插件或连接真实 SPW。

## 模块边界

| 目录 | 职责 |
| --- | --- |
| `core/` | 不依赖 SPW 或窗口的歌词数据、单调时钟补间、播放状态、配置模型 |
| `host/` | SPW / PF4J 生命周期、歌词回调映射、配置保存与通知、播放命令 |
| `ui/` | 字体回退与共享文字塑形、歌词块测量与居中、高亮、词岛位置、窗口动画、菜单和托盘 |
| `platform/` | JNA 实现的 Windows 鼠标穿透、全屏判断及进程频谱管道生命周期 |
| 根目录 `native/` | WASAPI 进程回环、双声道 FFT 和四频段输出；不保存 PCM |

只有 `IslandPlugin` 保存一个插件运行实例，供 PF4J 独立创建的扩展访问。回调只更新同步时序模型；Swing EDT 读取快照。`stop()` 解除静态引用及监听后销毁 UI。`META-INF/extensions.idx` 显式注册扩展，不依赖注解处理器生成索引。

全部设置由 `preference_config.json` 声明，SPW 原生渲染开关、外观列表和字体／数值输入。`HostSettings` 启动时将旧版数值配置转换为文本输入项使用的字符串，保留数值；右键和托盘使用同一存储。SPW 配置监听仅作为快速通知，另有 250 ms 文件同步；有效配置读入快照后再通知界面，空文件或解析失败不覆盖最后一次有效快照。保存前刷新已有文件以保留其他键值。数值兼容旧整数字符串和浮点存储。

`AmllMotion` 按曲目绝对毫秒采样 AMLL 移植曲线。字体塑形结果同时用于测量和逐字符变换，减少动画选项停用位移、缩放及辉光，保留逐字颜色。按 [NOTICE](../NOTICE) 保留 AGPL 模块的许可和来源。

频谱使用当前 JVM 的进程 ID，包含其子进程，不读取系统混音或麦克风。辅助进程将 44.1 kHz、16 位立体声 PCM 按 2048 帧加 Hann 窗做 FFT，各声道先算功率后合并，按窗口平方增益还原各频段 RMS。四个频段共用一个缓慢调整的音量基准（上升 250 ms、下降 4 s），保持频段强弱差异；非线性柱高映射让持续强音约处于半高，保留瞬态空间。静音重置基准，噪声门限防止底噪被自动放大。这是音乐可视化，不作为绝对音量表使用。

管道仅传四个归一化频段，每批约 46 ms；350 ms 无新数据自动归零。停用插件会关闭输入管道并回收该辅助进程。`test` 自动包含 `testSpectrum`，用多频率、多音量 PCM 检查频段隔离、强弱节拍、反相立体声、静音及安静曲目恢复；也可以单独运行 `.\gradlew.bat testSpectrum --no-daemon`。

## SPW 验收

1. 导入 ZIP，确认插件名称、版本、设置页及“关于与许可”可用。
2. 切换歌曲，分别播放普通 LRC、带翻译和逐字歌词，包含日文及韩文。核对当前行一致、行尾不裁切、上下居中，间奏不闪现歌名。
3. 暂停至少五秒再恢复；前后拖动播放进度；快速切歌。确认句间空隙保持上一句，歌曲开头显示歌名；切歌或跳转后不残留之前位置的歌词。
4. 如同时使用歌词来源插件，确认两者启用时歌词加载与词岛通知都正常；记录宿主版本与插件加载顺序。
5. 在 SPW 插件配置页逐项测试开关、字体、外观、整数输入及三个封面取色开关；从旧版本升级时核对数值保留。改完后拖动词岛并重启 SPW，确认保存值不被覆盖。确认“项目源代码”链接可用。
6. 开启鼠标穿透，点击原来词岛位置应操作下面窗口；从托盘和 SPW“找回词岛”恢复操作。
7. 在词岛所在屏幕进入／退出全屏，确认隐藏及恢复；另一个屏幕全屏不应隐藏词岛。
8. 分别在 100%、150%、200% 缩放与负坐标副屏拖动，断开原显示器后找回词岛。
9. 停用／重新启用插件，确认无重复窗口、托盘图标、按键响应或配置监听。
10. 使用共享音频输出播放音乐，确认四条频谱随音频变化，静音或暂停后归零；仅播放其他应用声音时不应驱动频谱。停用后任务管理器不应残留 `spw-spectrum.exe`。

公开 API 未定义歌词回调与播放进度通知的严格顺序，也没有逐字单元相对时间戳的另外说明；本插件按 API 的“时间戳”语义将其当作曲目绝对毫秒。此点和热启用时的事件重放需要用真实 SPW 验证。编译、离屏截图或模拟宿主检查不能代替该验收。

元数据由 `TrackMetadataLoader` 在单个后台线程读取，最多保留一个待处理请求；代次检查丢弃切歌或停止后的旧结果。`LocalTrackMetadata` 只读音频标签与同目录封面，不联网、不改写文件；封面解码按尺寸抽样，失败时独立回退，未知时长不推测进度上限。`IslandPalette` 分别控制三类颜色。

`IslandSurface` 提供稳定透明画布，动画只改变子面板尺寸，每帧清除整张画布。窗口输入区域跟随实际轮廓并保留抗锯齿边缘，透明空白不截获鼠标。展开高度与窗口尺寸同步插值。`PlaybackProgress` 独立管理拖动预览，在松开时调用公开 `seekTo`，切歌或时长不可用时取消。

新增验收：使用不同封面的 MP3、FLAC、M4A 和无封面歌曲，逐一切换三个取色选项；检查时长与 SPW 一致、点击／拖动可跳转，拖动中切歌不会跳转新歌。快速交替长短歌词和悬停展开，检查胶囊、刘海的边缘连续、无残影，并确认词岛外透明区域能操作下方窗口。

## 上游依据

- [SPW API 0.1.0-dev20](https://github.com/Moriafly/spw-workshop-api/tree/0.1.0-dev20)：播放扩展、配置与插件上下文；未依赖未公开接口。
- [Lyricify 原创许可声明](https://github.com/WXRIW/Lyricify-App#lyricify-原创)：灵动词岛概念与 CC BY-SA 4.0 署名。
- [Lyricify 名词](https://docs.lyricify.app/lyricify-4/terms/)：使用“灵动词岛”名称。
- [AMLL DOM 歌词动画](https://github.com/amll-dev/applemusic-like-lyrics/blob/58ccd3ffae7ec4e9a6d1cdb0dd88ac8c767f68a8/packages/core/src/lyric-player/dom/lyric-line.ts)：逐字抬升、长音强调及 AGPL-3.0 来源。
- [Microsoft 进程音频回环接口](https://learn.microsoft.com/en-us/windows/win32/api/audioclientactivationparams/ns-audioclientactivationparams-audioclient_process_loopback_params)：目标进程及子进程的音频捕获。

新增功能应保持许可证与分发源码完整。
