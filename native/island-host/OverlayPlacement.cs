// SPDX-License-Identifier: GPL-3.0-only
namespace IslandHost;

internal enum OverlayAnchor { TopLeft, TopCenter, TopRight, CenterLeft, Center, CenterRight, BottomLeft, BottomCenter, BottomRight }

internal readonly record struct OverlayRect(int X, int Y, int Width, int Height)
{
    public int Right => X + Width;
    public int Bottom => Y + Height;
    public int CenterX => X + Width / 2;
    public int CenterY => Y + Height / 2;
}

internal static class OverlayPlacement
{
    private const int SnapDistance = 24;

    public static (OverlayRect Bounds, OverlayAnchor Anchor) Snap(OverlayRect work, OverlayRect candidate)
    {
        var left = Nearest(candidate.X, work.X, work.X + (work.Width - candidate.Width) / 2,
            work.Right - candidate.Width);
        var top = Nearest(candidate.Y, work.Y, work.Y + (work.Height - candidate.Height) / 2,
            work.Bottom - candidate.Height);
        var bounds = candidate with { X = left.Value, Y = top.Value };
        var horizontal = left.Index >= 0 ? left.Index : Third(bounds.CenterX - work.X, work.Width);
        var vertical = top.Index >= 0 ? top.Index : Third(bounds.CenterY - work.Y, work.Height);
        return (bounds, (OverlayAnchor)(vertical * 3 + horizontal));
    }

    public static (int X, int Y) FixedPoint(OverlayRect bounds, OverlayAnchor anchor)
    {
        var index = (int)anchor;
        return (bounds.X + index % 3 * bounds.Width / 2,
            bounds.Y + index / 3 * bounds.Height / 2);
    }

    public static OverlayRect Place(OverlayRect work, int x, int y, int width, int height, OverlayAnchor anchor)
    {
        width = Math.Clamp(width, 1, Math.Max(1, work.Width));
        height = Math.Clamp(height, 1, Math.Max(1, work.Height));
        var index = (int)anchor;
        return new OverlayRect(
            Math.Clamp(x - index % 3 * width / 2, work.X, work.Right - width),
            Math.Clamp(y - index / 3 * height / 2, work.Y, work.Bottom - height),
            width, height);
    }

    private static (int Value, int Index) Nearest(int value, int first, int middle, int last)
    {
        var targets = new[] { first, middle, last };
        var nearest = Enumerable.Range(0, 3).MinBy(i => Math.Abs((long)value - targets[i]));
        return Math.Abs((long)value - targets[nearest]) <= SnapDistance
            ? (targets[nearest], nearest) : (value, -1);
    }

    private static int Third(int offset, int length) =>
        (int)Math.Min(2, Math.Clamp((long)offset * 3 / Math.Max(1, length), 0, 2));
}
