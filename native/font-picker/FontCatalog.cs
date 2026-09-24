using System.Drawing.Text;
using System.Globalization;
using Microsoft.UI.Xaml.Media;

namespace IslandFontPicker;

internal static class FontCatalog
{
    private const string BundledFontSource = "ms-appx:///Assets/MiSansVF.ttf#MiSans VF";

    private static readonly (string Token, int Weight)[] WeightTokens =
    [
        ("extralight", 200), ("ultralight", 200), ("hairline", 100),
        ("semilight", 350), ("demilight", 350), ("thin", 100), ("light", 300),
        ("semibold", 600), ("demibold", 600), ("extrabold", 800), ("ultrabold", 800),
        ("medium", 500), ("regular", 400), ("normal", 400), ("book", 400), ("roman", 400),
        ("black", 900), ("heavy", 900), ("bold", 700)
    ];

    private static readonly string[] RemovableFamilyTokens =
    [
        "extralight", "ultralight", "hairline", "semilight", "demilight", "thin", "light",
        "semibold", "demibold", "extrabold", "ultrabold", "medium", "regular", "normal",
        "book", "roman", "black", "heavy", "bold"
    ];

    // Families already named after a weight (Segoe UI Light) must not offer a synthesized Bold face.
    private static readonly string[] WeightFamilyMarkers =
    [
        "extralight", "ultralight", "hairline", "semilight", "demilight", "thin", "light",
        "semibold", "demibold", "extrabold", "ultrabold", "medium", "black", "heavy", "bold"
    ];

    private static readonly (bool Bold, bool Italic)[] Styles =
    [
        (false, false), (true, false), (false, true), (true, true)
    ];

    public static IReadOnlyList<FontOption> Load()
    {
        var fonts = new List<FontOption>
        {
            new("", "内置 MiSans", "插件默认 · 多语言缺字回退",
                new FontFamily(BundledFontSource), BundledFaces())
        };

        using var installed = new InstalledFontCollection();
        var names = installed.Families
            .Select(family => family.Name)
            .Where(name => !string.IsNullOrWhiteSpace(name))
            .Distinct(StringComparer.CurrentCultureIgnoreCase)
            .OrderBy(name => name, StringComparer.Create(CultureInfo.CurrentCulture, true));

        // GDI+ exposes weight variants as separate families; group them so the style list
        // can offer every real face of the selected family.
        var groups = new Dictionary<string, List<string>>(StringComparer.CurrentCultureIgnoreCase);
        var order = new List<string>();
        foreach (var name in names)
        {
            var baseName = BaseFamily(name);
            if (!groups.TryGetValue(baseName, out var members))
            {
                members = [];
                groups[baseName] = members;
                order.Add(baseName);
            }
            members.Add(name);
        }

        foreach (var baseName in order)
        {
            var faces = groups[baseName]
                .SelectMany(EnumerateFaces)
                .DistinctBy(face => face.StorageName, StringComparer.CurrentCultureIgnoreCase)
                .OrderBy(face => face.Weight)
                .ThenBy(face => face.Italic)
                .ToList();
            if (faces.Count == 0) faces.Add(new FontFace(baseName, "常规 400", 400, false));
            fonts.Add(new FontOption(baseName, baseName, "系统已安装字体", new FontFamily(baseName), faces));
        }
        return fonts;
    }

    private static IReadOnlyList<FontFace> BundledFaces() =>
    [
        BundledFace("极细 100", 100),
        BundledFace("细体 300", 300),
        BundledFace("次细 350", 350),
        BundledFace("常规 400", 400),
        BundledFace("中等 500", 500),
        BundledFace("粗体 700", 700),
        BundledFace("极粗 900", 900)
    ];

    private static FontFace BundledFace(string displayName, int weight) =>
        new("", displayName, weight, false, new FontFamily(BundledFontSource));

    private static IEnumerable<FontFace> EnumerateFaces(string familyName)
    {
        System.Drawing.FontFamily family;
        try
        {
            family = new System.Drawing.FontFamily(familyName);
        }
        catch (ArgumentException)
        {
            yield break;
        }

        using (family)
        {
            var weightNamed = WeightFamilyMarkers.Any(marker => ContainsWord(familyName, marker));
            foreach (var (bold, italic) in Styles)
            {
                if (bold && weightNamed) continue;
                var style = (bold ? System.Drawing.FontStyle.Bold : System.Drawing.FontStyle.Regular) |
                            (italic ? System.Drawing.FontStyle.Italic : System.Drawing.FontStyle.Regular);
                if (!family.IsStyleAvailable(style)) continue;
                var fullName = familyName + (bold ? " Bold" : "") + (italic ? " Italic" : "");
                var weight = FaceWeight(familyName, bold);
                yield return new FontFace(fullName, $"{StyleLabel(weight, italic)} {weight}", weight, italic);
            }
        }
    }

    private static string BaseFamily(string name)
    {
        var tokens = name.Split(' ', StringSplitOptions.RemoveEmptyEntries);
        var end = tokens.Length;
        while (end > 1 && RemovableFamilyTokens.Contains(tokens[end - 1], StringComparer.OrdinalIgnoreCase))
        {
            end--;
        }
        return string.Join(' ', tokens[..end]);
    }

    private static int FaceWeight(string familyName, bool bold)
    {
        foreach (var (token, weight) in WeightTokens)
        {
            if (ContainsWord(familyName, token)) return weight;
        }
        return bold ? 700 : 400;
    }

    private static string StyleLabel(int weight, bool italic)
    {
        var label = weight switch
        {
            <= 150 => "极细",
            <= 250 => "特细",
            <= 300 => "细体",
            <= 350 => "次细",
            <= 450 => "常规",
            <= 550 => "中等",
            <= 650 => "半粗",
            <= 750 => "加粗",
            <= 850 => "特粗",
            _ => "极粗"
        };
        if (!italic) return label;
        return label == "常规" ? "斜体" : $"{label}斜体";
    }

    private static bool ContainsWord(string name, string token)
    {
        var normalized = name.ToLowerInvariant().Replace('-', ' ').Replace('_', ' ');
        return normalized == token || normalized.StartsWith(token + " ") ||
               normalized.EndsWith(" " + token) || normalized.Contains(" " + token + " ");
    }
}

public sealed class FontFace
{
    public FontFace(string storageName, string displayName, int weight, bool italic,
                    FontFamily? family = null)
    {
        StorageName = storageName;
        DisplayName = displayName;
        Weight = weight;
        Italic = italic;
        Family = family;
    }

    public string StorageName { get; set; }
    public string DisplayName { get; set; }
    public int Weight { get; set; }
    public bool Italic { get; set; }
    public FontFamily? Family { get; set; }
}

public sealed class FontOption
{
    public FontOption(string storageName, string displayName, string description,
                      FontFamily family, IReadOnlyList<FontFace> faces)
    {
        StorageName = storageName;
        DisplayName = displayName;
        Description = description;
        Family = family;
        Faces = faces;
    }

    public string StorageName { get; set; }
    public string DisplayName { get; set; }
    public string Description { get; set; }
    public FontFamily Family { get; set; }
    public IReadOnlyList<FontFace> Faces { get; set; }
    public bool IsBundled => string.IsNullOrEmpty(StorageName);
}
