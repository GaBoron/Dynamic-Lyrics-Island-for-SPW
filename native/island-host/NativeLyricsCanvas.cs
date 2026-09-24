// SPDX-License-Identifier: GPL-3.0-only
// Links to the AGPL-3.0-only AMLL motion port; see NOTICE.
using Microsoft.Graphics.Canvas;
using Microsoft.Graphics.Canvas.Geometry;
using Microsoft.Graphics.Canvas.Text;
using System.Numerics;
using System.Text.Json;
using Windows.Foundation;
using Windows.UI;
using Microsoft.UI;

namespace IslandHost;

/** Renders whole-line DirectWrite layouts, then clips and transforms shaped grapheme regions. */
internal sealed class NativeLyricsCanvas : IDisposable
{
    private readonly NativeTextLayout _text = new();
    private readonly CanvasDevice _device = CanvasDevice.GetSharedDevice();
    private CanvasRenderTarget? _target;
    private int _width, _height;

    public int ContentHeight(NativeLyricsFrame frame, JsonElement? settings)
    {
        var size = FontSize(settings);
        var content = frame.Rows.Sum(row => size * 1.3f +
            (row.Translation is null ? 0 : size * .7f * 1.3f + 8)) +
            Math.Max(0, frame.Rows.Count - 1) * Math.Max(8, size * .36f);
        return Math.Max(58, (int)Math.Ceiling(content + 28));
    }

    public byte[] Render(NativeLyricsFrame frame, JsonElement? settings, int width, int height,
        float scale, bool expanded)
    {
        if (_target is null || _width != width || _height != height)
        {
            _target?.Dispose();
            _target = new CanvasRenderTarget(_device, width, height, 96);
            _width = width;
            _height = height;
        }
        using (var ds = _target.CreateDrawingSession())
        {
            ds.Clear(Colors.Transparent);
            ds.FillRoundedRectangle(0, 0, width, height, Math.Min(height, 58 * scale) / 2f,
                Math.Min(height, 58 * scale) / 2f, Color.FromArgb(246, 28, 32, 41));
            DrawRows(ds, frame, settings, width, height, scale, expanded);
        }
        return _target.GetPixelBytes();
    }

    private void DrawRows(CanvasDrawingSession ds, NativeLyricsFrame frame, JsonElement? settings,
        int width, int height, float scale, bool expanded)
    {
        var size = FontSize(settings) * scale;
        var family = String(settings, "fontFamily", "Segoe UI");
        var offset = Number(settings, "offsetMs", 0);
        var karaoke = Bool(settings, "karaoke", true);
        var rowGap = Math.Max(8, size * .36f);
        var total = frame.Rows.Sum(row => size * 1.3f +
            (row.Translation is null ? 0 : size * .7f * 1.3f + 8)) +
            Math.Max(0, frame.Rows.Count - 1) * rowGap;
        var lyricHeight = height - (expanded ? 42 * scale : 0);
        var y = (lyricHeight - total) / 2f;
        var outgoingY = y;
        foreach (var row in frame.Outgoing)
        {
            using (ds.CreateLayer((float)frame.OutgoingAlpha))
                DrawRow(ds, row, frame.PositionMs + offset, family, size,
                    width, height, outgoingY - (float)(size * (1 - frame.OffsetEm)), karaoke,
                    frame.DetailedKaraoke);
            outgoingY += size * 1.3f +
                (row.Translation is null ? 0 : size * .7f * 1.3f + 8) + rowGap;
        }
        foreach (var row in frame.Rows)
        {
            using (ds.CreateLayer((float)frame.IncomingAlpha))
                DrawRow(ds, row, frame.PositionMs + offset, family, size,
                    width, height, y + (float)(size * frame.OffsetEm), karaoke,
                    frame.DetailedKaraoke);
            y += size * 1.3f + (row.Translation is null ? 0 : size * .7f * 1.3f + 8) + rowGap;
        }
        if (expanded)
        {
            var controls = _text.Get("上一首          播放/暂停          下一首", "Segoe UI", 13 * scale);
            ds.DrawTextLayout(controls, (width - (float)controls.LayoutBounds.Width) / 2,
                height - 35 * scale, Color.FromArgb(255, 177, 182, 195));
        }
    }

    private void DrawRow(CanvasDrawingSession ds, NativeLyricRow row, long position,
        string family, float size, int width, int height, float y, bool karaoke, bool detailed)
    {
        var layout = _text.Get(row.Main, family, size);
        var textWidth = (float)layout.LayoutBounds.Width;
        var available = width - 32f;
        var overflow = Math.Max(0, textWidth - available);
        var activeX = ActiveX(layout, row, position);
        var scroll = overflow > 0 ? row.TimedWords.Count > 0
            ? Math.Clamp(activeX - available * .65f, 0, overflow)
            : Math.Clamp((position - (row.Line?.StartMs ?? 0)) / 45f, 0, overflow) : 0;
        var x = (overflow == 0 ? (width - textWidth) / 2 : 16 - scroll) -
            (float)layout.LayoutBounds.X;
        using (ds.CreateLayer(1f, new Rect(8, 0, width - 16, height)))
        {
            if (karaoke && row.TimedWords.Count > 0)
            {
                if (!detailed) ds.DrawTextLayout(layout, x, y, Color.FromArgb(255, 126, 129, 138));
                var start = 0;
                for (var wordIndex = 0; wordIndex < row.TimedWords.Count; wordIndex++)
                {
                    var word = row.TimedWords[wordIndex];
                    var length = Math.Min(word.Text.Length, row.Main.Length - start);
                    if (length <= 0) break;
                    var regions = layout.GetCharacterRegions(start, length);
                    var progress = word.Progress(position);
                    if (progress > 0 && !detailed)
                    {
                        foreach (var region in regions)
                        {
                            var bounds = region.LayoutBounds;
                            var leftToRight = IsLeftToRight(layout);
                            var clipX = x + bounds.X + (leftToRight ? 0 : bounds.Width * (1 - progress));
                            using (ds.CreateLayer(1f, new Rect(clipX, y + bounds.Y,
                                Math.Max(.5, bounds.Width * progress), bounds.Height)))
                                ds.DrawTextLayout(layout, x, y, Colors.White);
                        }
                    }
                    // The detailed motion path keeps the whole-line shaping and clips each grapheme.
                    if (detailed) DrawEmphasis(ds, layout, word, wordIndex == row.TimedWords.Count - 1,
                        start, x, y, size, position);
                    start += length;
                }
            }
            else ds.DrawTextLayout(layout, x, y, Colors.White);
        }
        if (row.Translation is { } translation)
        {
            var sub = _text.Get(translation, family, size * .7f);
            var subX = (width - (float)sub.LayoutBounds.Width) / 2;
            using (ds.CreateLayer(1f, new Rect(8, 0, width - 16, height)))
                ds.DrawTextLayout(sub, subX, y + size * 1.3f + 8,
                    Color.FromArgb(255, 177, 182, 195));
        }
    }

    private void DrawEmphasis(CanvasDrawingSession ds, CanvasTextLayout layout,
        NativeLyricWord word, bool last, int wordStart, float x, float y, float size, long position)
    {
        var graphemes = NativeAmllMotion.Graphemes(word.Text);
        var wordRegions = layout.GetCharacterRegions(wordStart, word.Text.Length);
        var leftToRight = IsLeftToRight(layout);
        var wordLeft = wordRegions.Length == 0 ? 0 : wordRegions.Min(region => region.LayoutBounds.X);
        var wordRight = wordRegions.Length == 0 ? 0 : wordRegions.Max(region => region.LayoutBounds.Right);
        var boundary = leftToRight ? wordLeft + (wordRight - wordLeft) * word.Progress(position)
            : wordRight - (wordRight - wordLeft) * word.Progress(position);
        for (var i = 0; i < graphemes.Count; i++)
        {
            var (start, length) = graphemes[i];
            var regions = layout.GetCharacterRegions(wordStart + start, length);
            var pose = NativeAmllMotion.Word(word, position, i, graphemes.Count, last);
            foreach (var region in regions)
            {
                var bounds = region.LayoutBounds;
                var center = new Vector2(x + (float)(bounds.X + bounds.Width / 2),
                    y + (float)(bounds.Y + bounds.Height / 2));
                using (ds.CreateLayer(1f, new Rect(x + bounds.X, y + bounds.Y,
                    bounds.Width, bounds.Height)))
                {
                    ds.Transform = Matrix3x2.CreateScale((float)pose.Scale, center) *
                        Matrix3x2.CreateTranslation((float)(pose.XEm * size), (float)(pose.YEm * size));
                    var sung = word.Progress(position) >= 1;
                    if (sung || pose.Glow > .001)
                    {
                        var shape = _text.Geometry(layout);
                        var tint = Color.FromArgb(255, 235, 239, 247);
                        if (sung) ds.DrawGeometry(shape, x, y,
                            Color.FromArgb(20, tint.R, tint.G, tint.B), Math.Max(1, size * .05f));
                        if (pose.Glow > .001)
                        {
                            ds.DrawGeometry(shape, x, y,
                                Color.FromArgb((byte)Math.Clamp((int)(pose.Glow * 42), 0, 255),
                                    tint.R, tint.G, tint.B), Math.Max(1, size * .11f));
                            ds.DrawGeometry(shape, x, y,
                                Color.FromArgb((byte)Math.Clamp((int)(pose.Glow * 92), 0, 255),
                                    tint.R, tint.G, tint.B), Math.Max(1, size * .045f));
                        }
                    }
                    ds.DrawTextLayout(layout, x, y, Color.FromArgb(255, 126, 129, 138));
                    ds.Transform = Matrix3x2.Identity;
                }
                var brightLeft = leftToRight ? bounds.X : Math.Max(bounds.X, boundary);
                var brightRight = leftToRight ? Math.Min(bounds.Right, boundary) : bounds.Right;
                if (brightRight > brightLeft)
                {
                    using (ds.CreateLayer(1f, new Rect(x + brightLeft, y + bounds.Y,
                        brightRight - brightLeft, bounds.Height)))
                    {
                        ds.Transform = Matrix3x2.CreateScale((float)pose.Scale, center) *
                            Matrix3x2.CreateTranslation((float)(pose.XEm * size), (float)(pose.YEm * size));
                        ds.DrawTextLayout(layout, x, y, Colors.White);
                        ds.Transform = Matrix3x2.Identity;
                    }
                }
            }
        }
    }

    private static float ActiveX(CanvasTextLayout layout, NativeLyricRow row, long position)
    {
        var start = 0;
        var active = 0f;
        var leftToRight = IsLeftToRight(layout);
        var width = (float)layout.LayoutBounds.Width;
        foreach (var word in row.TimedWords)
        {
            var length = Math.Min(word.Text.Length, row.Main.Length - start);
            if (length <= 0) break;
            var progress = word.Progress(position);
            if (progress > 0)
            {
                var regions = layout.GetCharacterRegions(start, length);
                if (regions.Length > 0)
                {
                    var bounds = regions[^1].LayoutBounds;
                    active = leftToRight
                        ? (float)(bounds.X + bounds.Width * progress - layout.LayoutBounds.X)
                        : width - (float)(bounds.Right - bounds.Width * progress - layout.LayoutBounds.X);
                }
            }
            start += length;
        }
        return active;
    }

    private static bool IsLeftToRight(CanvasTextLayout layout)
    {
        var textLength = layout.ClusterMetrics.Sum(cluster => cluster.CharacterCount);
        if (textLength < 2) return true;
        var first = layout.GetCharacterRegions(0, 1);
        var last = layout.GetCharacterRegions(textLength - 1, 1);
        return first.Length == 0 || last.Length == 0 ||
            first[0].LayoutBounds.X <= last[^1].LayoutBounds.X;
    }

    private static bool Bool(JsonElement? settings, string key, bool fallback) =>
        settings is { ValueKind: JsonValueKind.Object } json && json.TryGetProperty(key, out var value) &&
            value.ValueKind is JsonValueKind.True or JsonValueKind.False ? value.GetBoolean() : fallback;
    private static string String(JsonElement? settings, string key, string fallback) =>
        settings is { ValueKind: JsonValueKind.Object } json && json.TryGetProperty(key, out var value) &&
            value.ValueKind == JsonValueKind.String ? value.GetString() ?? fallback : fallback;
    private static int Number(JsonElement? settings, string key, int fallback) =>
        settings is { ValueKind: JsonValueKind.Object } json && json.TryGetProperty(key, out var value) &&
            value.ValueKind == JsonValueKind.Number ? value.GetInt32() : fallback;
    private static float FontSize(JsonElement? settings) => Math.Clamp(Number(settings, "fontSize", 22), 10, 96);

    public void Dispose()
    {
        _target?.Dispose();
        _text.Dispose();
    }
}
