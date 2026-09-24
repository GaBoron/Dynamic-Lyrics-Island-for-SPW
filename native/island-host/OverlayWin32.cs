// SPDX-License-Identifier: GPL-3.0-only
using System.Runtime.InteropServices;

namespace IslandHost;

internal static class OverlayWin32
{
    internal const int WsPopup = unchecked((int)0x80000000);
    internal const int ExLayered = 0x80000;
    internal const int ExTransparent = 0x20;
    internal const int ExNoActivate = 0x08000000;
    internal const int ExToolWindow = 0x80;
    internal const int ExTopmost = 0x8;
    internal const int GwlExStyle = -20;
    internal const uint SwpNoActivate = 0x10;
    internal const uint SwpShowWindow = 0x40;
    internal const uint SwpNoZOrder = 0x4;
    internal const uint LwaColorKey = 0x1;
    internal const uint LwaAlpha = 0x2;
    internal const uint MonitorDefaultToNearest = 2;
    internal static readonly nint HwndTopmost = new(-1);

    [StructLayout(LayoutKind.Sequential)]
    internal struct Point { public int X, Y; }

    [StructLayout(LayoutKind.Sequential)]
    internal struct Rect
    {
        public int Left, Top, Right, Bottom;
        public readonly OverlayRect Overlay => new(Left, Top, Right - Left, Bottom - Top);
    }

    [StructLayout(LayoutKind.Sequential, CharSet = CharSet.Unicode)]
    internal struct MonitorInfo
    {
        public int Size;
        public Rect Monitor, Work;
        public uint Flags;
        [MarshalAs(UnmanagedType.ByValTStr, SizeConst = 32)] public string Device;
    }

    [StructLayout(LayoutKind.Sequential)]
    internal struct Message
    {
        public nint Hwnd;
        public uint Id;
        public nuint WParam;
        public nint LParam;
        public uint Time;
        public Point Position;
        public uint Private;
    }

    [StructLayout(LayoutKind.Sequential)]
    internal struct PaintStruct
    {
        public nint Hdc;
        public int Erase;
        public Rect Paint;
        public int Restore, IncUpdate;
        [MarshalAs(UnmanagedType.ByValArray, SizeConst = 32)] public byte[] Reserved;
    }

    [UnmanagedFunctionPointer(CallingConvention.Winapi)]
    internal delegate nint WindowProcedure(nint hwnd, uint message, nuint wParam, nint lParam);

    [StructLayout(LayoutKind.Sequential, CharSet = CharSet.Unicode)]
    internal struct WindowClass
    {
        public uint Size, Style;
        public WindowProcedure Procedure;
        public int ClassExtra, WindowExtra;
        public nint Instance, Icon, Cursor, Background;
        [MarshalAs(UnmanagedType.LPWStr)] public string MenuName;
        [MarshalAs(UnmanagedType.LPWStr)] public string Name;
        public nint SmallIcon;
    }

    [DllImport("user32.dll", CharSet = CharSet.Unicode, SetLastError = true)]
    internal static extern ushort RegisterClassExW(ref WindowClass windowClass);
    [DllImport("kernel32.dll", CharSet = CharSet.Unicode)] internal static extern nint GetModuleHandleW(string? module);
    [DllImport("user32.dll", CharSet = CharSet.Unicode)] internal static extern nint LoadCursorW(nint instance, nint cursor);
    [DllImport("user32.dll", CharSet = CharSet.Unicode, SetLastError = true)]
    internal static extern nint CreateWindowExW(int exStyle, string className, string name, int style,
        int x, int y, int width, int height, nint parent, nint menu, nint instance, nint parameter);
    [DllImport("user32.dll", SetLastError = true)]
    [return: MarshalAs(UnmanagedType.Bool)] internal static extern bool DestroyWindow(nint hwnd);
    [DllImport("user32.dll")] internal static extern nint DefWindowProcW(nint hwnd, uint message, nuint wParam, nint lParam);
    [DllImport("user32.dll")] internal static extern int GetMessageW(out Message message, nint hwnd, uint min, uint max);
    [DllImport("user32.dll")] internal static extern bool TranslateMessage(ref Message message);
    [DllImport("user32.dll")] internal static extern nint DispatchMessageW(ref Message message);
    [DllImport("user32.dll")] internal static extern bool PostMessageW(nint hwnd, uint message, nuint wParam, nint lParam);
    [DllImport("user32.dll")] internal static extern void PostQuitMessage(int code);
    [DllImport("user32.dll", SetLastError = true)] internal static extern nuint SetTimer(nint hwnd, nuint id, uint delay, nint callback);
    [DllImport("user32.dll")] internal static extern bool KillTimer(nint hwnd, nuint id);
    [DllImport("user32.dll")] internal static extern bool SetWindowPos(nint hwnd, nint after, int x, int y, int width, int height, uint flags);
    [DllImport("user32.dll")] internal static extern bool ShowWindow(nint hwnd, int command);
    [DllImport("user32.dll")] internal static extern bool SetLayeredWindowAttributes(nint hwnd, uint key, byte alpha, uint flags);
    [DllImport("user32.dll", EntryPoint = "GetWindowLongPtrW")] internal static extern nint GetWindowLongPtr(nint hwnd, int index);
    [DllImport("user32.dll", EntryPoint = "SetWindowLongPtrW")] internal static extern nint SetWindowLongPtr(nint hwnd, int index, nint value);
    [DllImport("user32.dll")] internal static extern bool GetWindowRect(nint hwnd, out Rect rect);
    [DllImport("user32.dll")] internal static extern bool InvalidateRect(nint hwnd, nint rect, bool erase);
    [DllImport("user32.dll")] internal static extern bool GetCursorPos(out Point point);
    [DllImport("user32.dll")] internal static extern nint MonitorFromPoint(Point point, uint flags);
    internal delegate bool MonitorVisitor(nint monitor, nint dc, nint rectangle, nint data);
    [DllImport("user32.dll")] internal static extern bool EnumDisplayMonitors(nint dc, nint rectangle,
        MonitorVisitor visitor, nint data);
    [DllImport("user32.dll", CharSet = CharSet.Unicode)] internal static extern bool GetMonitorInfoW(nint monitor, ref MonitorInfo info);
    [DllImport("user32.dll")] internal static extern nint GetForegroundWindow();
    [DllImport("user32.dll")] internal static extern nint MonitorFromWindow(nint hwnd, uint flags);
    [DllImport("user32.dll")] internal static extern uint GetDpiForWindow(nint hwnd);
    [DllImport("user32.dll", CharSet = CharSet.Unicode)] internal static extern int GetClassNameW(nint hwnd, char[] name, int count);
    [DllImport("user32.dll")] internal static extern nint SetCapture(nint hwnd);
    [DllImport("user32.dll")] internal static extern bool ReleaseCapture();
    [DllImport("user32.dll")] internal static extern nint BeginPaint(nint hwnd, out PaintStruct paint);
    [DllImport("user32.dll")] internal static extern bool EndPaint(nint hwnd, ref PaintStruct paint);
    [DllImport("user32.dll")] internal static extern nint CreatePopupMenu();
    [DllImport("user32.dll", CharSet = CharSet.Unicode)] internal static extern bool AppendMenuW(nint menu, uint flags, nuint id, string? text);
    [DllImport("user32.dll")] internal static extern int TrackPopupMenu(nint menu, uint flags, int x, int y,
        int reserved, nint hwnd, nint rectangle);
    [DllImport("user32.dll")] internal static extern bool DestroyMenu(nint menu);
    [DllImport("user32.dll")] internal static extern bool SetForegroundWindow(nint hwnd);
    [DllImport("gdi32.dll")] internal static extern nint CreateSolidBrush(uint color);
    [DllImport("gdi32.dll")] internal static extern nint SelectObject(nint dc, nint obj);
    [DllImport("gdi32.dll")] internal static extern bool DeleteObject(nint obj);
    [DllImport("gdi32.dll")] internal static extern bool Rectangle(nint dc, int left, int top, int right, int bottom);
    [DllImport("gdi32.dll")] internal static extern bool RoundRect(nint dc, int left, int top, int right, int bottom, int width, int height);
    [DllImport("gdi32.dll")] internal static extern int SetBkMode(nint dc, int mode);
    [DllImport("gdi32.dll")] internal static extern uint SetTextColor(nint dc, uint color);
    [DllImport("user32.dll", CharSet = CharSet.Unicode)] internal static extern int DrawTextW(nint dc, string text, int count, ref Rect rect, uint format);

    internal static (OverlayRect Work, OverlayRect Monitor, string Device) Screen(Point point)
    {
        var handle = MonitorFromPoint(point, MonitorDefaultToNearest);
        var info = new MonitorInfo { Size = Marshal.SizeOf<MonitorInfo>(), Device = string.Empty };
        if (handle == 0 || !GetMonitorInfoW(handle, ref info))
            throw new InvalidOperationException("无法获取显示器工作区域");
        return (info.Work.Overlay, info.Monitor.Overlay, info.Device);
    }

    internal static (OverlayRect Work, OverlayRect Monitor, string Device)? Screen(string device)
    {
        (OverlayRect Work, OverlayRect Monitor, string Device)? found = null;
        MonitorVisitor visitor = (monitor, dc, rectangle, data) =>
        {
            var info = new MonitorInfo { Size = Marshal.SizeOf<MonitorInfo>(), Device = string.Empty };
            if (GetMonitorInfoW(monitor, ref info) && info.Device.Equals(device, StringComparison.OrdinalIgnoreCase))
                found = (info.Work.Overlay, info.Monitor.Overlay, info.Device);
            return found is null;
        };
        EnumDisplayMonitors(0, 0, visitor, 0);
        GC.KeepAlive(visitor);
        return found;
    }
}
