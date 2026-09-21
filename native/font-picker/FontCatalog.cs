using System.Drawing.Text;
using System.Globalization;
using Microsoft.UI.Xaml.Media;

namespace IslandFontPicker;

internal static class FontCatalog
{
    private const string BundledFontSource = "ms-appx:///Assets/NotoSansSC-Regular.otf#Noto Sans SC";

    public static IReadOnlyList<FontOption> Load()
    {
        var fonts = new List<FontOption>
        {
            new("", "内置 Noto Sans SC", "插件默认 · 多语言缺字回退", new FontFamily(BundledFontSource))
        };
        using var installed = new InstalledFontCollection();
        var names = installed.Families
            .Select(family => family.Name)
            .Where(name => !string.IsNullOrWhiteSpace(name))
            .Distinct(StringComparer.CurrentCultureIgnoreCase)
            .OrderBy(name => name, StringComparer.Create(CultureInfo.CurrentCulture, true));
        fonts.AddRange(names.Select(name =>
            new FontOption(name, name, "系统已安装字体", new FontFamily(name))));
        return fonts;
    }
}

public sealed class FontOption
{
    public FontOption(string storageName, string displayName, string description, FontFamily family)
    {
        StorageName = storageName;
        DisplayName = displayName;
        Description = description;
        Family = family;
    }

    public string StorageName { get; set; }
    public string DisplayName { get; set; }
    public string Description { get; set; }
    public FontFamily Family { get; set; }
    public bool IsBundled => string.IsNullOrEmpty(StorageName);
}
