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
    private PickerSession? _session;

    public ObservableCollection<FontOption> FilteredFonts { get; } = [];

    public MainPage()
    {
        InitializeComponent();
        ApplyFilter(string.Empty);
    }

    protected override void OnNavigatedTo(NavigationEventArgs e)
    {
        base.OnNavigatedTo(e);
        _session = (PickerSession)e.Parameter;
        var initial = _allFonts.FirstOrDefault(font => string.Equals(
            font.StorageName, _session.Options.FontFamily, StringComparison.CurrentCultureIgnoreCase)) ?? _allFonts[0];
        SelectFont(initial);
        FontList.SelectedItem = initial;
        WeightSelector.SelectedIndex = FontWeightsModel.IndexOf(_session.Options.FontWeight);
        DispatcherQueue.TryEnqueue(() => FontList.ScrollIntoView(initial));
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

    private void SelectFont(FontOption font)
    {
        _selectedFont = font;
        PrimaryPreviewText.FontFamily = font.Family;
        SecondaryPreviewText.FontFamily = font.Family;
        SelectedFontText.Text = font.IsBundled
            ? "内置 Noto Sans SC · 推荐默认"
            : $"{font.DisplayName} · 系统字体";
        StatusInfoBar.Severity = InfoBarSeverity.Informational;
        StatusInfoBar.Title = "字重说明";
        StatusInfoBar.Message = "自定义字体会优先使用对应的真实字重；缺少所选档位时使用最接近的已有字重。";
    }

    private void UpdateWeight()
    {
        if (WeightSelector?.SelectedItem is not ComboBoxItem item) return;
        var weight = FontWeightsModel.ToFontWeight(item.Tag?.ToString());
        PrimaryPreviewText.FontWeight = weight;
        SecondaryPreviewText.FontWeight = weight;
    }

    private void FontSearchBox_TextChanged(AutoSuggestBox sender, AutoSuggestBoxTextChangedEventArgs args)
    {
        if (args.Reason == AutoSuggestionBoxTextChangeReason.UserInput)
        {
            ApplyFilter(sender.Text);
        }
    }

    private void FontList_SelectionChanged(object sender, SelectionChangedEventArgs e)
    {
        if (FontList.SelectedItem is FontOption font) SelectFont(font);
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
        if (PrimaryPreviewText is not null) UpdateWeight();
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
        if (_selectedFont is null || WeightSelector.SelectedItem is not ComboBoxItem item) return;
        _session?.Complete(new PickerSelection(
            _selectedFont.StorageName,
            FontWeightsModel.Normalize(item.Tag?.ToString())));
    }

    private void Cancel_Click(object sender, RoutedEventArgs e)
    {
        _session?.Complete(null);
    }
}
