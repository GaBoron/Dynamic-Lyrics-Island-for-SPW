// SPDX-License-Identifier: GPL-3.0-only
package io.github.gaboron.spwisland.platform

import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeUnit

/** Shared prerequisite check for unpackaged, framework-dependent Windows UI processes. */
internal object WindowsNativeRuntime {
    private const val WINDOWS_APP_RUNTIME = "Microsoft.WindowsAppRuntime.1.8"
    // Microsoft.WindowsAppSDK.Runtime 1.8.260804001 declares this framework package version.
    private const val MIN_WINDOWS_APP_RUNTIME = "8000.946.1701.0"
    private const val DESKTOP_RUNTIME = "Microsoft.WindowsDesktop.App 10."

    fun isReady(): Boolean = hasDesktopRuntime() && hasWindowsAppRuntime()

    private fun hasDesktopRuntime(): Boolean {
        val dotnet = Path.of(System.getenv("ProgramFiles") ?: "C:\\Program Files", "dotnet", "dotnet.exe")
        if (!Files.isRegularFile(dotnet)) return false
        return output(dotnet.toString(), "--list-runtimes")
            ?.lineSequence()?.any { it.startsWith(DESKTOP_RUNTIME) } == true
    }

    private fun hasWindowsAppRuntime(): Boolean {
        // Query packages registered for this user, not merely files present on the machine.
        val query = "Get-AppxPackage -Name '$WINDOWS_APP_RUNTIME' -PackageTypeFilter Framework | " +
            "Where-Object { \$_.Architecture -eq 'X64' -and \$_.Version -ge [version]'$MIN_WINDOWS_APP_RUNTIME' } | " +
            "Select-Object -ExpandProperty Name"
        return output("powershell.exe", "-NoProfile", "-NonInteractive", "-Command", query)
            ?.lineSequence()?.any { it.trim() == WINDOWS_APP_RUNTIME } == true
    }

    private fun output(vararg command: String): String? = runCatching {
        val process = ProcessBuilder(*command).redirectErrorStream(true).start()
        if (!process.waitFor(8, TimeUnit.SECONDS)) {
            process.destroyForcibly()
            return null
        }
        if (process.exitValue() == 0) process.inputStream.bufferedReader().use { it.readText() } else null
    }.getOrNull()
}
