// SPDX-License-Identifier: GPL-3.0-only
using System.Diagnostics;
using System.Text;
using System.Text.Json;
using IslandHost;

var parentText = ReadArgument("--parent-pid");
var pipeName = ReadArgument("--spectrum-pipe");
if (!int.TryParse(parentText, out var parentId) || parentId <= 0 ||
    string.IsNullOrWhiteSpace(pipeName) || pipeName.Length > 100)
    return 2;

Console.InputEncoding = Encoding.UTF8;
Console.OutputEncoding = Encoding.UTF8;
using var parent = Process.GetProcessById(parentId);
using var cancellation = new CancellationTokenSource();
var state = new IslandHostState();
var commands = new HostCommandWriter(Console.Out);
var spectrum = SpectrumChannel.Receive(pipeName, state, cancellation.Token);
var watcher = Task.Run(async () =>
{
    while (!cancellation.IsCancellationRequested)
    {
        if (parent.HasExited) Environment.Exit(0);
        await Task.Delay(1000, cancellation.Token);
    }
});

Console.Out.WriteLine("{\"type\":\"ready\",\"protocol\":1}");
Console.Out.Flush();
try
{
    while (Console.ReadLine() is { } line)
    {
        using var document = JsonDocument.Parse(line);
        var root = document.RootElement;
        if (root.GetProperty("type").GetString() == "state") state.Accept(root);
    }
}
catch (Exception error) when (error is JsonException or IOException)
{
    Console.Error.WriteLine(error);
    return 3;
}
finally
{
    cancellation.Cancel();
    try { await spectrum; } catch (OperationCanceledException) { }
    catch (EndOfStreamException) { }
    try { await watcher; } catch (OperationCanceledException) { }
}
return 0;

string? ReadArgument(string name)
{
    var index = Array.IndexOf(args, name);
    return index >= 0 && index + 1 < args.Length ? args[index + 1] : null;
}
