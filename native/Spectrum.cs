// SPDX-License-Identifier: GPL-3.0-only
using System;
using System.Globalization;

namespace SpwIsland.Audio
{
    // Four real frequency bands, stereo power combined after the FFT to avoid phase cancellation.
    internal sealed class Spectrum
    {
        private const int Size = 2048;
        private readonly double[] left = new double[Size], right = new double[Size];
        private int used;
        internal string Push(short l, short r)
        {
            left[used] = l / 32768.0; right[used++] = r / 32768.0;
            if (used != Size) return null;
            used = 0;
            double[] a = Power(left), b = Power(right);
            int[] edges = { 40, 250, 1000, 4000, 16000 };
            string[] result = new string[4];
            for (int band = 0; band < 4; band++)
            {
                double power = 0;
                int start = Math.Max(1, (int)Math.Ceiling(edges[band] * Size / 44100.0));
                int end = Math.Min(Size / 2, (int)Math.Ceiling(edges[band + 1] * Size / 44100.0));
                for (int i = start; i < end; i++) power += (a[i] + b[i]) * .5;
                double amplitude = Math.Sqrt(power) * 4 / Size;
                double level = amplitude < .0005 ? 0 : Math.Max(0, Math.Min(1, (20 * Math.Log10(amplitude) + 66) / 60));
                result[band] = level.ToString("F4", CultureInfo.InvariantCulture);
            }
            return String.Join(",", result);
        }
        private static double[] Power(double[] samples)
        {
            double[] re = new double[Size], im = new double[Size];
            for (int i = 0; i < Size; i++) re[i] = samples[i] * (.5 - .5 * Math.Cos(2 * Math.PI * i / (Size - 1)));
            for (int i = 1, j = 0; i < Size; i++)
            {
                int bit = Size >> 1;
                for (; (j & bit) != 0; bit >>= 1) j ^= bit;
                j ^= bit;
                if (i < j) { double temp = re[i]; re[i] = re[j]; re[j] = temp; }
            }
            for (int length = 2; length <= Size; length <<= 1)
            {
                double angle = -2 * Math.PI / length;
                for (int start = 0; start < Size; start += length)
                {
                    double wr = 1, wi = 0;
                    for (int j = 0; j < length / 2; j++)
                    {
                        int even = start + j, odd = even + length / 2;
                        double tr = re[odd] * wr - im[odd] * wi, ti = re[odd] * wi + im[odd] * wr;
                        re[odd] = re[even] - tr; im[odd] = im[even] - ti;
                        re[even] += tr; im[even] += ti;
                        double next = wr * Math.Cos(angle) - wi * Math.Sin(angle);
                        wi = wr * Math.Sin(angle) + wi * Math.Cos(angle); wr = next;
                    }
                }
            }
            for (int i = 0; i < Size; i++) re[i] = re[i] * re[i] + im[i] * im[i];
            return re;
        }
    }
}
