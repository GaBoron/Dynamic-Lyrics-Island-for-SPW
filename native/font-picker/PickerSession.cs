using System.Text;
using Microsoft.UI.Text;

namespace IslandFontPicker;

public sealed record PickerOptions(string FontFamily, string FontWeight)
{
    public static PickerOptions Parse(string[] args)
    {
        string Read(string key, string fallback)
        {
            var index = Array.IndexOf(args, key);
            return index >= 0 && index + 1 < args.Length ? args[index + 1] : fallback;
        }
        return new PickerOptions(Read("--family", ""), FontWeightsModel.Normalize(Read("--weight", "400")));
    }
}

public sealed record PickerSelection(string FontFamily, string FontWeight);

public sealed class PickerSession(PickerOptions options, Action<PickerSelection?> completed)
{
    private bool _completed;
    public PickerOptions Options { get; } = options;

    public void Complete(PickerSelection? selection)
    {
        if (_completed) return;
        _completed = true;
        completed(selection);
    }
}

internal static class PickerProtocol
{
    public static string Serialize(PickerSelection selection)
    {
        var family = Convert.ToBase64String(Encoding.UTF8.GetBytes(selection.FontFamily));
        return $"APPLY:{selection.FontWeight}:{(family.Length == 0 ? "-" : family)}";
    }
}

internal static class FontWeightsModel
{
    private static readonly string[] Values = ["100", "300", "350", "400", "500", "700", "900"];

    public static string Normalize(string? value) => Values.Contains(value) ? value! : "400";
    public static string Nearest(int weight) =>
        Values.MinBy(value => Math.Abs(int.Parse(value) - weight)) ?? "400";
    public static int IndexOf(string? value) => Array.IndexOf(Values, Normalize(value));
    public static Windows.UI.Text.FontWeight ToFontWeight(string? value) => Normalize(value) switch
    {
        "100" => FontWeights.Thin,
        "300" => FontWeights.Light,
        "350" => FontWeights.SemiLight,
        "500" => FontWeights.Medium,
        "700" => FontWeights.Bold,
        "900" => FontWeights.Black,
        _ => FontWeights.Normal
    };
}
