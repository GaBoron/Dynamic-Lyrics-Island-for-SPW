// SPDX-License-Identifier: GPL-3.0-only
using Microsoft.Graphics.Canvas;
using Microsoft.Graphics.Canvas.Text;
using Microsoft.Graphics.Canvas.Geometry;

namespace IslandHost;

/** DirectWrite text layout through Win2D. Cache shaping until the content or font changes. */
internal sealed class NativeTextLayout : IDisposable
{
    private readonly CanvasDevice _device = CanvasDevice.GetSharedDevice();
    private readonly Dictionary<(string Text, string Family, float Size, int Weight, string Style, int Stretch), CanvasTextLayout> _layouts = [];
    private readonly Dictionary<CanvasTextLayout, CanvasGeometry> _geometry = [];

    public CanvasTextLayout Get(string text, string family, float size, int weight = 400,
        string style = "normal", int stretch = 5)
    {
        var key = (text, family, size, weight, style, stretch);
        if (_layouts.TryGetValue(key, out var existing)) return existing;
        if (_layouts.Count >= 96)
        {
            foreach (var geometry in _geometry.Values) geometry.Dispose();
            _geometry.Clear();
            foreach (var layout in _layouts.Values) layout.Dispose();
            _layouts.Clear();
        }
        using var format = new CanvasTextFormat
        {
            FontFamily = string.IsNullOrWhiteSpace(family) ? "Segoe UI" : family,
            FontSize = size,
            FontWeight = new Windows.UI.Text.FontWeight { Weight = (ushort)Math.Clamp(weight, 100, 900) },
            FontStyle = style switch
            {
                "italic" => Windows.UI.Text.FontStyle.Italic,
                "oblique" => Windows.UI.Text.FontStyle.Oblique,
                _ => Windows.UI.Text.FontStyle.Normal
            },
            FontStretch = (Windows.UI.Text.FontStretch)Math.Clamp(stretch, 1, 9),
            WordWrapping = CanvasWordWrapping.NoWrap
        };
        var created = new CanvasTextLayout(_device, text, format, 10000, 1000);
        _layouts.Add(key, created);
        return created;
    }

    public CanvasGeometry Geometry(CanvasTextLayout layout)
    {
        if (!_geometry.TryGetValue(layout, out var geometry))
        {
            geometry = CanvasGeometry.CreateText(layout);
            _geometry.Add(layout, geometry);
        }
        return geometry;
    }

    public void Dispose()
    {
        foreach (var geometry in _geometry.Values) geometry.Dispose();
        _geometry.Clear();
        foreach (var layout in _layouts.Values) layout.Dispose();
        _layouts.Clear();
    }
}
