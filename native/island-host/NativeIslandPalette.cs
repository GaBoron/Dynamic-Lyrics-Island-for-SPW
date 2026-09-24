// SPDX-License-Identifier: GPL-3.0-only
using System.Text.Json;
using Windows.UI;

namespace IslandHost;

/** Shared cover-derived colors for the native frame. */
internal readonly record struct NativeIslandPalette(Color Lyric, Color Background, Color Spectrum)
{
    public static NativeIslandPalette From(HostView view)
    {
        var cover = CoverColor(view.Metadata);
        var opacity = Math.Clamp(Number(view.Settings, "opacity", 96), 35, 100) / 100f;
        var white = Color.FromArgb(255, 255, 255, 255);
        return new NativeIslandPalette(
            Flag(view.Settings, "lyricCoverColor") && cover is { } lyric
                ? Bright(lyric) : white,
            Flag(view.Settings, "backgroundCoverColor") && cover is { } background
                ? Dark(background, opacity)
                : Color.FromArgb((byte)Math.Round(255 * opacity), 7, 8, 12),
            Flag(view.Settings, "spectrumCoverColor") && cover is { } spectrum
                ? Bright(spectrum) : white);
    }

    private static Color? CoverColor(JsonElement? metadata)
    {
        if (metadata is not { ValueKind: JsonValueKind.Object } value ||
            !value.TryGetProperty("coverRgb", out var rgb) ||
            rgb.ValueKind != JsonValueKind.Number || !rgb.TryGetInt32(out var raw)) return null;
        return Color.FromArgb(255, (byte)(raw >> 16), (byte)(raw >> 8), (byte)raw);
    }

    private static Color Dark(Color cover, float opacity)
    {
        var hsv = ToHsv(cover);
        return FromHsv(hsv.Hue, Math.Min(hsv.Saturation, .65f), .16f,
            (byte)Math.Round(255 * opacity));
    }

    private static Color Bright(Color cover)
    {
        var hsv = ToHsv(cover);
        return FromHsv(hsv.Hue, Math.Min(hsv.Saturation, .55f), 1f, 255);
    }

    private static (float Hue, float Saturation) ToHsv(Color color)
    {
        var r = color.R / 255f;
        var g = color.G / 255f;
        var b = color.B / 255f;
        var max = Math.Max(r, Math.Max(g, b));
        var min = Math.Min(r, Math.Min(g, b));
        var delta = max - min;
        var hue = delta == 0 ? 0 : max == r ? ((g - b) / delta) % 6 :
            max == g ? (b - r) / delta + 2 : (r - g) / delta + 4;
        return ((float)((hue / 6 + 1) % 1), max == 0 ? 0 : delta / max);
    }

    private static Color FromHsv(float hue, float saturation, float value, byte alpha)
    {
        var angle = hue * 6;
        var chroma = value * saturation;
        var x = chroma * (1 - Math.Abs(angle % 2 - 1));
        var offset = value - chroma;
        var (r, g, b) = angle switch
        {
            < 1 => (chroma, x, 0f), < 2 => (x, chroma, 0f),
            < 3 => (0f, chroma, x), < 4 => (0f, x, chroma),
            < 5 => (x, 0f, chroma), _ => (chroma, 0f, x)
        };
        return Color.FromArgb(alpha, (byte)Math.Round((r + offset) * 255),
            (byte)Math.Round((g + offset) * 255), (byte)Math.Round((b + offset) * 255));
    }

    private static bool Flag(JsonElement? source, string key) =>
        source is { ValueKind: JsonValueKind.Object } value &&
        value.TryGetProperty(key, out var item) && item.ValueKind == JsonValueKind.True;

    private static int Number(JsonElement? source, string key, int fallback) =>
        source is { ValueKind: JsonValueKind.Object } value &&
        value.TryGetProperty(key, out var item) && item.ValueKind == JsonValueKind.Number &&
        item.TryGetInt32(out var number) ? number : fallback;
}
