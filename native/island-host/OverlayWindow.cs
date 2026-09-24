// SPDX-License-Identifier: GPL-3.0-only
using System.Runtime.InteropServices;
using System.Text.Json;
using System.Text.RegularExpressions;
using static IslandHost.OverlayWin32;

namespace IslandHost;

// The native window is opt-in until the native lyric renderer can replace AWT.
// Its window policy is independent of the future text and artwork renderer.
internal sealed class OverlayWindow(IslandHostState state, HostCommandWriter commands)
{
    private const uint WmPaint = 0x000F, WmDestroy = 0x0002, WmClose = 0x0010;
    private const uint WmTimer = 0x0113, WmLButtonDown = 0x0201, WmLButtonUp = 0x0202;
    private const uint WmMouseMove = 0x0200, WmLButtonDblClk = 0x0203, WmRButtonUp = 0x0205;
    private const uint WmNcActivate = 0x0086;
    private const uint WmDisplayChange = 0x007E;
    private const uint CsDblClks = 0x0008;
    private const uint MfString = 0, MfChecked = 8, MfSeparator = 0x800;
    private const uint TpmReturnCmd = 0x100, TpmRightButton = 2;
    private const uint SwpNoMove = 2, SwpNoSize = 1;
    private const int BaseWidth = 300, BaseHeight = 58;
    private const int BaseExpandedHeight = 100, BaseHoverMargin = 18;
    private static readonly string ClassName = "SPW Island native overlay";
    private nint _hwnd;
    private OverlayRect _bounds;
    private OverlayAnchor _anchor = OverlayAnchor.TopCenter;
    private bool _dragging;
    private Point _press;
    private OverlayRect _dragStart;
    private bool _clickThrough;
    private bool _shown;
    private bool _hovered;
    private byte _alpha = 255;
    private uint _timerDelay = 50;
    private long _nextTopmost;
    private string? _lastPosition;
    private string? _lastTitle;
    private uint _dpi = 96;
    private int Width => Scale(BaseWidth);
    private int Height => Scale(BaseHeight);
    private int ExpandedHeight => Scale(BaseExpandedHeight);
    private int HoverMargin => Scale(BaseHoverMargin);
    private int Scale(int value) => (int)Math.Round(value * _dpi / 96.0);

    public void Run()
    {
        var procedure = new WindowProcedure(HandleMessage);
        var instance = GetModuleHandleW(null);
        var windowClass = new WindowClass
        {
            Size = (uint)Marshal.SizeOf<WindowClass>(), Style = CsDblClks,
            Procedure = procedure, Name = ClassName, MenuName = string.Empty,
            Instance = instance, Cursor = LoadCursorW(0, new nint(32512))
        };
        if (RegisterClassExW(ref windowClass) == 0) throw new InvalidOperationException("无法注册词岛窗口");
        var cursor = new Point();
        GetCursorPos(out cursor);
        var work = Screen(cursor).Work;
        _bounds = OverlayPlacement.Place(work, work.CenterX, work.Y, Width, Height, _anchor);
        _hwnd = CreateWindowExW(ExLayered | ExNoActivate | ExToolWindow | ExTopmost,
            ClassName, "动态歌词岛", WsPopup, _bounds.X, _bounds.Y, Width, Height,
            0, 0, instance, 0);
        if (_hwnd == 0) throw new InvalidOperationException("无法创建词岛窗口");
        UpdateDpi(_hwnd);
        SetLayeredWindowAttributes(_hwnd, 0, 255, LwaColorKey | LwaAlpha);
        SetTimer(_hwnd, 1, 50, 0);
        try
        {
            while (GetMessageW(out var message, 0, 0, 0) > 0)
            {
                TranslateMessage(ref message);
                DispatchMessageW(ref message);
            }
        }
        finally
        {
            KillTimer(_hwnd, 1);
            if (_hwnd != 0) DestroyWindow(_hwnd);
            GC.KeepAlive(procedure);
        }
    }

    public void Stop()
    {
        var handle = Interlocked.Exchange(ref _hwnd, 0);
        if (handle != 0) PostMessageW(handle, WmClose, 0, 0);
    }

    private nint HandleMessage(nint hwnd, uint message, nuint wParam, nint lParam)
    {
        switch (message)
        {
            case WmNcActivate: return 1;
            case WmDisplayChange:
                _lastPosition = null;
                var displayPoint = OverlayPlacement.FixedPoint(_bounds, _anchor);
                var relocated = OverlayPlacement.Place(
                    Screen(new Point { X = displayPoint.X, Y = displayPoint.Y }).Work,
                    displayPoint.X, displayPoint.Y, _bounds.Width, _bounds.Height, _anchor);
                _bounds = relocated;
                SetWindowPos(hwnd, HwndTopmost, relocated.X, relocated.Y,
                    relocated.Width, relocated.Height, SwpNoActivate);
                return 0;
            case WmPaint: Paint(hwnd); return 0;
            case WmTimer: Tick(hwnd); return 0;
            case WmLButtonDown:
                if (!_clickThrough && GetCursorPos(out _press))
                {
                    _dragStart = _bounds;
                    _dragging = false;
                    SetCapture(hwnd);
                }
                return 0;
            case WmMouseMove:
                if (GetCursorPos(out var pointer) && _dragStart.Width > 0)
                {
                    if (!_dragging && Math.Abs(pointer.X - _press.X) + Math.Abs(pointer.Y - _press.Y) < 4)
                        return 0;
                    _dragging = true;
                    var candidate = _dragStart with
                    {
                        X = _dragStart.X + pointer.X - _press.X,
                        Y = _dragStart.Y + pointer.Y - _press.Y
                    };
                    var (snapped, anchor) = OverlayPlacement.Snap(Screen(pointer).Work, candidate);
                    _bounds = snapped;
                    _anchor = anchor;
                    SetWindowPos(hwnd, HwndTopmost, snapped.X, snapped.Y, snapped.Width, snapped.Height, SwpNoActivate);
                }
                return 0;
            case WmLButtonUp:
                if (_dragStart.Width > 0)
                {
                    ReleaseCapture();
                    _dragStart = default;
                    if (_dragging)
                    {
                        var point = new Point { X = _bounds.CenterX, Y = _bounds.CenterY };
                        var screen = Screen(point);
                        var fixedPoint = OverlayPlacement.FixedPoint(_bounds, _anchor);
                        commands.SavePosition(screen.Device, fixedPoint.X, fixedPoint.Y,
                            _anchor.ToString(), screen.Monitor.X, screen.Monitor.Y);
                    }
                    else if (_hovered && GetCursorPos(out var click) && click.Y - _bounds.Y >= Height)
                    {
                        var column = Math.Clamp((click.X - _bounds.X) * 3 / Width, 0, 2);
                        if (column == 0) commands.Previous();
                        else if (column == 1) commands.Toggle();
                        else commands.Next();
                    }
                    _dragging = false;
                }
                return 0;
            case WmLButtonDblClk: commands.Toggle(); return 0;
            case WmRButtonUp:
                if (GetCursorPos(out var at)) ShowMenu(hwnd, at);
                return 0;
            case WmClose: DestroyWindow(hwnd); return 0;
            case WmDestroy: _hwnd = 0; PostQuitMessage(0); return 0;
            default: return DefWindowProcW(hwnd, message, wParam, lParam);
        }
    }

    private void Tick(nint hwnd)
    {
        UpdateDpi(hwnd);
        var view = state.Snapshot();
        var settings = view.Settings;
        var title = view.Track is { } track && track.ValueKind == JsonValueKind.Object &&
            track.TryGetProperty("title", out var name) ? name.GetString() : null;
        if (title != _lastTitle)
        {
            _lastTitle = title;
            InvalidateRect(hwnd, 0, false);
        }
        bool Flag(string name, bool fallback = false) =>
            settings is { } json && json.TryGetProperty(name, out var value) ? value.GetBoolean() : fallback;
        var delay = Flag("lowPerformance") ? 250u : 50u;
        if (delay != _timerDelay)
        {
            KillTimer(hwnd, 1);
            SetTimer(hwnd, 1, delay, 0);
            _timerDelay = delay;
        }
        var visible = Flag("enabled", true) && (!Flag("hidePaused") || view.Playing) &&
            (!Flag("hideFullscreen", true) || !ForegroundFullscreen(hwnd));
        var clickThrough = Flag("clickThrough");
        if (clickThrough != _clickThrough)
        {
            _clickThrough = clickThrough;
            var style = GetWindowLongPtr(hwnd, GwlExStyle).ToInt64();
            style = clickThrough ? style | ExTransparent : style & ~ExTransparent;
            SetWindowLongPtr(hwnd, GwlExStyle, new nint(style));
        }
        if (visible != _shown)
        {
            ShowWindow(hwnd, visible ? 8 : 0); // SW_SHOWNA / SW_HIDE
            _shown = visible;
        }
        if (!visible) return;
        if (Environment.TickCount64 >= _nextTopmost)
        {
            SetWindowPos(hwnd, HwndTopmost, 0, 0, 0, 0,
                SwpNoActivate | SwpNoMove | SwpNoSize);
            _nextTopmost = Environment.TickCount64 + (Flag("lowPerformance") ? 5000 : 2000);
        }
        if (GetCursorPos(out var cursor))
        {
            var inside = cursor.X >= _bounds.X - (_hovered ? HoverMargin : 0) &&
                cursor.X < _bounds.Right + (_hovered ? HoverMargin : 0) &&
                cursor.Y >= _bounds.Y - (_hovered ? HoverMargin : 0) &&
                cursor.Y < _bounds.Bottom + (_hovered ? HoverMargin : 0);
            _hovered = !_clickThrough && inside;
            if (!_dragging)
            {
                var desired = _hovered ? ExpandedHeight : Height;
                var nextHeight = Flag("lowPerformance") ? desired :
                    _bounds.Height + Math.Clamp(desired - _bounds.Height, -16, 16);
                if (nextHeight != _bounds.Height)
                {
                    var fixedPoint = OverlayPlacement.FixedPoint(_bounds, _anchor);
                    var next = OverlayPlacement.Place(Screen(cursor).Work, fixedPoint.X, fixedPoint.Y,
                        Width, nextHeight, _anchor);
                    _bounds = next;
                    SetWindowPos(hwnd, HwndTopmost, next.X, next.Y, next.Width, next.Height, SwpNoActivate);
                }
            }
            var hidden = clickThrough && Flag("autoHideOnHover") &&
                cursor.X >= _bounds.X && cursor.X < _bounds.Right &&
                cursor.Y >= _bounds.Y && cursor.Y < _bounds.Bottom;
            var target = (byte)(hidden ? 0 : 255);
            if (target != _alpha)
            {
                _alpha = target;
                SetLayeredWindowAttributes(hwnd, 0, _alpha, LwaColorKey | LwaAlpha);
            }
        }
        if (settings is { } placement && !_dragging)
        {
            var signature = string.Join(':',
                placement.TryGetProperty("screen", out var screen) ? screen.ToString() : "",
                placement.TryGetProperty("positionX", out var x) ? x.ToString() : "",
                placement.TryGetProperty("positionY", out var y) ? y.ToString() : "",
                placement.TryGetProperty("positionAnchor", out var anchor) ? anchor.ToString() : "",
                view.Displays?.GetRawText() ?? "");
            if (signature != _lastPosition)
            {
                _lastPosition = signature;
                ApplySavedPosition(hwnd, placement, view.Displays);
            }
        }
    }

    private void UpdateDpi(nint hwnd)
    {
        var dpi = GetDpiForWindow(hwnd);
        if (dpi == 0 || dpi == _dpi) return;
        var fixedPoint = OverlayPlacement.FixedPoint(_bounds, _anchor);
        _dpi = dpi;
        var next = OverlayPlacement.Place(Screen(new Point { X = fixedPoint.X, Y = fixedPoint.Y }).Work,
            fixedPoint.X, fixedPoint.Y, Width, _hovered ? ExpandedHeight : Height, _anchor);
        _bounds = next;
        SetWindowPos(hwnd, HwndTopmost, next.X, next.Y, next.Width, next.Height, SwpNoActivate);
    }

    private void ApplySavedPosition(nint hwnd, JsonElement settings, JsonElement? displays)
    {
        if (!settings.TryGetProperty("positionX", out var x) || x.ValueKind != JsonValueKind.Number ||
            !settings.TryGetProperty("positionY", out var y) || y.ValueKind != JsonValueKind.Number ||
            !settings.TryGetProperty("screen", out var screenId) ||
            displays is not { ValueKind: JsonValueKind.Array })
            return;
        var id = screenId.GetString();
        var match = Regex.Match(id ?? "", @"Display(\d+)$", RegexOptions.IgnoreCase);
        if (!match.Success || !int.TryParse(match.Groups[1].Value, out var index)) return;
        var nativeScreen = Screen(@"\\.\DISPLAY" + (index + 1));
        if (nativeScreen is null) return;
        JsonElement? javaScreen = null;
        foreach (var display in displays.Value.EnumerateArray())
        {
            if (display.GetProperty("id").GetString() == id) { javaScreen = display; break; }
        }
        if (javaScreen is null) return;
        var java = javaScreen.Value;
        var scaleX = java.GetProperty("scaleX").GetDouble();
        var scaleY = java.GetProperty("scaleY").GetDouble();
        if (!double.IsFinite(scaleX) || !double.IsFinite(scaleY) || scaleX <= 0 || scaleY <= 0) return;
        var anchor = _anchor;
        if (settings.TryGetProperty("positionAnchor", out var value) && value.ValueKind == JsonValueKind.Object &&
            value.TryGetProperty("horizontal", out var horizontal) &&
            value.TryGetProperty("vertical", out var vertical))
        {
            var h = horizontal.GetString() switch { "LEFT" => 0, "RIGHT" => 2, _ => 1 };
            var v = vertical.GetString() switch { "TOP" => 0, "BOTTOM" => 2, _ => 1 };
            anchor = (OverlayAnchor)(v * 3 + h);
        }
        var pointX = nativeScreen.Value.Monitor.X +
            (int)Math.Round((x.GetInt32() - java.GetProperty("x").GetInt32()) * scaleX);
        var pointY = nativeScreen.Value.Monitor.Y +
            (int)Math.Round((y.GetInt32() - java.GetProperty("y").GetInt32()) * scaleY);
        var target = OverlayPlacement.Place(nativeScreen.Value.Work, pointX, pointY,
            Width, _bounds.Height, anchor);
        if (target == _bounds) return;
        _anchor = anchor;
        _bounds = target;
        SetWindowPos(hwnd, HwndTopmost, target.X, target.Y, target.Width, target.Height, SwpNoActivate);
    }

    private static bool ForegroundFullscreen(nint hwnd)
    {
        var foreground = GetForegroundWindow();
        if (foreground == 0 || foreground == hwnd ||
            MonitorFromWindow(foreground, MonitorDefaultToNearest) != MonitorFromWindow(hwnd, MonitorDefaultToNearest))
            return false;
        var name = new char[128];
        GetClassNameW(foreground, name, name.Length);
        var kind = new string(name).TrimEnd('\0');
        if (kind is "Progman" or "WorkerW" or "Shell_TrayWnd") return false;
        if (!GetWindowRect(foreground, out var bounds)) return false;
        var screen = Screen(new Point { X = bounds.Left, Y = bounds.Top }).Monitor;
        return bounds.Left <= screen.X && bounds.Top <= screen.Y &&
            bounds.Right >= screen.Right && bounds.Bottom >= screen.Bottom;
    }

    private void ShowMenu(nint hwnd, Point point)
    {
        var settings = state.Snapshot().Settings;
        bool Checked(string key) => settings is { } json && json.TryGetProperty(key, out var value) && value.GetBoolean();
        var menu = CreatePopupMenu();
        try
        {
            AppendMenuW(menu, MfString | (Checked("lowPerformance") ? MfChecked : 0), 1, "低性能模式");
            AppendMenuW(menu, MfString | (Checked("translation") ? MfChecked : 0), 2, "显示翻译");
            AppendMenuW(menu, MfString | (Checked("karaoke") ? MfChecked : 0), 3, "逐字高亮");
            AppendMenuW(menu, MfString | (Checked("clickThrough") ? MfChecked : 0), 4, "鼠标穿透");
            AppendMenuW(menu, MfString | (Checked("autoHideOnHover") ? MfChecked : 0), 5, "悬停自动隐藏");
            AppendMenuW(menu, MfSeparator, 0, null);
            AppendMenuW(menu, MfString, 6, "重置位置");
            // TPM_RETURNCMD avoids relying on focus or WM_COMMAND for a no-activate overlay.
            var id = TrackPopupMenu(menu, TpmReturnCmd | TpmRightButton, point.X, point.Y, 0, hwnd, 0);
            switch (id)
            {
                case 1: commands.SetSetting("reduced_motion", !Checked("lowPerformance")); break;
                case 2: commands.SetSetting("translation", !Checked("translation")); break;
                case 3: commands.SetSetting("karaoke", !Checked("karaoke")); break;
                case 4: commands.SetSetting("click_through", !Checked("clickThrough")); break;
                case 5: commands.SetSetting("auto_hide_on_hover", !Checked("autoHideOnHover")); break;
                case 6: commands.ResetPosition(); break;
            }
        }
        finally { DestroyMenu(menu); }
    }

    private void Paint(nint hwnd)
    {
        var dc = BeginPaint(hwnd, out var paint);
        var height = _bounds.Height;
        try
        {
            var black = CreateSolidBrush(0);
            var old = SelectObject(dc, black);
            Rectangle(dc, 0, 0, Width, height);
            SelectObject(dc, old);
            DeleteObject(black);
            var fill = CreateSolidBrush(0x29201c); // dark neutral, COLORREF
            old = SelectObject(dc, fill);
            RoundRect(dc, 0, 0, Width, height, Height, Height);
            SelectObject(dc, old);
            DeleteObject(fill);
            SetBkMode(dc, 1);
            SetTextColor(dc, 0x00ffffff);
            var view = state.Snapshot();
            var title = view.Track is { } track && track.ValueKind == JsonValueKind.Object &&
                track.TryGetProperty("title", out var name) ? name.GetString() : null;
            var area = new Rect { Left = 12, Top = 0, Right = Width - 12, Bottom = Height };
            DrawTextW(dc, title ?? "动态歌词岛", -1, ref area, 0x25);
            if (height > Height)
            {
                area = new Rect { Left = 0, Top = Height, Right = Width, Bottom = height };
                DrawTextW(dc, "上一首             播放/暂停             下一首", -1, ref area, 0x25);
            }
        }
        finally { EndPaint(hwnd, ref paint); }
    }
}
