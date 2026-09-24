// SPDX-License-Identifier: GPL-3.0-only
using System.IO.Pipes;

namespace IslandHost;

// Fixed 16-byte frames keep optional spectrum traffic off the state/control pipe.
internal static class SpectrumChannel
{
    public static async Task Receive(string name, IslandHostState state, CancellationToken cancellation)
    {
        var frame = new byte[4 * sizeof(float)];
        while (!cancellation.IsCancellationRequested)
        {
            using var pipe = new NamedPipeServerStream(name, PipeDirection.In, 1,
                PipeTransmissionMode.Byte, PipeOptions.Asynchronous | PipeOptions.CurrentUserOnly);
            await pipe.WaitForConnectionAsync(cancellation);
            try
            {
                while (!cancellation.IsCancellationRequested)
                {
                    await pipe.ReadExactlyAsync(frame, cancellation);
                    state.AcceptSpectrum(frame);
                }
            }
            catch (EndOfStreamException) { }
            catch (IOException) when (!cancellation.IsCancellationRequested) { }
        }
    }
}
