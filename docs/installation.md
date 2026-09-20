# 安装与更新

> [文档首页](README.md) · [使用与设置](usage.md) · [故障排查](troubleshooting.md) · [兼容性与限制](compatibility.md) · [开发指南](development.md)

本文只介绍如何选择正确的安装包、安装插件和更新版本。功能怎么使用请看 [使用与设置](usage.md)，平台差异见 [兼容性与限制](compatibility.md)。

## 选择安装包

Releases 会按平台分别提供插件包：

| 平台 | 安装包 | 状态 |
| --- | --- | --- |
| Windows x64 | `dynamic-lyrics-island-for-spw-*-windows-x64.zip` | 完整支持 |
| Linux x64 | `dynamic-lyrics-island-for-spw-*-linux-x64.zip` | 实验性支持 |

请从 [Releases](https://github.com/GaBoron/Dynamic-Lyrics-Island-for-SPW/releases/latest) 下载对应平台的 **非 `-source` ZIP**。

> [!WARNING]
> 不要把 Windows 包装到 Linux，也不要导入 `-source` 源码包。平台能力和打包内容不同。

## Windows

### 系统要求

- Windows 10 / 11 x64；
- 支持 SPW Workshop API 0.1.0-dev20 的 SPW 版本；
- Java 由 SPW 提供，不需要单独安装。

词岛本身可以在不满足实时频谱条件时继续工作。Windows 实时频谱额外需要：

- Windows build 20348 或更高；
- 可用的 .NET Framework 4.x 运行环境；
- 共享模式音频输出。

详细限制见 [兼容性与限制](compatibility.md#实时频谱与音频输出)。

### 推荐安装方式

1. 下载最新 Windows x64 插件 ZIP；
2. 打开 SPW 的创意工坊／插件管理；
3. 导入 ZIP；
4. 启用 Dynamic Lyrics Island for SPW；
5. 播放一首歌曲确认词岛已经出现。

### 手动安装

如果当前 SPW 没有插件 ZIP 导入入口：

1. 完全退出 SPW；
2. 将插件 ZIP 放入：

```text
%APPDATA%\Salt Player for Windows\workshop\plugins\
```

3. 重新启动 SPW；
4. 在插件管理中确认插件已启用。

如果插件没有出现在列表中，请优先检查文件位置和安装包平台，不要直接继续排查词岛显示设置。

---

## Linux

Linux x64 目前属于实验性支持。

运行环境需要：

- 支持 Java / Swing 插件的 SPW Linux 宿主；
- `/usr/bin/python3`；
- GTK 3；
- X11 或 XWayland 桌面环境。

界面使用 SPW 自带的 JVM，不需要另外安装 Java。Python helper 只使用标准库，不需要 PyGObject。

安装步骤：

1. 下载最新 Linux x64 插件 ZIP；
2. 在 SPW 的插件管理中导入并启用；
3. 播放一首歌曲确认词岛出现；
4. 快捷设置请从系统托盘打开。

Linux 目前没有 Windows 的实时进程频谱、鼠标穿透和前台全屏检测等能力，完整差异请查看 [兼容性与限制](compatibility.md#linux-实验性支持)。

---

## 更新

正常更新不需要先清空设置。

### 通过 SPW 导入

1. 下载新版本中与你的平台对应的 ZIP；
2. 在 SPW 中重新导入；
3. 必要时重启 SPW；
4. 确认插件版本已更新。

### Windows 手动安装

如果一直使用 `%APPDATA%\Salt Player for Windows\workshop\plugins\`：

1. 先退出 SPW；
2. 用新版插件包替换旧版本；
3. 重新启动 SPW。

> [!TIP]
> 更新后如果只是词岛位置或显示状态异常，先使用插件设置中的“找回词岛”，通常不需要删除配置文件重装。

## 安装完成后

建议用一首 SPW 已经能够正常显示歌词的歌曲做第一次测试。

- 词岛出现，但没有歌词：查看 [故障排查 → 歌词](troubleshooting.md#歌词与时序)；
- 词岛完全不出现：查看 [故障排查 → 安装与显示](troubleshooting.md#安装与显示)；
- 实时频谱不动：查看 [故障排查 → 封面、进度与频谱](troubleshooting.md#封面进度与频谱)。

本插件只显示 SPW 已加载的歌词，不负责联网搜索歌词。若需要自动获取逐字歌词，可以搭配 [SPW-Lyrics](https://github.com/GaBoron/SPW-Lyrics)。