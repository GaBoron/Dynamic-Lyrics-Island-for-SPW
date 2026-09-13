// SPDX-License-Identifier: GPL-3.0-only
using System;
using System.Runtime.InteropServices;
using System.Windows;
using System.Windows.Interop;
using System.Windows.Media;

namespace SpwIsland.Menu
{
    internal static class MenuBackdrop
    {
        private const int UseImmersiveDarkMode = 20;
        private const int WindowCornerPreference = 33;
        private const int SystemBackdropType = 38;
        private const int RedirectionBitmapAlpha = 39;

        [StructLayout(LayoutKind.Sequential)]
        private struct Margins { internal int Left, Right, Top, Bottom; }

        [DllImport("dwmapi.dll")]
        private static extern int DwmSetWindowAttribute(IntPtr window, int attribute, ref int value, int size);

        [DllImport("dwmapi.dll")]
        private static extern int DwmExtendFrameIntoClientArea(IntPtr window, ref Margins margins);

        internal static void Apply(Window window, bool dark, bool acrylic)
        {
            var source = (HwndSource)PresentationSource.FromVisual(window);
            if (source == null) return;

            int darkMode = dark ? 1 : 0;
            int roundSmall = 3;
            DwmSetWindowAttribute(source.Handle, UseImmersiveDarkMode, ref darkMode, sizeof(int));
            DwmSetWindowAttribute(source.Handle, WindowCornerPreference, ref roundSmall, sizeof(int));
            if (acrylic) {
                source.CompositionTarget.BackgroundColor = Colors.Transparent;
                int preserveAlpha = 1;
                int acrylicType = 3;
                DwmSetWindowAttribute(source.Handle, RedirectionBitmapAlpha, ref preserveAlpha, sizeof(int));
                DwmSetWindowAttribute(source.Handle, SystemBackdropType, ref acrylicType, sizeof(int));
                var margins = new Margins { Left = -1, Right = -1, Top = -1, Bottom = -1 };
                DwmExtendFrameIntoClientArea(source.Handle, ref margins);
            }
        }
    }
}
