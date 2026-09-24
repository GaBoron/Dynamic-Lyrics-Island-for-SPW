// SPDX-License-Identifier: GPL-3.0-only
using Microsoft.Graphics.Canvas;
using Microsoft.Graphics.Canvas.Geometry;
using System.Numerics;
using System.Text.Json;
using Windows.UI;

namespace IslandHost;

/** Draws the island silhouette and track progress independently of lyric shaping. */
internal sealed class NativeIslandChrome(CanvasDevice device) : IDisposable
{
    private CanvasGeometry? _silhouette;
    private (int Width, int Height, int Roundness, bool Notch, OverlayAnchor Anchor)? _shapeKey;

    public void Draw(CanvasDrawingSession ds, HostView view, int width, int height,
        float scale, bool expanded, OverlayAnchor anchor)
    {
        var settings = view.Settings;
        var roundness = Math.Clamp(Number(settings, "cornerRoundness", 95), 0, 100);
        var notch = Flag(settings, "notch");
        var palette = NativeIslandPalette.From(view);
        var key = (width, height, roundness, notch, anchor);
        if (_shapeKey != key)
        {
            _silhouette?.Dispose();
            _silhouette = NativeIslandShape.Create(device, width, height,
                roundness, notch, anchor);
            _shapeKey = key;
        }
        var silhouette = _silhouette!;
        ds.FillGeometry(silhouette, palette.Background);

        if (!Flag(settings, "lowPerformance") && !expanded &&
            view.Track is not null && view.Status != "IDLE")
            DrawProgress(ds, silhouette, view, width, height, scale, palette, anchor);

        ds.DrawGeometry(silhouette, Color.FromArgb(19, 255, 255, 255), 1f);
    }

    private static void DrawProgress(CanvasDrawingSession ds, CanvasGeometry silhouette,
        HostView view, int width, int height, float scale, NativeIslandPalette palette,
        OverlayAnchor anchor)
    {
        var mode = String(view.Settings, "backgroundProgress", "OFF");
        if (mode == "OFF") return;
        var duration = Number(view.Metadata, "durationMs", 0L);
        if (duration <= 0) return;
        var progress = Math.Clamp(view.PositionMs / (double)duration, 0, 1);
        if (progress <= 0) return;
        if (mode == "FILL")
        {
            var baseColor = palette.Background;
            var fill = Color.FromArgb(40,
                (byte)Math.Min(255, baseColor.R + 24),
                (byte)Math.Min(255, baseColor.G + 24),
                (byte)Math.Min(255, baseColor.B + 26));
            using (ds.CreateLayer(1f, silhouette))
                ds.FillRectangle(0, 0, (float)(width * progress), height, fill);
        }
        else if (mode == "TOP_LINE")
        {
            var lineColor = palette.Lyric;
            var inset = Math.Max(NativeIslandShape.TopEdgeInset(height,
                Number(view.Settings, "cornerRoundness", 95),
                Flag(view.Settings, "notch"), anchor), 8 * scale);
            var end = width - inset;
            if (end <= inset) return;
            ds.DrawLine(new Vector2(inset, 1.5f * scale),
                new Vector2(end, 1.5f * scale),
                Color.FromArgb(42, lineColor.R, lineColor.G, lineColor.B), 1.5f * scale);
            ds.DrawLine(new Vector2(inset, 1.5f * scale),
                new Vector2(inset + (float)((end - inset) * progress), 1.5f * scale),
                lineColor, 1.5f * scale);
        }
    }

    private static bool Flag(JsonElement? source, string key) =>
        source is { ValueKind: JsonValueKind.Object } value &&
        value.TryGetProperty(key, out var item) && item.ValueKind == JsonValueKind.True;

    private static string String(JsonElement? source, string key, string fallback) =>
        source is { ValueKind: JsonValueKind.Object } value &&
        value.TryGetProperty(key, out var item) && item.ValueKind == JsonValueKind.String
            ? item.GetString() ?? fallback : fallback;

    private static int Number(JsonElement? source, string key, int fallback) =>
        source is { ValueKind: JsonValueKind.Object } value &&
        value.TryGetProperty(key, out var item) && item.ValueKind == JsonValueKind.Number &&
        item.TryGetInt32(out var number) ? number : fallback;

    private static long Number(JsonElement? source, string key, long fallback) =>
        source is { ValueKind: JsonValueKind.Object } value &&
        value.TryGetProperty(key, out var item) && item.ValueKind == JsonValueKind.Number &&
        item.TryGetInt64(out var number) ? number : fallback;

    public void Dispose() => _silhouette?.Dispose();
}
