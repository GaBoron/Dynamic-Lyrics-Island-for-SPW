# 构建

需要 JDK 21 和网络连接。Windows x64 构建还需要系统 .NET Framework 4.x 的 C# 编译器。版本号与开源地址的唯一来源为根目录 `gradle.properties`。开源地址会写入插件清单和运行时资源。Gradle Wrapper 验证发行版 SHA-256，构建不会自动安装插件或启动 SPW。

```powershell
.\gradlew.bat pluginWindows --no-daemon
```

Linux x64：

```bash
./gradlew pluginLinux --no-daemon
```

输出位于 `build/distributions/`，文件名带有 `windows-x64` 或 `linux-x64` 平台后缀。`plugin` 会选择当前主机对应的任务。安装使用不带 `-source` 后缀的 ZIP；它包含 `classes/`、`lib/`、许可说明及 `source/` 中的对应源码 ZIP。Windows 构建中的 `buildSpectrum` 从 `native/*.cs` 编译 `classes/native/spw-spectrum.exe`，不需要另装 .NET SDK；Linux 包不包含该 Windows helper，实时频谱会安全降级为不可用。API、Kotlin 标准库和 PF4J 由 SPW 提供，不重复打包；随包附带 JNA、JNA Platform 和用于只读元数据提取的 Jaudiotagger 3.0.1；后者的原版源码 JAR 随包放入 `source/`，LGPL-2.1 许可放入 `licenses/`。构建与运行不下载或使用 Lyricify 程序。

Linux 使用独立界面进程：宿主中的 `LinuxIslandProcess` 在后台线程每 100 ms 发送播放快照，封面、完整歌词及设置仅在变化时传送；`RemotePlayback` 使用单调时钟补间。匿名双向管道传递受控命令，插件卸载时销毁子进程。Windows 保持原有进程内实现。

`src/linux/resources/native/island-linux.py` 使用 Python 标准库 ctypes 调用标准 JNI Invocation API，加载宿主 `java.home/lib/server/libjvm.so`，因此兼容不包含 `bin/java` 的 jpackage 运行库。只在子 JVM 设置 XRender 和禁用 Java2D OpenGL。`BufferedIslandWindow` 在 ARGB 缓冲完成绘制后一次性提交，`X11InputRegion` 只设置 ShapeInput，不改变可见窗口轮廓。

Linux 托盘通过同一脚本的 `tray` 模式调用 AppIndicator（不可用时回退 GTK StatusIcon），将 GTK 菜单直接挂到托盘上。`GtkTray` 只在菜单内容或勾选变化时发送更新；后台线程读写管道，GTK 主循环负责更新菜单，菜单打开期间延后替换。词岛区域右键暂缓，旧 `menu` 模式保留供后续使用。GTK 自己管理样式、勾选、键盘和菜单外部点击；不再使用 Swing heavyweight popup 或全局点击轮询。脚本仅依赖 `/usr/bin/python3` 标准库和系统 GTK 3 动态库，不需要 PyGObject。GTK 与 AWT 分处不同进程，避免初始化和线程冲突。Linux 仍不支持进程音频频谱、全局穿透和全屏检测，配置页保持隐藏对应选项。

## 模块边界

| 目录 | 职责 |
| --- | --- |
| `core/` | 不依赖 SPW 或窗口的歌词数据、单调时钟补间、播放状态、配置模型 |
| `host/` | SPW / PF4J 生命周期、歌词回调映射、配置保存与通知、播放命令 |
| `ui/` | 字体回退与共享文字塑形、歌词块测量与居中、高亮、词岛位置、窗口动画、菜单和托盘 |
| `platform/` | JNA 实现的 Windows 鼠标穿透、全屏判断、系统主题读取及进程频谱管道生命周期 |
| 根目录 `native/` | WASAPI 进程回环、双声道 FFT 和四频段输出；不保存 PCM |

只有 `IslandPlugin` 保存一个插件运行实例，供 PF4J 独立创建的扩展访问。回调只更新同步时序模型；Windows Swing EDT 直接读取快照，Linux 后台管道同步到独立界面进程。`stop()` 解除静态引用及监听后销毁 UI。`META-INF/extensions.idx` 显式注册扩展，不依赖注解处理器生成索引。

全部设置由 `preference_config.json` 声明，SPW 原生渲染开关、外观列表、圆角滑杆和字体／数值输入。SPW 的列表配置只支持静态选项，因此字体继续使用文本输入；托盘仅提供高频快捷开关，不复制完整设置页。`HostSettings` 将滑杆产生的有效小数规范化为整数，并兼容旧整数字符串和浮点存储。SPW 配置监听仅作为快速通知，另有 250 ms 文件同步；有效配置读入快照后再通知界面，空文件或解析失败不覆盖最后一次有效快照。保存前刷新已有文件以保留其他键值。

`AmllMotion` 按曲目绝对毫秒采样 AMLL 移植曲线。字体塑形结果同时用于测量和逐字符变换。`core/PerformanceProfile` 是持续渲染、后台工作和可选宿主集成的统一性能预算；低性能模式约以 15 FPS 刷新，由 `TimedKaraokeBoundary` 保留每个歌词单元的真实时间并只计算当前边界，停用布局过渡、逐字符轮廓、位移、缩放、辉光、音频捕获和私有歌词轴探测，改用轻量模拟频谱，并降低全屏与置顶检查频率。以后新增持续性高成本功能时，应同时定义其低性能模式行为。按 [NOTICE](../NOTICE) 保留 AGPL 模块的许可和来源。

频谱使用当前 JVM 的进程 ID，包含其子进程，不读取系统混音或麦克风。辅助进程将 44.1 kHz、16 位立体声 PCM 按 2048 帧加 Hann 窗做 FFT，各声道先算功率后合并，按窗口平方增益还原各频段 RMS。四个频段共用一个缓慢调整的音量基准（上升 250 ms、下降 4 s），保持频段强弱差异；非线性柱高映射让持续强音约处于半高，保留瞬态空间。静音重置基准，噪声门限防止底噪被自动放大。这是音乐可视化，不作为绝对音量表使用。

管道仅传四个归一化频段，每批约 46 ms；350 ms 无新数据自动归零。选择封面或开启低性能模式时不启动频谱辅助进程；低性能模式由 `SyntheticSpectrum` 根据播放时间生成低成本动画。运行中切换模式会按需回收或重启捕获进程，停用插件也会关闭输入管道并回收该辅助进程。

## 运行边界

公开 API 未定义歌词回调与播放进度通知的严格顺序，也没有逐字单元相对时间戳的另外说明；本插件按 API 的“时间戳”语义将其当作曲目绝对毫秒。构建成功只证明源码能够生成插件包，不代表真实 SPW、音频设备或多显示器环境中的运行结果。

元数据由 `TrackMetadataLoader` 在单个后台线程读取，最多保留一个待处理请求；代次检查丢弃切歌或停止后的旧结果。`LocalTrackMetadata` 只读音频标签与同目录封面，不联网、不改写文件；封面解码按尺寸抽样，失败时独立回退，未知时长不推测进度上限。`CoverColorExtractor` 按色相家族统计占比，优先排除近黑、近白像素并在没有彩色候选时回退。`IslandPalette` 分别控制三类颜色。

首次安装或重启若错过公开曲目加载回调，`CurrentTrackRecovery` 在后台短时重试，通过 `HostPlaybackProbe` 只读当前曲目对象并立即交给同一个元数据加载器；公开回调先到、成功恢复或达到重试上限后都会停止。该探测不在 Swing EDT 或绘制循环中运行。

`IslandSurface` 提供稳定透明画布，动画只改变子面板尺寸，每帧清除整张画布。窗口输入区域跟随实际轮廓并保留抗锯齿边缘，透明空白不截获鼠标。`IslandPlacement` 根据词岛中心所在的屏幕三等分区域自动选择九宫格锚点，水平和垂直伸缩都围绕该点进行；拖动期间锁定起始锚点，松手后一次性保存新锚点。只要画布仍能容纳词岛，切换锚点时不移动原生透明窗口，避免分区切换重绘闪烁。拖动固定以 8 ms 定时刷新，不受低性能模式的常规 15 FPS 限制。刘海外形由垂直锚点决定：顶部使用原方向、底部垂直翻转、中部使用胶囊；底部区域的播放控件向上展开。展开高度与窗口尺寸同步插值。`PlaybackProgress` 独立管理拖动预览，在松开时调用公开 `seekTo`，切歌或时长不可用时取消。

## 上游依据

- [SPW API 0.1.0-dev20](https://github.com/Moriafly/spw-workshop-api/tree/0.1.0-dev20)：播放扩展、配置与插件上下文；启动曲目恢复和实验性完整歌词轴读取是失败即回退的可选宿主探测。
- [Lyricify 原创许可声明](https://github.com/WXRIW/Lyricify-App#lyricify-原创)：灵动词岛概念与 CC BY-SA 4.0 署名。
- [Lyricify 名词](https://docs.lyricify.app/lyricify-4/terms/)：使用“灵动词岛”名称。
- [AMLL DOM 歌词动画](https://github.com/amll-dev/applemusic-like-lyrics/blob/58ccd3ffae7ec4e9a6d1cdb0dd88ac8c767f68a8/packages/core/src/lyric-player/dom/lyric-line.ts)：逐字抬升、长音强调及 AGPL-3.0 来源。
- [Microsoft 进程音频回环接口](https://learn.microsoft.com/en-us/windows/win32/api/audioclientactivationparams/ns-audioclientactivationparams-audioclient_process_loopback_params)：目标进程及子进程的音频捕获。

新增功能应保持许可证与分发源码完整。

## Linux 桌面回归检查

以下均为显式运行的检查，不随 `pluginLinux` 打包执行：

- `./gradlew linuxProcessCheck --no-daemon`：主动阻塞宿主 EDT，检查子进程仍提交完整帧。可通过 `-PtestRuntime=/path/to/runtime` 使用 SPW 自带的 JVM。
- `./gradlew linuxDesktopCheck --no-daemon`：检查透明画布、窗口形状和帧调度，组件绘制图保存在 `build/desktop-check/`。
- `python3 src/test/python/linux_gtk_check.py select` / `outside`：实际显示 GTK 菜单，检查命令回传或外部点击关闭并保存菜单绘制图。`outside` 会移动和点击鼠标，运行时不要操作其他应用。

这些检查不替代真实 SPW、具体合成器和多显示器环境下的用户验证。
