using System.Text;

namespace IslandFontPicker;

public sealed record PickerOptions(string FontFamily, int FontWeight, int FontSize,
    string FontStyle, int FontStretch)
{
    public static PickerOptions Parse(string[] args)
    {
        string Read(string key, string fallback)
        {
            var index = Array.IndexOf(args, key);
            return index >= 0 && index + 1 < args.Length ? args[index + 1] : fallback;
        }
        var weight = int.TryParse(Read("--weight", "400"), out var parsed) ? parsed : 400;
        var size = int.TryParse(Read("--size", "22"), out var parsedSize) ? parsedSize : 22;
        var style = Read("--style", "normal");
        var stretch = int.TryParse(Read("--stretch", "5"), out var parsedStretch) ? parsedStretch : 5;
        return new PickerOptions(Read("--family", ""), Math.Clamp(weight, 100, 900),
            Math.Clamp(size, 14, 42), style is "italic" or "oblique" ? style : "normal",
            Math.Clamp(stretch, 1, 9));
    }
}

public sealed record PickerSelection(string FontFamily, int FontWeight, int FontSize,
    string FontStyle, int FontStretch);

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
        return $"APPLY:{(family.Length == 0 ? "-" : family)}:{selection.FontWeight}:{selection.FontSize}:{selection.FontStyle}:{selection.FontStretch}";
    }
}
