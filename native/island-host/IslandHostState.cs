// SPDX-License-Identifier: GPL-3.0-only
using System.Diagnostics;
using System.Text.Json;

namespace IslandHost;

internal sealed record HostView(
    long PositionMs, bool Playing, string Status, double PlaybackRate,
    JsonElement? Track, JsonElement? Line, JsonElement? Lyrics,
    JsonElement? Metadata, JsonElement? Settings, JsonElement? Displays,
    long LyricsRevision, float[] Spectrum);

// Owns the native process's playback clock. A future renderer reads this state locally.
internal sealed class IslandHostState
{
    private readonly object _gate = new();
    private long _anchorTicks;
    private long _positionMs;
    private bool _playing;
    private string _status = "IDLE";
    private double _rate = 1;
    private JsonElement? _track;
    private JsonElement? _line;
    private JsonElement? _lyrics;
    private JsonElement? _metadata;
    private JsonElement? _settings;
    private JsonElement? _displays;
    private long _lyricsRevision;
    private readonly float[] _spectrum = new float[4];

    public void Accept(JsonElement message)
    {
        lock (_gate)
        {
            _positionMs = message.GetProperty("positionMs").GetInt64();
            _playing = message.GetProperty("playing").GetBoolean();
            _status = message.GetProperty("status").GetString() ?? "IDLE";
            _rate = message.GetProperty("playbackRate").GetDouble();
            if (!double.IsFinite(_rate) || _rate < 0) _rate = 1;
            _anchorTicks = Stopwatch.GetTimestamp();
            Update("track", ref _track);
            Update("line", ref _line);
            Update("lyrics", ref _lyrics);
            Update("metadata", ref _metadata);
            Update("settings", ref _settings);
            Update("displays", ref _displays);
            if (message.TryGetProperty("track", out _) || message.TryGetProperty("line", out _) ||
                message.TryGetProperty("lyrics", out _) || message.TryGetProperty("settings", out _))
                _lyricsRevision++;
        }

        void Update(string key, ref JsonElement? destination)
        {
            if (message.TryGetProperty(key, out var value))
                destination = value.Clone();
        }
    }

    public long PositionMs
    {
        get
        {
            lock (_gate)
            {
                return CurrentPosition();
            }
        }
    }

    public HostView Snapshot()
    {
        lock (_gate)
            return new HostView(CurrentPosition(), _playing, _status, _rate, _track, _line,
                _lyrics, _metadata, _settings, _displays, _lyricsRevision,
                (float[])_spectrum.Clone());
    }

    private long CurrentPosition()
    {
        if (!_playing) return _positionMs;
        var elapsed = (Stopwatch.GetTimestamp() - _anchorTicks) * 1000.0 / Stopwatch.Frequency;
        return _positionMs + (long)(Math.Clamp(elapsed, 0, 2500) * _rate);
    }

    public void AcceptSpectrum(ReadOnlySpan<byte> bytes)
    {
        lock (_gate)
        {
            for (var i = 0; i < 4; i++)
            {
                var level = BitConverter.ToSingle(bytes.Slice(i * sizeof(float), sizeof(float)));
                _spectrum[i] = float.IsFinite(level) ? Math.Clamp(level, 0, 1) : 0;
            }
        }
    }
}
