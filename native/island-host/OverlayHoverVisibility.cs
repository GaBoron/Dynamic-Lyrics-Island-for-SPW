// SPDX-License-Identifier: GPL-3.0-only
using System.Diagnostics;

namespace IslandHost;

/** Animates click-through hover hiding without changing the window's input region. */
internal sealed class OverlayHoverVisibility
{
    private const double FadeSeconds = .24;
    private double _opacity = 1;
    private long _lastTick = Stopwatch.GetTimestamp();

    public byte Update(bool hidden, bool lowPerformance)
    {
        var now = Stopwatch.GetTimestamp();
        var elapsed = Math.Clamp((now - _lastTick) / (double)Stopwatch.Frequency, 0, FadeSeconds);
        _lastTick = now;
        var target = hidden ? 0.0 : 1.0;
        if (lowPerformance) _opacity = target;
        else if (_opacity < target) _opacity = Math.Min(target, _opacity + elapsed / FadeSeconds);
        else if (_opacity > target) _opacity = Math.Max(target, _opacity - elapsed / FadeSeconds);
        return (byte)Math.Round(_opacity * 255);
    }
}
