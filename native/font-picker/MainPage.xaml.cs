using System.Collections.ObjectModel;
using Microsoft.UI.Text;
using Microsoft.UI.Xaml;
using Microsoft.UI.Xaml.Controls;
using Microsoft.UI.Xaml.Media;
using Microsoft.UI.Xaml.Navigation;

// To learn more about WinUI, the WinUI project structure,
// and more about our project templates, see: http://aka.ms/winui-project-info.

namespace IslandFontPicker;

/// <summary>
/// The main content page displayed inside the application window.
/// Add your UI logic, event handlers, and data binding here.
/// </summary>
public sealed partial class MainPage : Page
{
    private readonly IReadOnlyList<FontOption> _allFonts = FontCatalog.Load();
    private FontOption? _selectedFont;
    private FontFace? _selectedFace;
    private PickerSession? _session;

    public ObservableCollection<FontOption> FilteredFonts { get; } = [];
    public ObservableCollection<FontFace> SelectedFaces { get; } = [];

    public MainPage()
    {
        InitializeComponent();
        ApplyFilter(string.Empty);
    }

    protected override void OnNavigatedTo(NavigationEventArgs e)
    {
        base.OnNavigatedTo(e);
        _session = (PickerSession)e.Parameter;
        var (font, face) = FindSelection(_session.Options.FontFamily, _session.Options.FontWeight);
        FontList.SelectedItem = font;
        SelectFont(font, face);
        DispatcherQueue.TryEnqueue(() => FontList.ScrollIntoView(font));
    }

    private (FontOption Font, FontFace Face) FindSelection(string family, string weight)
    {
        var stored = family?.Trim() ?? string.Empty;
        if (stored.Length > 0)
        {
            // A stored family name keeps its saved weight; a stored full face name selects that exact face.
            foreach (var font in _allFonts)
            {
                if (string.Equals(font.StorageName, stored, StringComparison.CurrentCultureIgnoreCase))
                {
                    return (font, PreferredFace(font, weight));
                }
            }
            foreach (var font in _allFonts)
            {
                var face = font.Faces.FirstOrDefault(candidate =>
                    string.Equals(candidate.StorageName, stored, StringComparison.CurrentCultureIgnoreCase));
                if (face is not null) return (font, face);
            }
        }
        var fallback = _allFonts[0];
        return (fallback, PreferredFace(fallback, weight));
    }

    private static FontFace PreferredFace(FontOption font, string weight)
    {
        var target = int.TryParse(weight, out var parsed) ? parsed : 400;
        return font.Faces.MinBy(face => Math.Abs(face.Weight - target)) ?? font.Faces[0];
    }

    private void ApplyFilter(string query)
    {
        var normalized = query.Trim();
        FilteredFonts.Clear();
        foreach (var font in _allFonts.Where(font =>
                     normalized.Length == 0 ||
                     font.DisplayName.Contains(normalized, StringComparison.CurrentCultureIgnoreCase)))
        {
            FilteredFonts.Add(font);
        }

        FontCountText.Text = $"{FilteredFonts.Count} 项";
    }

    private void SelectFont(FontOption font, FontFace? preferred = null)
    {
        _selectedFont = font;
        PrimaryPreviewText.FontFamily = font.Family;
        SecondaryPreviewText.FontFamily = font.Family;
        SelectedFontText.Text = font.IsBundled
            ? "内置 MiSans · 推荐默认"
            : $"{font.DisplayName} · 系统字体";
        SelectedFaces.Clear();
        foreach (var face in font.Faces) SelectedFaces.Add(face);
        var target = preferred ?? PreferredFace(font, _session?.Options.FontWeight ?? "400");
        WeightSelector.SelectedIndex = IndexOfFace(font.Faces, target);
        StatusInfoBar.Severity = InfoBarSeverity.Informational;
        StatusInfoBar.Title = "字型说明";
        StatusInfoBar.Message = "只列出当前字体实际包含的字型；确认后词岛会直接使用所选的真实字面。";
    }

    private static int IndexOfFace(IReadOnlyList<FontFace> faces, FontFace target)
    {
        for (var index = 0; index < faces.Count; index++)
        {
            var face = faces[index];
            if (face.StorageName == target.StorageName && face.Weight == target.Weight && face.Italic == target.Italic)
            {
                return index;
            }
        }
        return 0;
    }

    private void UpdatePreview()
    {
        if (WeightSelector?.SelectedItem is not FontFace face) return;
        _selectedFace = face;
        var family = face.Family ?? _selectedFont?.Family;
        if (family is not null)
        {
            PrimaryPreviewText.FontFamily = family;
            SecondaryPreviewText.FontFamily = family;
        }
        var weight = WeightToFontWeight(face.Weight);
        var style = face.Italic ? Windows.UI.Text.FontStyle.Italic : Windows.UI.Text.FontStyle.Normal;
        PrimaryPreviewText.FontWeight = weight;
        PrimaryPreviewText.FontStyle = style;
        SecondaryPreviewText.FontWeight = weight;
        SecondaryPreviewText.FontStyle = style;
    }

    private static Windows.UI.Text.FontWeight WeightToFontWeight(int weight) => weight switch
    {
        <= 150 => FontWeights.Thin,
        <= 250 => FontWeights.ExtraLight,
        <= 300 => FontWeights.Light,
        <= 350 => FontWeights.SemiLight,
        <= 450 => FontWeights.Normal,
        <= 550 => FontWeights.Medium,
        <= 650 => FontWeights.SemiBold,
        <= 750 => FontWeights.Bold,
        <= 850 => FontWeights.ExtraBold,
        _ => FontWeights.Black
    };

    private void FontSearchBox_TextChanged(AutoSuggestBox sender, AutoSuggestBoxTextChangedEventArgs args)
    {
        if (args.Reason == AutoSuggestionBoxTextChangeReason.UserInput)
        {
            ApplyFilter(sender.Text);
        }
    }

    private void FontList_SelectionChanged(object sender, SelectionChangedEventArgs e)
    {
        // ListView raises this again after the initial selection; keep the face resolved in OnNavigatedTo.
        if (FontList.SelectedItem is FontOption font && !ReferenceEquals(font, _selectedFont)) SelectFont(font);
    }

    private void PreviewInput_TextChanged(object sender, TextChangedEventArgs e)
    {
        if (PrimaryPreviewText is null) return;
        PrimaryPreviewText.Text = string.IsNullOrWhiteSpace(PreviewInput.Text)
            ? "若深夜的风掠过海面"
            : PreviewInput.Text;
    }

    private void WeightSelector_SelectionChanged(object sender, SelectionChangedEventArgs e)
    {
        if (PrimaryPreviewText is not null) UpdatePreview();
    }

    private void RestoreDefault_Click(object sender, RoutedEventArgs e)
    {
        FontSearchBox.Text = string.Empty;
        ApplyFilter(string.Empty);
        FontList.SelectedItem = _allFonts[0];
        FontList.ScrollIntoView(_allFonts[0]);
        SelectFont(_allFonts[0]);
    }

    private void Apply_Click(object sender, RoutedEventArgs e)
    {
        if (_selectedFace is null) return;
        _session?.Complete(new PickerSelection(
            _selectedFace.StorageName,
            FontWeightsModel.Nearest(_selectedFace.Weight)));
    }

    private void Cancel_Click(object sender, RoutedEventArgs e)
    {
        _session?.Complete(null);
    }
}
