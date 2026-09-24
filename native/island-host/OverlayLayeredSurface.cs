// SPDX-License-Identifier: GPL-3.0-only
using System.Runtime.InteropServices;

namespace IslandHost;

/** Transfers premultiplied Win2D BGRA frames to the transparent Win32 overlay. */
internal sealed class OverlayLayeredSurface : IDisposable
{
    [StructLayout(LayoutKind.Sequential)]
    private struct BitmapHeader
    {
        public uint Size;
        public int Width, Height;
        public ushort Planes, BitCount;
        public uint Compression, ImageSize;
        public int XPelsPerMeter, YPelsPerMeter;
        public uint ClrUsed, ClrImportant;
    }

    [StructLayout(LayoutKind.Sequential)]
    private struct BitmapInfo
    {
        public BitmapHeader Header;
        public uint Color;
    }

    [StructLayout(LayoutKind.Sequential)]
    private struct BlendFunction
    {
        public byte Operation, Flags, ConstantAlpha, AlphaFormat;
    }

    [StructLayout(LayoutKind.Sequential)]
    private struct Size { public int Width, Height; }

    [DllImport("gdi32.dll")] private static extern nint CreateCompatibleDC(nint dc);
    [DllImport("gdi32.dll")] private static extern bool DeleteDC(nint dc);
    [DllImport("gdi32.dll")] private static extern nint CreateDIBSection(nint dc, ref BitmapInfo info,
        uint usage, out nint bits, nint section, uint offset);
    [DllImport("gdi32.dll")] private static extern nint SelectObject(nint dc, nint obj);
    [DllImport("gdi32.dll")] private static extern bool DeleteObject(nint obj);
    [DllImport("user32.dll", SetLastError = true)] private static extern bool UpdateLayeredWindow(nint hwnd,
        nint screenDc, ref OverlayWin32.Point destination, ref Size size, nint sourceDc,
        ref OverlayWin32.Point source, uint colorKey, ref BlendFunction blend, uint flags);

    private nint _dc, _bitmap, _previous, _bits;
    private int _width, _height;

    public void Present(nint hwnd, OverlayRect bounds, byte[] pixels, byte opacity)
    {
        if (bounds.Width != _width || bounds.Height != _height) Resize(bounds.Width, bounds.Height);
        if (pixels.Length != _width * _height * 4)
            throw new InvalidOperationException("词岛画面尺寸不匹配");
        Marshal.Copy(pixels, 0, _bits, pixels.Length);
        var destination = new OverlayWin32.Point { X = bounds.X, Y = bounds.Y };
        var source = new OverlayWin32.Point();
        var size = new Size { Width = _width, Height = _height };
        var blend = new BlendFunction { Operation = 0, ConstantAlpha = opacity, AlphaFormat = 1 };
        if (!UpdateLayeredWindow(hwnd, 0, ref destination, ref size, _dc, ref source,
                0, ref blend, 2))
            throw new InvalidOperationException($"词岛画面更新失败：{Marshal.GetLastWin32Error()}");
    }

    private void Resize(int width, int height)
    {
        Dispose();
        var info = new BitmapInfo
        {
            Header = new BitmapHeader
            {
                Size = (uint)Marshal.SizeOf<BitmapHeader>(), Width = width, Height = -height,
                Planes = 1, BitCount = 32
            }
        };
        _dc = CreateCompatibleDC(0);
        _bitmap = CreateDIBSection(_dc, ref info, 0, out _bits, 0, 0);
        if (_dc == 0 || _bitmap == 0 || _bits == 0)
            throw new InvalidOperationException("无法创建词岛透明画面");
        _previous = SelectObject(_dc, _bitmap);
        _width = width;
        _height = height;
    }

    public void Dispose()
    {
        if (_dc != 0 && _previous != 0) SelectObject(_dc, _previous);
        if (_bitmap != 0) DeleteObject(_bitmap);
        if (_dc != 0) DeleteDC(_dc);
        _dc = _bitmap = _previous = _bits = 0;
        _width = _height = 0;
    }
}
