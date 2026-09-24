// SPDX-License-Identifier: GPL-3.0-only
using System.Globalization;
using Microsoft.Graphics.Canvas.Text;
using Microsoft.UI.Xaml.Media;
using IslandHost;

namespace IslandFontPicker;

internal static class FontCatalog
{
    private static readonly Dictionary<string, IReadOnlyList<FontShapeOption>> SystemShapes = LoadShapes();

    public static IReadOnlyList<FontShapeOption> ShapesFor(FontOption option) => option.IsBundled
        ? [new("标准", "normal", 5)]
        : SystemShapes.GetValueOrDefault(option.StorageName) ?? [new("标准", "normal", 5)];

    private static Dictionary<string, IReadOnlyList<FontShapeOption>> LoadShapes()
    {
        using var installed = CanvasFontSet.GetSystemFontSet();
        return installed.Fonts
            .Where(face => (int)face.Stretch is >= 1 and <= 9)
            .SelectMany(face => face.FamilyNames.Values.Select(family => (family, face)))
            .GroupBy(item => item.family, StringComparer.CurrentCultureIgnoreCase)
            .ToDictionary(group => group.Key, group => (IReadOnlyList<FontShapeOption>)group
                .Select(item => Shape(item.face))
                .DistinctBy(shape => (shape.Style, shape.Stretch))
                .OrderBy(shape => shape.Stretch == 5 ? 0 : 1)
                .ThenBy(shape => shape.Stretch)
                .ThenBy(shape => shape.Style).ToList(),
                StringComparer.CurrentCultureIgnoreCase);
    }

    private static FontShapeOption Shape(CanvasFontFace face)
    {
        var style = face.Style switch
        {
            Windows.UI.Text.FontStyle.Italic => "italic",
            Windows.UI.Text.FontStyle.Oblique => "oblique",
            _ => "normal"
        };
        var stretch = (int)face.Stretch;
        var width = stretch switch
        {
            1 => "极窄", 2 => "特窄", 3 => "窄体", 4 => "微窄",
            6 => "微宽", 7 => "宽体", 8 => "特宽", 9 => "极宽", _ => ""
        };
        var slant = style switch { "italic" => "斜体", "oblique" => "倾斜", _ => "" };
        var label = string.Join(" · ", new[] { width, slant }.Where(part => part.Length > 0));
        return new FontShapeOption(label.Length == 0 ? "标准" : label, style, stretch);
    }

    public static IReadOnlyList<FontOption> Load()
    {
        var fonts = new List<FontOption>
        {
            new("", "内置 MiSans", "默认歌词字体 · 缺字由系统字体补齐",
                new FontFamily(NativeFontDefaults.BundledFontSource))
        };
        var names = CanvasTextFormat.GetSystemFontFamilies()
            .Where(name => !string.IsNullOrWhiteSpace(name))
            .Distinct(StringComparer.CurrentCultureIgnoreCase)
            .OrderBy(name => name, StringComparer.Create(CultureInfo.CurrentCulture, true));
        foreach (var name in names)
            fonts.Add(new FontOption(name, name, "系统已安装字体", new FontFamily(name)));
        return fonts;
    }
}

public sealed class FontShapeOption(string displayName, string style, int stretch)
{
    public string DisplayName { get; set; } = displayName;
    public string Style { get; set; } = style;
    public int Stretch { get; set; } = stretch;
}

public sealed class FontOption(string storageName, string displayName, string description,
    FontFamily family)
{
    public string StorageName { get; } = storageName;
    public string DisplayName { get; } = displayName;
    public string Description { get; } = description;
    public FontFamily Family { get; } = family;
    public bool IsBundled => StorageName.Length == 0;
}
