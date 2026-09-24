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
    public void SetSetting(string key, bool value) => Write(new { type = "setting", key, value });
    public void SetSetting(string key, string value) => Write(new { type = "setting", key, value });
    public void ShowAbout() => Send("about");
    public void OpenSource() => Send("source");
    public void SavePosition(string screen, int x, int y, string anchor, int monitorX, int monitorY) =>
        Write(new { type = "position", screen, x, y, anchor, monitorX, monitorY });
    public void ResetPosition() => Write(new { type = "resetPosition" });

    private void Send(string action, long? positionMs = null)
    {
        Write(new { type = "command", action, positionMs });
    }

    private void Write<T>(T message)
    {
        lock (_gate)
        {
            output.WriteLine(JsonSerializer.Serialize(message));
            output.Flush();
        }
    }
}
