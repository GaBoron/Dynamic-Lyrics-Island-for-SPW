# 安装与更新

## 系统要求

- Windows 10 / 11
- 兼容 SPW Workshop API 0.1.0-dev20 的 SPW 版本
- 实时频谱需要 Windows build 20348 或更高版本，以及共享模式音频输出

旧版 Windows 仍可显示歌词。Java 由 SPW 提供，频谱辅助程序使用系统自带的 .NET Framework 4.x。

## 安装

1. 从 [Releases](https://github.com/GaBoron/Dynamic-Lyrics-Island-for-SPW/releases/latest) 下载 `dynamic-lyrics-island-for-spw-*.zip`。
2. 在 SPW 的创意工坊／插件管理中导入 ZIP，并启用 Dynamic Lyrics Island for SPW。
3. 播放一首带歌词的歌曲。

如果当前 SPW 没有导入入口：退出 SPW，将 ZIP 放入 `%APPDATA%\Salt Player for Windows\workshop\plugins\`，再重新启动 SPW。

更新时下载新版 ZIP，并在 SPW 中重新导入；如果使用手动安装方式，请退出 SPW 后替换原插件包，再重新启动。

若需要自动获取逐字歌词，可搭配 [SPW-Lyrics](https://github.com/GaBoron/SPW-Lyrics)。安装后没有歌词、词岛消失或频谱不动时，请查看 [常见问题](troubleshooting.md)。
