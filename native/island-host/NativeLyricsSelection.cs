// SPDX-License-Identifier: GPL-3.0-only
using System.Text.Json;

namespace IslandHost;

internal sealed record NativeLyricWord(long StartMs, long EndMs, string Text)
{
    public double Progress(long position) => position < StartMs ? 0 :
        EndMs <= StartMs ? 1 : Math.Clamp((position - StartMs) / (double)(EndMs - StartMs), 0, 1);
}

internal sealed record NativeLyricLine(long StartMs, long EndMs, string Text,
    string? Translation, IReadOnlyList<NativeLyricWord> Words)
{
    public IReadOnlyList<NativeLyricWord> TimedWords =>
        Words.Count > 0 && string.Concat(Words.Select(word => word.Text)) == Text &&
        Words.All(word => word.EndMs >= word.StartMs) &&
        Words.Zip(Words.Skip(1)).All(pair => pair.First.StartMs <= pair.Second.StartMs)
            ? Words : Array.Empty<NativeLyricWord>();
    public bool HasWordTimingEvidence => TimedWords.Any(word =>
        word.StartMs != StartMs || word.EndMs != EndMs);
}

internal sealed record NativeLyricRow(NativeLyricLine? Line, string Main, string? Translation,
    IReadOnlyList<NativeLyricWord> TimedWords);

internal sealed record NativeLyricsDocument(NativeLyricLine? Current,
    IReadOnlyList<NativeLyricLine> Lyrics, string? Title, bool Experimental,
    bool Translation, bool SongUsesWordTiming);

internal static class NativeLyricsSelection
{
    public static NativeLyricsDocument Prepare(HostView view)
    {
        var settings = view.Settings;
        var experimental = Flag(settings, "experimentalMultiLine");
        var translation = Flag(settings, "translation", true);
        var current = ParseLine(view.Line);
        var lyrics = ParseLines(view.Lyrics);
        var songUsesWordTiming = (lyrics.Count > 0 ? lyrics : current is null ? [] : [current])
            .Any(line => line.HasWordTimingEvidence);
        var title = view.Track is { } track && track.ValueKind == JsonValueKind.Object &&
            track.TryGetProperty("title", out var name) ? name.GetString() : null;
        return new NativeLyricsDocument(current, lyrics, title, experimental, translation,
            songUsesWordTiming);
    }

    public static IReadOnlyList<NativeLyricRow> Select(NativeLyricsDocument document, long positionMs)
    {
        var current = document.Current;
        IReadOnlyList<NativeLyricLine> selected = current is null ? [] : [current];
        if (document.Experimental && document.Lyrics.Count > 0)
        {
            var reached = Math.Max(positionMs, current?.StartMs ?? 0);
            var started = document.Lyrics.Where(line => !string.IsNullOrWhiteSpace(line.Text) &&
                line.StartMs <= reached).ToList();
            if (started.Count > 0)
            {
                var active = started.Where(line => line.EndMs > reached && line.EndMs > line.StartMs).ToList();
                selected = (active.Count > 0 ? active : [started.MaxBy(line => line.EndMs)!])
                    .OrderBy(line => line.StartMs).ThenBy(line => line.EndMs).ToList();
            }
        }
        if (selected.Count == 0)
        {
            return [new NativeLyricRow(null,
                string.IsNullOrWhiteSpace(document.Title) ? "SPW" : document.Title!, null, [])];
        }
        return selected.Select(line => new NativeLyricRow(line,
            string.IsNullOrWhiteSpace(line.Text) ? "SPW" : line.Text,
            document.Translation && !string.IsNullOrWhiteSpace(line.Translation) ? line.Translation : null,
            document.SongUsesWordTiming ? line.TimedWords : [])).ToList();
    }

    private static bool Flag(JsonElement? settings, string key, bool fallback = false) =>
        settings is { ValueKind: JsonValueKind.Object } json && json.TryGetProperty(key, out var value) &&
            value.ValueKind is JsonValueKind.True or JsonValueKind.False ? value.GetBoolean() : fallback;

    private static IReadOnlyList<NativeLyricLine> ParseLines(JsonElement? element)
    {
        if (element is not { ValueKind: JsonValueKind.Array } array) return [];
        return array.EnumerateArray().Select(item => ParseLine(item)).OfType<NativeLyricLine>().ToList();
    }

    private static NativeLyricLine? ParseLine(JsonElement? element)
    {
        if (element is not { ValueKind: JsonValueKind.Object } json ||
            !json.TryGetProperty("startMs", out var start) ||
            !json.TryGetProperty("endMs", out var end) ||
            !json.TryGetProperty("text", out var text)) return null;
        var words = new List<NativeLyricWord>();
        if (json.TryGetProperty("words", out var array) && array.ValueKind == JsonValueKind.Array)
        {
            foreach (var item in array.EnumerateArray())
            {
                if (item.ValueKind != JsonValueKind.Object ||
                    !item.TryGetProperty("startMs", out var wordStart) ||
                    !item.TryGetProperty("endMs", out var wordEnd) ||
                    !item.TryGetProperty("text", out var wordText)) continue;
                words.Add(new NativeLyricWord(wordStart.GetInt64(), wordEnd.GetInt64(), wordText.GetString() ?? ""));
            }
        }
        var sub = json.TryGetProperty("translation", out var translation) &&
            translation.ValueKind == JsonValueKind.String ? translation.GetString() : null;
        return new NativeLyricLine(start.GetInt64(), end.GetInt64(), text.GetString() ?? "", sub, words);
    }
}
