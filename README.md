# Dynamic Lyrics Island for SPW

> 把 SPW 的歌词变成桌面顶部的灵动词岛。

[![Release](https://img.shields.io/github/v/release/GaBoron/Dynamic-Lyrics-Island-for-SPW?label=Release)](https://github.com/GaBoron/Dynamic-Lyrics-Island-for-SPW/releases/latest)
[![Windows](https://img.shields.io/badge/Windows-10%20%2F%2011-0078D4?logo=windows)](#系统要求)
[![License](https://img.shields.io/badge/License-GPL--3.0%20%2B%20AGPL--3.0-blue)](#许可与致谢)

胶囊／刘海外观、逐字高亮、翻译、实时频谱、专辑封面，以及悬停播放控制——全部在 SPW 插件设置中调整。

| 歌词 | 外观 | 交互 |
| --- | --- | --- |
| 逐字动画与翻译 | 胶囊／顶部刘海 | 播放、暂停与进度跳转 |
| 日韩字体回退 | 频谱／专辑封面 | 拖动、吸附与鼠标穿透 |
| 长歌词平滑滚动 | 封面取色 | 全屏自动隐藏 |

## 🚀 安装

1. 从 [Releases](https://github.com/GaBoron/Dynamic-Lyrics-Island-for-SPW/releases/latest) 下载 `dynamic-lyrics-island-for-spw-*.zip`。
2. 在 SPW 的创意工坊／插件管理中导入 ZIP，并启用 **Dynamic Lyrics Island for SPW**。
3. 播放一首带歌词的歌曲；若在播放中途启用插件，请切换一次歌曲。

如果当前 SPW 没有导入入口：退出 SPW，将 ZIP 放入 `%APPDATA%\Salt Player for Windows\workshop\plugins\`，再重新启动 SPW。

```mermaid
flowchart LR
    A[下载插件 ZIP] --> B[导入并启用]
    B --> C[播放带歌词歌曲]
    C --> D[在插件设置中定制词岛]
```

### 系统要求

- Windows 10 / 11
- 兼容 **SPW Workshop API 0.1.0-dev20** 的 SPW 版本
- 实时频谱需要 Windows build 20348 或更高版本，以及共享模式音频输出

旧版 Windows 仍可显示歌词。Java 由 SPW 提供，频谱辅助程序使用系统自带的 .NET Framework 4.x。

## 🖱️ 常用操作

| 操作 | 效果 |
| --- | --- |
| 悬停词岛 | 展开歌曲信息、播放按钮和进度条 |
| 双击文字区域 | 播放／暂停 |
| 点击或拖动进度条 | 跳转播放位置 |
| 拖动词岛 | 调整并保存位置；靠近顶边自动吸附 |
| 右击词岛 | 打开显示、外观、穿透和位置选项 |
| 在 SPW 内按 `Ctrl+Shift+D` | 显示／隐藏词岛（非全局快捷键） |
| 托盘右键 | 显示、解锁或重置位置 |
| 插件设置 → 找回词岛 | 恢复显示、关闭穿透并移回主屏幕顶部 |

> [!TIP]
> 开启鼠标穿透后，鼠标事件会传给下方窗口。需要调整词岛时，可从托盘菜单或 SPW 插件设置中解除穿透。

## ✨ 功能说明

- **歌词动画**：逐字歌词按 SPW 时间戳平滑高亮；普通逐行歌词保持整行显示，长歌词自动平移。
- **AMLL 动效**：支持逐字抬升、长音强调、辉光和弹性过渡，也可开启“减少动画”。
- **翻译与字体**：当前行有翻译时显示第二行；所选字体缺字时自动回退到系统日韩字体。
- **频谱与封面**：左侧可显示 SPW 进程的四频段实时能量，或本地音频内嵌／同目录 `cover`、`folder` 图片。
- **封面取色**：可分别应用到歌词高亮、背景和频谱；颜色会自动调整明暗以保持可读性。
- **桌面适配**：支持顶部吸附、多屏找回、全屏隐藏和暂停隐藏。

插件只接收 SPW 已加载的歌词，不搜索、不上传，也不修改歌曲或歌词文件。SPW 当前仅提供正在播放的歌词行，因此无法预告下一句或显示完整歌词列表。

## 🛠️ 常见问题

<details>
<summary><strong>没有歌词或歌名</strong></summary>

先确认 SPW 自己能够显示歌词，再切换一次歌曲。歌名来自歌词加载回调；若其他插件提前返回歌词，SPW 是否继续通知本插件取决于宿主调用顺序。
</details>

<details>
<summary><strong>看不到封面</strong></summary>

插件只读本地音频内嵌封面，并回退到同目录的 `cover.jpg`、`cover.png`、`folder.jpg` 或 `folder.png`。网络歌曲或不可读文件会显示唱片占位图。
</details>

<details>
<summary><strong>高亮偏早或偏晚</strong></summary>

在插件设置中调整“逐字高亮偏移”：正值使高亮提前，负值使高亮延后。歌词换行时机仍由 SPW 决定。
</details>

<details>
<summary><strong>频谱不动</strong></summary>

确认歌曲正在播放且未静音。进程回环不支持低于 Windows build 20348 的系统，独占／ASIO 输出也可能无法捕获；请尝试共享模式输出。
</details>

<details>
<summary><strong>词岛消失</strong></summary>

检查“显示词岛”“暂停时隐藏”和“全屏时隐藏”。动态壁纸可能被识别为全屏窗口；多屏位置异常时使用“找回词岛”。
</details>

## 📚 开发与构建

构建方式、模块边界和宿主运行限制见 [开发说明](docs/development.md)。

## 📄 许可与致谢

**灵动词岛原创：Lyricify / WXRIW（XY Wang）。** 本项目依据 [CC BY-SA 4.0](https://github.com/WXRIW/Lyricify-App#lyricify-原创)独立实现，不是 Lyricify 官方产品，也不包含 Lyricify 程序或词库。

AMLL 动画移植模块采用 **AGPL-3.0-only**，其他程序采用 **GPL-3.0-only**；视觉、交互改编及文档采用 **CC BY-SA 4.0**。SPW API 与改编构建示例保留 Apache-2.0 声明，JNA 采用其 Apache-2.0 许可。

详见 [NOTICE](NOTICE)、[第三方许可说明](THIRD_PARTY_NOTICES.md)和 [LICENSE](LICENSE)。插件包包含许可文件与对应完整源码，转发时请一并保留。
