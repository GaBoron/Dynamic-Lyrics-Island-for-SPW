// SPDX-License-Identifier: GPL-3.0-only
using System.Collections.ObjectModel;
using Microsoft.UI.Xaml;
using Microsoft.UI.Xaml.Controls;
using Microsoft.UI.Xaml.Navigation;

namespace IslandFontPicker;

public sealed partial class MainPage : Page
{
    private readonly IReadOnlyList<FontOption> _allFonts = FontCatalog.Load();
    private FontOption? _selectedFont;
    private string _style = "normal";
    private int _stretch = 5;
    private PickerSession? _session;

    public ObservableCollection<FontOption> FilteredFonts { get; } = [];
    public ObservableCollection<FontShapeOption> SelectedShapes { get; } = [];

    public MainPage()
    {
        InitializeComponent();
        ApplyFilter(string.Empty);
    }

    protected override void OnNavigatedTo(NavigationEventArgs e)
    {
        base.OnNavigatedTo(e);
        _session = (PickerSession)e.Parameter;
        _style = _session.Options.FontStyle;
        _stretch = _session.Options.FontStretch;
        var font = FindSelection(_session.Options.FontFamily);
        FontList.SelectedItem = font;
        SelectFont(font);
        WeightSlider.Value = _session.Options.FontWeight;
        SizeSlider.Value = _session.Options.FontSize;
        FaceSelector.SelectedItem = SelectedShapes.FirstOrDefault(shape =>
            shape.Style == _style && shape.Stretch == _stretch) ?? SelectedShapes.FirstOrDefault();
        UpdatePreview();
        DispatcherQueue.TryEnqueue(() => FontList.ScrollIntoView(font));
    }

    private void UpdatePreview()
    {
        if (PrimaryPreviewText is null || WeightSlider is null || SizeSlider is null ||
            WeightValueText is null || SizeValueText is null) return;
        var weightValue = (int)Math.Round(WeightSlider.Value);
        var sizeValue = (int)Math.Round(SizeSlider.Value);
        var weight = new Windows.UI.Text.FontWeight { Weight = (ushort)weightValue };
        PrimaryPreviewText.FontWeight = weight;
        SecondaryPreviewText.FontWeight = weight;
        PrimaryPreviewText.FontSize = sizeValue;
        SecondaryPreviewText.FontSize = Math.Max(10, sizeValue * .7);
        WeightValueText.Text = weightValue.ToString();
        SizeValueText.Text = $"{sizeValue} px";
        var style = _style switch
        {
            "italic" => Windows.UI.Text.FontStyle.Italic,
            "oblique" => Windows.UI.Text.FontStyle.Oblique,
            _ => Windows.UI.Text.FontStyle.Normal
        };
        PrimaryPreviewText.FontStyle = style;
        SecondaryPreviewText.FontStyle = style;
        PrimaryPreviewText.FontStretch = (Windows.UI.Text.FontStretch)_stretch;
        SecondaryPreviewText.FontStretch = (Windows.UI.Text.FontStretch)_stretch;
    }

    private void WeightSlider_ValueChanged(object sender, Microsoft.UI.Xaml.Controls.Primitives.RangeBaseValueChangedEventArgs e) =>
        UpdatePreview();

    private void SizeSlider_ValueChanged(object sender, Microsoft.UI.Xaml.Controls.Primitives.RangeBaseValueChangedEventArgs e) =>
        UpdatePreview();

    private void FaceSelector_SelectionChanged(object sender, SelectionChangedEventArgs e)
    {
        if (FaceSelector.SelectedItem is not FontShapeOption shape) return;
        _style = shape.Style;
        _stretch = shape.Stretch;
        UpdatePreview();
    }

    private FontOption FindSelection(string family)
    {
        var stored = family.Trim();
        var exact = _allFonts.FirstOrDefault(font =>
            string.Equals(font.StorageName, stored, StringComparison.CurrentCultureIgnoreCase));
        if (exact is not null) return exact;
        // Older settings could contain a full face name. Selecting its family saves only that family.
        var parts = stored.Split(' ', StringSplitOptions.RemoveEmptyEntries).ToList();
        while (parts.Count > 1 && LegacyStyleWords.Contains(parts[^1]))
        {
            parts.RemoveAt(parts.Count - 1);
            var baseName = string.Join(' ', parts);
            var match = _allFonts.FirstOrDefault(font =>
                string.Equals(font.StorageName, baseName, StringComparison.CurrentCultureIgnoreCase));
            if (match is not null) return match;
        }
        return _allFonts[0];
    }

    private static readonly HashSet<string> LegacyStyleWords = new(StringComparer.OrdinalIgnoreCase)
    {
        "Thin", "Light", "DemiLight", "SemiLight", "Regular", "Medium", "SemiBold",
        "Bold", "ExtraBold", "Black", "Heavy", "Italic", "Oblique"
    };

    private void ApplyFilter(string query)
    {
        var normalized = query.Trim();
        FilteredFonts.Clear();
        foreach (var font in _allFonts.Where(font =>
                     normalized.Length == 0 ||
                     font.DisplayName.Contains(normalized, StringComparison.CurrentCultureIgnoreCase)))
            FilteredFonts.Add(font);
        FontCountText.Text = $"{FilteredFonts.Count} 项";
    }

    private void SelectFont(FontOption font)
    {
        _selectedFont = font;
        SelectedShapes.Clear();
        foreach (var shape in FontCatalog.ShapesFor(font)) SelectedShapes.Add(shape);
        FaceSelector.SelectedItem = SelectedShapes.FirstOrDefault(shape =>
            shape.Style == _style && shape.Stretch == _stretch) ?? SelectedShapes.FirstOrDefault();
        PrimaryPreviewText.FontFamily = font.Family;
        SecondaryPreviewText.FontFamily = font.Family;
        SelectedFontText.Text = font.IsBundled
            ? "内置 MiSans · 默认歌词字体"
            : $"{font.DisplayName} · 系统字体";
    }

    private void FontSearchBox_TextChanged(AutoSuggestBox sender, AutoSuggestBoxTextChangedEventArgs args)
    {
        if (args.Reason == AutoSuggestionBoxTextChangeReason.UserInput)
            ApplyFilter(sender.Text);
    }

    private void FontList_SelectionChanged(object sender, SelectionChangedEventArgs e)
    {
        if (FontList.SelectedItem is FontOption font && !ReferenceEquals(font, _selectedFont))
            SelectFont(font);
    }

    private void PreviewInput_TextChanged(object sender, TextChangedEventArgs e)
    {
        if (PrimaryPreviewText is null) return;
        PrimaryPreviewText.Text = string.IsNullOrWhiteSpace(PreviewInput.Text)
            ? "若深夜的风掠过海面"
            : PreviewInput.Text;
    }

    private void RestoreDefault_Click(object sender, RoutedEventArgs e)
    {
        FontSearchBox.Text = string.Empty;
        ApplyFilter(string.Empty);
        FontList.SelectedItem = _allFonts[0];
        FontList.ScrollIntoView(_allFonts[0]);
        SelectFont(_allFonts[0]);
        FaceSelector.SelectedItem = SelectedShapes.FirstOrDefault(shape =>
            shape.Style == "normal" && shape.Stretch == 5) ?? SelectedShapes.FirstOrDefault();
        WeightSlider.Value = 400;
        SizeSlider.Value = 22;
        UpdatePreview();
    }

    private void Apply_Click(object sender, RoutedEventArgs e)
    {
        if (_selectedFont is not null)
            _session?.Complete(new PickerSelection(_selectedFont.StorageName,
                (int)Math.Round(WeightSlider.Value), (int)Math.Round(SizeSlider.Value), _style, _stretch));
    }

    private void Cancel_Click(object sender, RoutedEventArgs e) => _session?.Complete(null);
}
