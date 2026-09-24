// SPDX-License-Identifier: GPL-3.0-only
using System.Text.Json;

namespace IslandHost;

// Native controls call this narrow return path; the Kotlin side owns SPW playback.
internal sealed class HostCommandWriter(TextWriter output)
{
    private readonly object _gate = new();

    public void Previous() => Send("previous");
    public void Toggle() => Send("toggle");
    public void Next() => Send("next");
    public void Seek(long positionMs) => Send("seek", Math.Max(0, positionMs));

    private void Send(string action, long? positionMs = null)
    {
        lock (_gate)
        {
            output.WriteLine(JsonSerializer.Serialize(new { type = "command", action, positionMs }));
            output.Flush();
        }
    }
}
