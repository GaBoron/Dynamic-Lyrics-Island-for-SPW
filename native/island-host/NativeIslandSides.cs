// SPDX-License-Identifier: GPL-3.0-only
using Microsoft.Graphics.Canvas;
using Microsoft.Graphics.Canvas.Geometry;
using System.Text.Json;
using Windows.Foundation;
using Windows.Graphics.DirectX;
using Windows.UI;

namespace IslandHost;

/** Draws the selected leading visual and trailing status outside the lyric area. */
internal sealed class NativeIslandSides(CanvasDevice device) : IDisposable
{
    private CanvasBitmap? _cover;
    private long _coverRevision = -1;

    public static bool Visible(JsonElement? settings) =>
        Mode(settings) != "NONE";

    public static float TextInset(JsonElement? settings, float scale) =>
        Visible(settings) ? 54 * scale : 16 * scale;

    public void Draw(CanvasDrawingSession ds, HostView view, int width, int height,
        float scale, bool expanded, NativeIslandPalette palette)
    {
        var mode = Mode(view.Settings);
        if (mode == "NONE") return;
        var lyricHeight = height - (expanded ? 42 * scale : 0);
        var size = Math.Min(32 * scale, Math.Max(12 * scale, lyricHeight - 20 * scale));
        var y = (lyricHeight - size) / 2;
        var leadingX = 16 * scale;
        var trailingX = width - leadingX - size;
        if (mode is "COVER_SPECTRUM" or "COVER")
            DrawCover(ds, view, leadingX, y, size, palette.Spectrum);
        else if (mode == "SPECTRUM")
            DrawSpectrum(ds, view, leadingX, y, size, palette.Spectrum);
        if (mode == "COVER_SPECTRUM")
            DrawSpectrum(ds, view, trailingX, y, size, palette.Spectrum);
        else
            DrawStatus(ds, view, trailingX, y, size);
    }

    private void DrawCover(CanvasDrawingSession ds, HostView view, float x, float y,
        float size, Color accent)
    {
        if (_coverRevision != view.MetadataRevision)
        {
            _coverRevision = view.MetadataRevision;
            _cover?.Dispose();
            _cover = null;
            if (view.Metadata is { ValueKind: JsonValueKind.Object } metadata &&
                metadata.TryGetProperty("coverPixels", out var value) &&
                value.ValueKind == JsonValueKind.String)
            {
                var encoded = value.GetString();
                if (encoded is { Length: <= 22000 and > 0 })
                {
                    var bytes = new byte[64 * 64 * 4];
                    if (Convert.TryFromBase64String(encoded, bytes, out var written) &&
                        written == bytes.Length)
                        _cover = CanvasBitmap.CreateFromBytes(device, bytes, 64, 64,
                            DirectXPixelFormat.B8G8R8A8UIntNormalized);
                }
            }
        }
        using var clip = CanvasGeometry.CreateRoundedRectangle(device, x, y, size, size,
            size * .18f, size * .18f);
        if (_cover is { } bitmap)
        {
            using (ds.CreateLayer(1f, clip))
                ds.DrawImage(bitmap, new Rect(x, y, size, size));
            ds.DrawGeometry(clip, Color.FromArgb(35, 255, 255, 255), 1f);
            return;
        }
        ds.FillGeometry(clip, Color.FromArgb(55, accent.R, accent.G, accent.B));
        ds.DrawEllipse(x + size * .5f, y + size * .5f,
            size * .22f, size * .22f, Color.FromArgb(180, accent.R, accent.G, accent.B), 1f);
        ds.FillEllipse(x + size * .5f, y + size * .5f,
            size * .06f, size * .06f, accent);
    }

    private static void DrawSpectrum(CanvasDrawingSession ds, HostView view,
        float x, float y, float size, Color accent)
    {
        var lowPerformance = Flag(view.Settings, "lowPerformance");
        var live = !lowPerformance && view.Spectrum.Any(level => level > .015f);
        for (var i = 0; i < 4; i++)
        {
            var level = !view.Playing ? .06f : live ? view.Spectrum[i] :
                .18f + .33f * (float)(1 + Math.Sin(view.PositionMs / 300.0 + i * 1.3)) / 2;
            var barHeight = size * (.1f + .9f * Math.Clamp(level, 0, 1));
            var barWidth = size * .095f;
            var barX = x + size * (.16f + i * .22f);
            ds.FillRoundedRectangle(barX, y + (size - barHeight) / 2,
                barWidth, barHeight, barWidth / 2, barWidth / 2, accent);
        }
    }

    private static void DrawStatus(CanvasDrawingSession ds, HostView view,
        float x, float y, float size)
    {
        var color = Color.FromArgb(255, 115, 131, 149);
        if (view.Playing)
            ds.FillEllipse(x + size * .5f, y + size * .5f,
                size * .09f, size * .09f, color);
        else
        {
            ds.FillRoundedRectangle(x + size * .31f, y + size * .34f,
                size * .11f, size * .32f, size * .04f, size * .04f, color);
            ds.FillRoundedRectangle(x + size * .56f, y + size * .34f,
                size * .11f, size * .32f, size * .04f, size * .04f, color);
        }
    }

    private static string Mode(JsonElement? settings) =>
        settings is { ValueKind: JsonValueKind.Object } value &&
        value.TryGetProperty("sideContent", out var item) && item.ValueKind == JsonValueKind.String
            ? item.GetString() ?? "COVER_SPECTRUM" : "COVER_SPECTRUM";

    private static bool Flag(JsonElement? settings, string key) =>
        settings is { ValueKind: JsonValueKind.Object } value &&
        value.TryGetProperty(key, out var item) && item.ValueKind == JsonValueKind.True;

    public void Dispose() => _cover?.Dispose();
}
