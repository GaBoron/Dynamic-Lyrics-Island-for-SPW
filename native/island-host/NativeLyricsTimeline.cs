// SPDX-License-Identifier: GPL-3.0-only
using System.Diagnostics;
using System.Text.Json;

namespace IslandHost;

internal sealed record NativeLyricsFrame(
    IReadOnlyList<NativeLyricRow> Rows,
    IReadOnlyList<NativeLyricRow> Outgoing,
    long PositionMs,
    double Transition,
    double OffsetEm,
    double IncomingAlpha,
    double OutgoingAlpha,
    bool DetailedKaraoke);

/** Keeps selection and line transitions local to the native playback clock. */
internal sealed class NativeLyricsTimeline
{
    private long _revision = -1;
    private long _clockRevision = -1;
    private long _changedAt;
    private string? _track;
    private NativeLyricsDocument? _document;
    private IReadOnlyList<NativeLyricRow> _rows = [];
    private IReadOnlyList<NativeLyricRow> _outgoing = [];

    public NativeLyricsFrame Frame(HostView view)
    {
        var lowPerformance = Flag(view.Settings, "lowPerformance");
        var clockJumped = view.ClockRevision != _clockRevision;
        _clockRevision = view.ClockRevision;
        var track = view.Track?.GetRawText();
        var trackChanged = _track != track;
        _track = track;
        if (clockJumped || trackChanged) _outgoing = [];
        if (view.LyricsRevision != _revision)
        {
            _document = NativeLyricsSelection.Prepare(view);
            _revision = view.LyricsRevision;
        }
        var next = NativeLyricsSelection.Select(_document!, view.PositionMs);
        var lineChanged = !SameLines(_rows, next);
        if (lineChanged)
        {
            _outgoing = !lowPerformance && !clockJumped && !trackChanged ? _rows : [];
            _changedAt = clockJumped
                ? Stopwatch.GetTimestamp() - (long)(.65 * Stopwatch.Frequency)
                : Stopwatch.GetTimestamp();
        }
        _rows = next;
        var elapsed = (Stopwatch.GetTimestamp() - _changedAt) / (double)Stopwatch.Frequency;
        var transition = lowPerformance ? 1 : Math.Clamp(elapsed / .65, 0, 1);
        var spring = transition >= 1 ? 1 : NativeAmllMotion.Line(transition * .65);
        if (transition >= 1) _outgoing = [];
        return new NativeLyricsFrame(_rows, _outgoing, view.PositionMs,
            transition, 1 - spring,
            Math.Clamp(transition * 3, 0, 1), Math.Clamp(1 - transition * 3, 0, 1),
            !lowPerformance && Flag(view.Settings, "karaoke", true));
    }

    private static bool SameLines(IReadOnlyList<NativeLyricRow> first, IReadOnlyList<NativeLyricRow> second) =>
        first.Count == second.Count && first.Zip(second).All(pair =>
            pair.First.Line?.StartMs == pair.Second.Line?.StartMs &&
            pair.First.Line?.EndMs == pair.Second.Line?.EndMs &&
            pair.First.Line?.Text == pair.Second.Line?.Text &&
            pair.First.Main == pair.Second.Main);

    private static bool Flag(JsonElement? settings, string key, bool fallback = false) =>
        settings is { ValueKind: JsonValueKind.Object } json && json.TryGetProperty(key, out var value) &&
            value.ValueKind is JsonValueKind.True or JsonValueKind.False ? value.GetBoolean() : fallback;
}
