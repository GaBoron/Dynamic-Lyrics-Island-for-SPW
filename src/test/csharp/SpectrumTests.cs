// SPDX-License-Identifier: GPL-3.0-only
using System;
using System.Globalization;
using SpwIsland.Audio;

internal static class SpectrumTests
{
    private static void Require(bool condition, string message) { if (!condition) throw new Exception(message); }
    private static double[] Frame(Spectrum spectrum, double amplitude, int frequency, int index, bool antiphase)
    {
        string result = null;
        for (int i = 0; i < 2048; i++)
        {
            short sample = (short)(32767 * amplitude * Math.Sin(2 * Math.PI * frequency * (index * 2048.0 + i) / 44100));
            result = spectrum.Push(sample, antiphase ? (short)-sample : sample);
        }
        return Array.ConvertAll(result.Split(','), x => Double.Parse(x, CultureInfo.InvariantCulture));
    }
    private static int Main()
    {
        int[] frequencies = { 110, 600, 2000, 8000 };
        foreach (double amplitude in new[] { .01, .1, .8 })
        for (int band = 0; band < 4; band++)
        {
            var spectrum = new Spectrum(); double[] levels = null;
            for (int frame = 0; frame < 32; frame++) levels = Frame(spectrum, amplitude, frequencies[band], frame, true);
            Require(levels[band] > .4 && levels[band] < .65, "Sustained signal lacks headroom");
            for (int other = 0; other < 4; other++) if (other != band)
                Require(levels[other] < .02, "Frequency leaked into another band");
        }
        var beats = new Spectrum(); double low = 0, high = 0;
        for (int frame = 0; frame < 120; frame++)
        {
            bool strong = frame % 10 < 2;
            double level = Frame(beats, strong ? .4 : .1, 110, frame, false)[0];
            if (frame >= 20) { if (strong) high += level; else low += level; }
        }
        high /= 20; low /= 80;
        Require(high - low > .3, "Beat envelope was flattened");
        var balanced = new SpectrumLevels(); double[] mixed = null;
        for (int i = 0; i < 120; i++) mixed = balanced.Map(new[] { .2, .1, .05, .025 });
        Require(mixed[0] > mixed[1] && mixed[1] > mixed[2] && mixed[2] > mixed[3], "Lost relative band balance");
        Require(mixed[0] < .65 && mixed[0] - mixed[3] > .4, "Steady bands crowded at the top");
        for (int i = 0; i < 15; i++) foreach (double level in balanced.Map(new double[4])) Require(level == 0, "Silence must be zero");
        var quiet = balanced.Map(new[] { .007, 0, 0, 0 });
        Require(quiet[0] > .4 && quiet[0] < .65, "Quiet track did not recover after silence");
        foreach (double level in balanced.Map(new[] { .00001, .00001, .00001, .00001 })) Require(level == 0, "Noise was amplified");
        Console.WriteLine("PASS: 12 tone/volume cases, antiphase stereo, band balance, silence/noise, quiet restart");
        Console.WriteLine("Beat envelope: low {0:F3}, high {1:F3}, swing {2:F3}", low, high, high - low);
        return 0;
    }
}
