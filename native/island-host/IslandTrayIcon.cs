// SPDX-License-Identifier: GPL-3.0-only
using System.Runtime.InteropServices;

namespace IslandHost;

/** Keeps quick controls reachable when the island is hidden or click-through is enabled. */
internal sealed class IslandTrayIcon : IDisposable
{
    public const uint CallbackMessage = 0x8001;
    private const uint NimAdd = 0, NimDelete = 2;
    private const uint NifMessage = 1, NifIcon = 2, NifTip = 4;
    private const uint WmLeftDoubleClick = 0x0203, WmRightButtonUp = 0x0205;
    private const uint WmContextMenu = 0x007B;
    private const uint ImageIcon = 1, LoadFromFile = 0x0010;
    private readonly nint _window;
    private readonly uint _taskbarCreated;
    private readonly nint _icon;
    private bool _added;

    public IslandTrayIcon(nint window)
    {
        _window = window;
        _taskbarCreated = RegisterWindowMessageW("TaskbarCreated");
        _icon = LoadIcon();
    }

    public void Add()
    {
        var data = Data();
        _added = Shell_NotifyIconW(NimAdd, ref data);
        if (!_added) Console.Error.WriteLine("[SPW Island] 无法显示托盘入口");
    }

    public bool Handle(uint message, nint parameter, Action toggle, Action showMenu)
    {
        if (_taskbarCreated != 0 && message == _taskbarCreated)
        {
            if (_added)
            {
                var data = Data();
                Shell_NotifyIconW(NimDelete, ref data);
            }
            _added = false;
            Add();
            return true;
        }
        if (message != CallbackMessage) return false;
        switch ((uint)parameter.ToInt64())
        {
            case WmLeftDoubleClick: toggle(); break;
            case WmRightButtonUp:
            case WmContextMenu: showMenu(); break;
        }
        return true;
    }

    public void Dispose()
    {
        if (_added)
        {
            var data = Data();
            Shell_NotifyIconW(NimDelete, ref data);
            _added = false;
        }
        if (_icon != 0 && _ownsIcon) DestroyIcon(_icon);
    }

    private bool _ownsIcon;

    private nint LoadIcon()
    {
        var path = Path.Combine(AppContext.BaseDirectory, "Assets", "AppIcon.ico");
        var icon = LoadImageW(0, path, ImageIcon, 0, 0, LoadFromFile);
        _ownsIcon = icon != 0;
        return icon != 0 ? icon : LoadIconW(0, new nint(32512));
    }

    private NotifyIconData Data() => new()
    {
        Size = (uint)Marshal.SizeOf<NotifyIconData>(),
        Window = _window,
        Id = 1,
        Flags = NifMessage | NifIcon | NifTip,
        Callback = CallbackMessage,
        Icon = _icon,
        Tip = "灵动词岛 for SPW",
        Info = "",
        InfoTitle = ""
    };

    [StructLayout(LayoutKind.Sequential, CharSet = CharSet.Unicode)]
    private struct NotifyIconData
    {
        public uint Size;
        public nint Window;
        public uint Id, Flags, Callback;
        public nint Icon;
        [MarshalAs(UnmanagedType.ByValTStr, SizeConst = 128)] public string Tip;
        public uint State, StateMask;
        [MarshalAs(UnmanagedType.ByValTStr, SizeConst = 256)] public string Info;
        public uint Version;
        [MarshalAs(UnmanagedType.ByValTStr, SizeConst = 64)] public string InfoTitle;
        public uint InfoFlags;
        public Guid Guid;
        public nint BalloonIcon;
    }

    [DllImport("shell32.dll", CharSet = CharSet.Unicode, SetLastError = true)]
    [return: MarshalAs(UnmanagedType.Bool)]
    private static extern bool Shell_NotifyIconW(uint message, ref NotifyIconData data);
    [DllImport("user32.dll", CharSet = CharSet.Unicode)]
    private static extern uint RegisterWindowMessageW(string message);
    [DllImport("user32.dll", CharSet = CharSet.Unicode, SetLastError = true)]
    private static extern nint LoadImageW(nint instance, string name, uint type, int width, int height, uint flags);
    [DllImport("user32.dll", CharSet = CharSet.Unicode)]
    private static extern nint LoadIconW(nint instance, nint name);
    [DllImport("user32.dll")]
    [return: MarshalAs(UnmanagedType.Bool)]
    private static extern bool DestroyIcon(nint icon);
}
