// SPDX-License-Identifier: GPL-3.0-only
using Microsoft.Graphics.Canvas;
using Microsoft.Graphics.Canvas.Geometry;
using System.Numerics;

namespace IslandHost;

/** Builds the same pill and edge-notch family for every native frame size. */
internal static class NativeIslandShape
{
    public static CanvasGeometry Create(CanvasDevice device, int width, int height,
        int roundness, bool notch, OverlayAnchor anchor)
    {
        var w = Math.Max(1, width - 1f);
        var h = Math.Max(1, height - 1f);
        var factor = Math.Clamp(roundness, 0, 100) / 100f;
        if (!notch || anchor is OverlayAnchor.CenterLeft or OverlayAnchor.Center or OverlayAnchor.CenterRight)
        {
            var radius = Math.Min(w, h) * .5f * factor;
            if (radius <= 0) return CanvasGeometry.CreateRectangle(device, .5f, .5f, w, h);
            if (roundness >= 100) return CanvasGeometry.CreateRoundedRectangle(device,
                .5f, .5f, w, h, radius, radius);
            return Continuous(device, w, h, radius, Exponent(factor));
        }
        return Notch(device, w, h, factor, anchor is OverlayAnchor.BottomLeft or
            OverlayAnchor.BottomCenter or OverlayAnchor.BottomRight);
    }

    public static float TopEdgeInset(int height, int roundness, bool notch, OverlayAnchor anchor) =>
        notch && anchor is not (OverlayAnchor.CenterLeft or OverlayAnchor.Center or
            OverlayAnchor.CenterRight) ? 10.5f :
        height * .5f * Math.Clamp(roundness, 0, 100) / 100f;

    private static CanvasGeometry Continuous(CanvasDevice device, float w, float h,
        float radius, float exponent)
    {
        using var path = new CanvasPathBuilder(device);
        path.BeginFigure(.5f + radius, .5f);
        path.AddLine(.5f + w - radius, .5f);
        Corner(path, radius, exponent, angle =>
            (.5f + w - radius + radius * Power(MathF.Sin(angle), exponent),
                .5f + radius - radius * Power(MathF.Cos(angle), exponent)));
        path.AddLine(.5f + w, .5f + h - radius);
        Corner(path, radius, exponent, angle =>
            (.5f + w - radius + radius * Power(MathF.Cos(angle), exponent),
                .5f + h - radius + radius * Power(MathF.Sin(angle), exponent)));
        path.AddLine(.5f + radius, .5f + h);
        Corner(path, radius, exponent, angle =>
            (.5f + radius - radius * Power(MathF.Sin(angle), exponent),
                .5f + h - radius + radius * Power(MathF.Cos(angle), exponent)));
        path.AddLine(.5f, .5f + radius);
        Corner(path, radius, exponent, angle =>
            (.5f + radius - radius * Power(MathF.Cos(angle), exponent),
                .5f + radius - radius * Power(MathF.Sin(angle), exponent)));
        path.EndFigure(CanvasFigureLoop.Closed);
        return CanvasGeometry.CreatePath(path);
    }

    private static CanvasGeometry Notch(CanvasDevice device, float w, float h,
        float factor, bool bottom)
    {
        var radius = Math.Min(Math.Min(h * .45f, 32), Math.Min((w - 20) / 2, h - 14));
        radius = Math.Max(0, radius) * factor;
        using var path = new CanvasPathBuilder(device);
        Vector2 Point(float x, float y) => new(.5f + x, .5f + (bottom ? h - y : y));
        path.BeginFigure(Point(0, 0));
        path.AddLine(Point(w, 0));
        path.AddCubicBezier(Point(w - 10, 0), Point(w - 10, 8), Point(w - 10, 14));
        path.AddLine(Point(w - 10, h - radius));
        Corner(path, radius, Exponent(factor), angle =>
        {
            var x = w - 10 - radius + radius * Power(MathF.Cos(angle), Exponent(factor));
            var y = h - radius + radius * Power(MathF.Sin(angle), Exponent(factor));
            var mapped = Point(x, y);
            return (mapped.X, mapped.Y);
        });
        path.AddLine(Point(10 + radius, h));
        Corner(path, radius, Exponent(factor), angle =>
        {
            var x = 10 + radius - radius * Power(MathF.Sin(angle), Exponent(factor));
            var y = h - radius + radius * Power(MathF.Cos(angle), Exponent(factor));
            var mapped = Point(x, y);
            return (mapped.X, mapped.Y);
        });
        path.AddLine(Point(10, 14));
        path.AddCubicBezier(Point(10, 8), Point(10, 0), Point(0, 0));
        path.EndFigure(CanvasFigureLoop.Closed);
        return CanvasGeometry.CreatePath(path);
    }

    private static void Corner(CanvasPathBuilder path, float radius, float exponent,
        Func<float, (float X, float Y)> point)
    {
        if (radius <= 0) return;
        for (var step = 1; step <= 16; step++)
        {
            var (x, y) = point(MathF.PI / 2 * step / 16);
            path.AddLine(x, y);
        }
    }

    private static float Exponent(float factor) =>
        factor <= .6f ? 4 : 4 - 2 * (factor - .6f) / .4f;

    private static float Power(float value, float exponent) =>
        MathF.Pow(value, 2 / exponent);
}
