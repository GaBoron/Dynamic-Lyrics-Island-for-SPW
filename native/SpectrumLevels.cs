// SPDX-License-Identifier: GPL-3.0-only
using System;

namespace SpwIsland.Audio
{
    // One slowly adapting reference preserves the relative energy of all four bands.
    // The display is a responsive music visualizer, not a calibrated dBFS meter.
    internal sealed class SpectrumLevels
    {
        private const double FrameSeconds = 2048.0 / 44100;
        private const double NoiseFloor = .0003;
        private double reference;
        private double silentSeconds;

        internal double[] Map(double[] rms)
        {
            double peak = 0;
            foreach (double value in rms) peak = Math.Max(peak, value);
            if (peak < NoiseFloor)
            {
                silentSeconds += FrameSeconds;
                if (silentSeconds >= .5) reference = 0;
                return new double[rms.Length];
            }
            silentSeconds = 0;
            double target = Math.Max(.004, peak);
            if (reference == 0) reference = target;
            else
            {
                // Follow a louder passage in 250 ms, a quieter one in 4 s. This leaves
                // short attacks visible instead of normalizing every frame to full height.
                double seconds = target > reference ? .25 : 4.0;
                reference += (target - reference) * (1 - Math.Exp(-FrameSeconds / seconds));
            }
            var levels = new double[rms.Length];
            for (int i = 0; i < rms.Length; i++)
            {
                if (rms[i] < NoiseFloor) continue;
                double relative = rms[i] / (reference * 1.25);
                // Sustained dominant bands settle near 53%; brief peaks can rise higher.
                // A smooth shoulder avoids hard clipping at the top of the display.
                levels[i] = 1 - Math.Exp(-Math.Pow(relative, 1.2));
            }
            return levels;
        }
        internal void Reset() { reference = 0; silentSeconds = 0; }
    }
}
