// SPDX-License-Identifier: AGPL-3.0-only
// Adapted from the existing AMLL motion port in AmllMotion.kt; see NOTICE.
using System.Globalization;
using System.Text;

namespace IslandHost;

internal readonly record struct NativeWordPose(double XEm, double YEm, double Scale, double Glow);

internal static class NativeAmllMotion
{
    public static NativeWordPose Word(NativeLyricWord word, long time, int character, int count, bool last)
    {
        var duration = Math.Max(1000.0, (double)(word.EndMs - word.StartMs));
        var elapsed = (double)(time - word.StartMs);
        var lift = -.05 * Bezier(Math.Clamp(elapsed / duration, 0, 1), 0, 0, .58, 1);
        var cjk = word.Text.EnumerateRunes().Any(IsCjk);
        if (word.EndMs - word.StartMs < 1000 || string.IsNullOrWhiteSpace(word.Text) ||
            (!cjk && (word.Text.Trim().Length < 2 || word.Text.Trim().Length > 7)))
            return new NativeWordPose(0, lift, 1, 0);
        var amount = duration / 2000;
        amount = (amount > 1 ? Math.Sqrt(amount) : Math.Pow(amount, 3)) * .6;
        var blur = duration / 3000;
        blur = (blur > 1 ? Math.Sqrt(blur) : Math.Pow(blur, 3)) * .5;
        if (last) { amount *= 1.6; blur *= 1.5; duration *= 1.2; }
        amount = Math.Min(1.2, amount);
        blur = Math.Min(.8, blur);
        var progress = Math.Clamp((elapsed - duration / 2.5 / Math.Max(1, count) * character) / duration, 0, 1);
        var emphasis = progress < .5
            ? Bezier(progress * 2, .2, .4, .58, 1)
            : 1 - Bezier((progress - .5) * 2, .3, 0, .58, 1);
        var floatProgress = Math.Clamp((elapsed + 400) / (duration * 1.4), 0, 1);
        return new NativeWordPose(
            -emphasis * .03 * amount * (count / 2.0 - character),
            lift - emphasis * .025 * amount - Math.Sin(floatProgress * Math.PI) * .05,
            1 + emphasis * .1 * amount,
            emphasis * blur);
    }

    public static double Line(double seconds) =>
        1 - Math.Exp(-10 * seconds) * (Math.Cos(14 * seconds) + 10.0 / 14 * Math.Sin(14 * seconds));

    public static IReadOnlyList<(int Start, int Length)> Graphemes(string text)
    {
        var result = new List<(int, int)>();
        var enumerator = StringInfo.GetTextElementEnumerator(text);
        var start = 0;
        while (enumerator.MoveNext())
        {
            var next = enumerator.ElementIndex;
            if (next > start) result.Add((start, next - start));
            start = next;
        }
        if (start < text.Length) result.Add((start, text.Length - start));
        return result;
    }

    private static bool IsCjk(Rune rune)
    {
        var value = rune.Value;
        return value is >= 0x3400 and <= 0x9fff or >= 0xf900 and <= 0xfaff or
            >= 0x3040 and <= 0x30ff or >= 0x31f0 and <= 0x31ff or
            >= 0xac00 and <= 0xd7af or >= 0x1100 and <= 0x11ff or
            >= 0x20000 and <= 0x323af;
    }

    private static double Bezier(double x, double x1, double y1, double x2, double y2)
    {
        static double Curve(double t, double a, double b) =>
            3 * Math.Pow(1 - t, 2) * t * a + 3 * (1 - t) * t * t * b + Math.Pow(t, 3);
        var low = 0.0;
        var high = 1.0;
        for (var i = 0; i < 18; i++)
        {
            var middle = (low + high) / 2;
            if (Curve(middle, x1, x2) < x) low = middle;
            else high = middle;
        }
        return Curve((low + high) / 2, y1, y2);
    }
}
