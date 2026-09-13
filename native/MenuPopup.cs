// SPDX-License-Identifier: GPL-3.0-only
using System;
using System.Collections.Generic;
using System.Runtime.InteropServices;
using System.Windows;
using System.Windows.Controls;
using System.Windows.Controls.Primitives;
using System.Windows.Input;
using System.Windows.Interop;
using System.Windows.Media;

namespace SpwIsland.Menu
{
    internal sealed class MenuWindow : Window
    {
        private readonly bool dark;
        private readonly List<Button> buttons = new List<Button>();
        private readonly Brush text;
        private readonly Brush muted;
        private readonly Brush accent;
        private readonly Brush hover;
        private readonly NativePoint anchor;
        private bool closing;

        [StructLayout(LayoutKind.Sequential)]
        private struct NativePoint { internal int X, Y; }
        [StructLayout(LayoutKind.Sequential, CharSet = CharSet.Auto)]
        private struct MonitorInfo {
            internal int Size;
            internal int Left, Top, Right, Bottom;
            internal int WorkLeft, WorkTop, WorkRight, WorkBottom;
            internal int Flags;
        }
        [DllImport("user32.dll")] private static extern IntPtr MonitorFromPoint(NativePoint point, int flags);
        [DllImport("user32.dll", CharSet = CharSet.Auto)] private static extern bool GetMonitorInfo(IntPtr monitor, ref MonitorInfo info);
        [DllImport("user32.dll")] private static extern bool SetForegroundWindow(IntPtr window);
        [DllImport("user32.dll", EntryPoint = "GetWindowLongW")]
        private static extern int GetWindowLong(IntPtr window, int index);
        [DllImport("user32.dll", EntryPoint = "SetWindowLongW")]
        private static extern int SetWindowLong(IntPtr window, int index, int value);

        internal MenuWindow(IEnumerable<MenuEntry> entries, bool useDark, bool useAcrylic, IntPtr owner, int anchorX, int anchorY)
        {
            anchor = new NativePoint { X = anchorX, Y = anchorY };
            dark = useDark;
            text = Brush(useDark ? 0xF5 : 0x1B, useDark ? 0xF5 : 0x1B, useDark ? 0xF5 : 0x1B);
            muted = Brush(useDark ? 0x9D : 0x5D, useDark ? 0x9D : 0x5D, useDark ? 0x9D : 0x5D);
            accent = Brush(useDark ? 0x60 : 0x00, useDark ? 0xCD : 0x67, useDark ? 0xFF : 0xC0);
            hover = new SolidColorBrush(Color.FromArgb(useDark ? (byte)150 : (byte)138,
                useDark ? (byte)0x4A : (byte)0xE9, useDark ? (byte)0x4A : (byte)0xE9,
                useDark ? (byte)0x4A : (byte)0xE9));

            WindowStyle = WindowStyle.None;
            ResizeMode = ResizeMode.NoResize;
            AllowsTransparency = false;
            Background = useAcrylic ? Brushes.Transparent : Brush(useDark ? 0x20 : 0xF3,
                useDark ? 0x20 : 0xF3, useDark ? 0x20 : 0xF3);
            ShowInTaskbar = false;
            Topmost = true;
            SizeToContent = SizeToContent.WidthAndHeight;
            WindowStartupLocation = WindowStartupLocation.Manual;
            FontFamily = new FontFamily("Microsoft YaHei UI");
            FontSize = 13;

            var stack = new StackPanel { Margin = new Thickness(6) };
            foreach (var entry in entries) AddEntry(stack, entry);
            Content = new Border {
                MinWidth = 248,
                CornerRadius = new CornerRadius(8),
                BorderThickness = new Thickness(1),
                BorderBrush = BrushA(useDark ? 74 : 62, useDark ? 0xFF : 0x45,
                    useDark ? 0xFF : 0x45, useDark ? 0xFF : 0x45),
                // A quiet neutral veil keeps colorful wallpaper from tinting the command
                // surface too heavily while leaving DWM's Acrylic texture visible.
                Background = useAcrylic
                    ? BrushA(useDark ? 38 : 46, useDark ? 0x14 : 0xF8,
                        useDark ? 0x14 : 0xF8, useDark ? 0x14 : 0xF8)
                    : Brush(useDark ? 0x20 : 0xF3, useDark ? 0x20 : 0xF3,
                        useDark ? 0x20 : 0xF3),
                Child = stack
            };

            // Establish ownership before HWND creation so both tray and island menus
            // belong to the same overlay instead of a separate application's window group.
            if (owner != IntPtr.Zero) new WindowInteropHelper(this).Owner = owner;
            SourceInitialized += delegate {
                var handle = new WindowInteropHelper(this).Handle;
                const int extendedStyle = -20, toolWindow = 0x80, appWindow = 0x40000;
                SetWindowLong(handle, extendedStyle,
                    (GetWindowLong(handle, extendedStyle) | toolWindow) & ~appWindow);
                MenuBackdrop.Apply(this, dark, useAcrylic);
            };
            Loaded += delegate {
                PlaceAtAnchor();
                SetForegroundWindow(new WindowInteropHelper(this).Handle);
                Activate();
            };
            Deactivated += delegate { Dismiss(); };
            PreviewKeyDown += OnPreviewKeyDown;
        }

        private void AddEntry(Panel panel, MenuEntry entry)
        {
            if (entry.Kind == EntryKind.Separator) {
                panel.Children.Add(new Border { Height = 1, Margin = new Thickness(32, 6, 10, 6), Background =
                    BrushA(dark ? 54 : 42, dark ? 0xFF : 0x20, dark ? 0xFF : 0x20, dark ? 0xFF : 0x20) });
                return;
            }
            if (entry.Kind == EntryKind.Title) {
                panel.Children.Add(new TextBlock { Text = entry.Label, Foreground = muted, FontWeight = FontWeights.SemiBold,
                    FontSize = 11.5, Padding = new Thickness(32, 8, 10, 4) });
                return;
            }
            if (entry.Kind == EntryKind.Note) {
                panel.Children.Add(new TextBlock { Text = entry.Label, Foreground = muted, FontSize = 11.5,
                    Padding = new Thickness(32, 5, 10, 7), TextTrimming = TextTrimming.CharacterEllipsis });
                return;
            }

            var content = new Grid();
            content.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(22) });
            content.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(1, GridUnitType.Star) });
            var mark = new TextBlock { Text = entry.Kind == EntryKind.Toggle && entry.Selected ? "✓" : "",
                Foreground = accent, FontFamily = new FontFamily("Segoe UI Symbol"), FontSize = 14,
                VerticalAlignment = VerticalAlignment.Center };
            var label = new TextBlock { Text = entry.Label, Foreground = text, VerticalAlignment = VerticalAlignment.Center,
                TextTrimming = TextTrimming.CharacterEllipsis };
            Grid.SetColumn(mark, 0); Grid.SetColumn(label, 1);
            content.Children.Add(mark); content.Children.Add(label);

            var button = new Button { Tag = entry.Id, Content = content, Height = 32, Padding = new Thickness(10, 0, 12, 0),
                HorizontalContentAlignment = HorizontalAlignment.Stretch, Background = Brushes.Transparent,
                BorderThickness = new Thickness(0), FocusVisualStyle = null, Template = ButtonTemplate() };
            button.Click += delegate { Console.Out.WriteLine(entry.Id); Console.Out.Flush(); Dismiss(); };
            buttons.Add(button);
            panel.Children.Add(button);
        }

        private ControlTemplate ButtonTemplate()
        {
            var template = new ControlTemplate(typeof(Button));
            var border = new FrameworkElementFactory(typeof(Border), "surface");
            border.SetValue(Border.CornerRadiusProperty, new CornerRadius(5));
            border.SetValue(Border.BackgroundProperty, new TemplateBindingExtension(Control.BackgroundProperty));
            var presenter = new FrameworkElementFactory(typeof(ContentPresenter));
            presenter.SetValue(ContentPresenter.ContentProperty, new TemplateBindingExtension(ContentControl.ContentProperty));
            presenter.SetValue(ContentPresenter.HorizontalAlignmentProperty, HorizontalAlignment.Stretch);
            presenter.SetValue(ContentPresenter.VerticalAlignmentProperty, VerticalAlignment.Center);
            border.AppendChild(presenter); template.VisualTree = border;
            var hoverTrigger = new Trigger { Property = UIElement.IsMouseOverProperty, Value = true };
            hoverTrigger.Setters.Add(new Setter(Border.BackgroundProperty, hover, "surface"));
            template.Triggers.Add(hoverTrigger);
            var focusTrigger = new Trigger { Property = UIElement.IsKeyboardFocusedProperty, Value = true };
            focusTrigger.Setters.Add(new Setter(Border.BackgroundProperty, hover, "surface"));
            template.Triggers.Add(focusTrigger);
            return template;
        }

        private void OnPreviewKeyDown(object sender, KeyEventArgs eventArgs)
        {
            if (eventArgs.Key == Key.Escape) { Dismiss(); eventArgs.Handled = true; return; }
            int current = buttons.IndexOf(Keyboard.FocusedElement as Button);
            if (eventArgs.Key == Key.Down || eventArgs.Key == Key.Up) {
                int direction = eventArgs.Key == Key.Down ? 1 : -1;
                buttons[(current < 0 ? 0 : (current + direction + buttons.Count) % buttons.Count)].Focus();
                eventArgs.Handled = true;
            }
        }

        private void PlaceAtAnchor()
        {
            var info = new MonitorInfo { Size = Marshal.SizeOf(typeof(MonitorInfo)) };
            GetMonitorInfo(MonitorFromPoint(anchor, 2), ref info);
            var source = (HwndSource)PresentationSource.FromVisual(this);
            Matrix fromDevice = source.CompositionTarget.TransformFromDevice;
            Point point = fromDevice.Transform(new Point(anchor.X, anchor.Y));
            Point workStart = fromDevice.Transform(new Point(info.WorkLeft, info.WorkTop));
            Point workEnd = fromDevice.Transform(new Point(info.WorkRight, info.WorkBottom));
            Left = Math.Max(workStart.X, Math.Min(point.X, workEnd.X - ActualWidth));
            Top = point.Y + ActualHeight <= workEnd.Y ? point.Y : point.Y - ActualHeight;
            Top = Math.Max(workStart.Y, Math.Min(Top, workEnd.Y - ActualHeight));
        }

        private static Brush Brush(int red, int green, int blue) {
            var brush = new SolidColorBrush(Color.FromRgb((byte)red, (byte)green, (byte)blue)); brush.Freeze(); return brush;
        }

        private static Brush BrushA(int alpha, int red, int green, int blue) {
            var brush = new SolidColorBrush(Color.FromArgb((byte)alpha, (byte)red, (byte)green, (byte)blue));
            brush.Freeze(); return brush;
        }

        private void Dismiss() {
            if (closing) return;
            closing = true;
            Close();
        }
    }

    internal static class Program
    {
        [STAThread]
        private static int Main(string[] args)
        {
            try {
                var entries = MenuEntry.Read(Console.In);
                var app = new Application { ShutdownMode = ShutdownMode.OnMainWindowClose };
                bool dark = args.Length > 0 && args[0] == "dark";
                bool acrylic = args.Length < 2 || args[1] != "solid";
                var owner = args.Length > 2 ? new IntPtr(Int64.Parse(args[2])) : IntPtr.Zero;
                app.Run(new MenuWindow(entries, dark, acrylic, owner, Int32.Parse(args[3]), Int32.Parse(args[4])));
                return 0;
            } catch (Exception error) {
                Console.Error.WriteLine("Native popup menu: " + error);
                return 1;
            }
        }
    }
}
