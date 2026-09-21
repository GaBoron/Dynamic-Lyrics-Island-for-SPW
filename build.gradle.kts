// Build layout adapted from Moriafly/spw-workshop-api (Apache-2.0).
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.gradle.internal.os.OperatingSystem

plugins {
    kotlin("jvm") version "2.3.0"
    `java-library`
}
group = "io.github.gaboron"
java { toolchain { languageVersion.set(JavaLanguageVersion.of(21)) } }
kotlin { compilerOptions { jvmTarget.set(JvmTarget.JVM_21) } }
val workshop = "com.github.Moriafly:spw-workshop-api:0.1.0-dev20"
val projectUrl = providers.gradleProperty("projectUrl")
val currentOs = OperatingSystem.current()
val targetPlatform = providers.gradleProperty("targetPlatform").orNull
require(targetPlatform == null || targetPlatform == "windows" || targetPlatform == "linux") {
    "targetPlatform must be windows or linux"
}
val isWindows = targetPlatform?.let { it == "windows" } ?: currentOs.isWindows
val isLinux = targetPlatform?.let { it == "linux" } ?: currentOs.isLinux
val metadataSources by configurations.creating { isTransitive = false }
val fontPickerOutput = layout.projectDirectory.dir(
    "native/font-picker/bin/x64/Release/net10.0-windows10.0.26100.0/win-x64"
)
dependencies {
    compileOnly(kotlin("stdlib"))
    compileOnly(workshop) { isTransitive = false }
    compileOnly("org.pf4j:pf4j:3.12.0")
    implementation("net.java.dev.jna:jna:5.17.0")
    implementation("net.java.dev.jna:jna-platform:5.17.0")
    implementation("net.jthink:jaudiotagger:3.0.1")
    metadataSources("net.jthink:jaudiotagger:3.0.1:sources")
}
tasks.processResources {
    if (isWindows) {
        dependsOn("buildSpectrum", "buildFontPicker")
        from(layout.buildDirectory.file("native/spw-spectrum.exe")) { into("native") }
        from(fontPickerOutput) {
            into("native/font-picker")
            exclude("*.pdb", "*.xml", "*.lib", "*.exp")
        }
    } else if (isLinux) {
        exclude { it.file == file("src/main/resources/preference_config.json") }
        from("src/linux/resources")
    }
    inputs.property("projectUrl", projectUrl)
    filesMatching("project.properties") { expand("projectUrl" to projectUrl.get()) }
}
tasks.jar {
    manifest.attributes(
        "Plugin-Class" to "io.github.gaboron.spwisland.host.IslandPlugin",
        "Plugin-Id" to "io.github.gaboron.spwisland",
        "Plugin-Name" to "Dynamic Lyrics Island for SPW",
        "Plugin-Version" to project.version.toString(),
        "Plugin-Provider" to "GaBoron",
        "Plugin-Description" to "SPW lyrics island; concept by Lyricify / WXRIW (CC BY-SA 4.0)",
        "Plugin-Has-Config" to "true",
        "Plugin-Open-Source-Url" to projectUrl.get()
    )
}
tasks.register<Zip>("sourceArchive") {
    archiveFileName.set("dynamic-lyrics-island-for-spw-${project.version}-source.zip")
    destinationDirectory.set(layout.buildDirectory.dir("distributions"))
    from("src") { into("src") }
    from("native") {
        into("native")
        exclude("**/bin/**", "**/obj/**")
    }
    from("gradle") { into("gradle") }
    from("licenses") { into("licenses") }
    from("docs") { into("docs") }
    from("build.gradle.kts", "settings.gradle.kts", "gradle.properties", "gradlew", "gradlew.bat",
        "README.md", "LICENSE", "NOTICE", "THIRD_PARTY_NOTICES.md", ".gitignore")
}

tasks.register<Exec>("buildSpectrum") {
    val output = layout.buildDirectory.file("native/spw-spectrum.exe")
    inputs.files(fileTree("native") { include("*.cs") })
    outputs.file(output)
    onlyIf {
        if (!isWindows) logger.lifecycle("Skipping the Windows spectrum helper on ${currentOs.name}")
        isWindows
    }
    doFirst { output.get().asFile.parentFile.mkdirs() }
    executable = "${System.getenv("WINDIR") ?: "C:/Windows"}/Microsoft.NET/Framework64/v4.0.30319/csc.exe"
    args("/nologo", "/target:winexe", "/platform:x64", "/optimize+", "/out:${output.get().asFile.absolutePath}",
        file("native/AudioInterop.cs").absolutePath, file("native/Spectrum.cs").absolutePath,
        file("native/ProcessLoopback.cs").absolutePath, file("native/SpectrumLevels.cs").absolutePath)
}

tasks.register<Exec>("buildFontPicker") {
    val project = file("native/font-picker/IslandFontPicker.csproj")
    inputs.files(fileTree("native/font-picker") { exclude("bin/**", "obj/**") })
    inputs.files(fileTree("src/main/resources/fonts"))
    outputs.dir(fontPickerOutput)
    onlyIf {
        if (!isWindows) logger.lifecycle("Skipping the WinUI font picker on ${currentOs.name}")
        isWindows
    }
    commandLine(
        "dotnet", "build", project.absolutePath,
        "-c", "Release", "-p:Platform=x64", "-p:RuntimeIdentifier=win-x64",
        "--self-contained", "true"
    )
}

fun registerPluginArchive(taskName: String, platform: String, enabled: Boolean) = tasks.register<Zip>(taskName) {
    dependsOn(tasks.jar, "sourceArchive")
    onlyIf {
        if (!enabled) logger.lifecycle("$taskName must run on a $platform host")
        enabled
    }
    archiveFileName.set("dynamic-lyrics-island-for-spw-${project.version}-$platform-x64.zip")
    destinationDirectory.set(layout.buildDirectory.dir("distributions"))
    into("classes") { from(tasks.jar.map { zipTree(it.archiveFile) }) }
    into("lib") { from(configurations.runtimeClasspath) }
    into("licenses") { from("licenses") }
    into("source") { from(tasks.named("sourceArchive")); from(metadataSources) }
    from("LICENSE", "NOTICE", "THIRD_PARTY_NOTICES.md", "README.md")
}

val pluginWindows = registerPluginArchive("pluginWindows", "windows", isWindows)
val pluginLinux = registerPluginArchive("pluginLinux", "linux", isLinux)

tasks.register("plugin") {
    group = "build"
    description = "Builds the plugin archive for the current host platform."
    dependsOn(if (isWindows) pluginWindows else pluginLinux)
    doFirst {
        check(isWindows || isLinux) { "Only Windows and Linux plugin archives are supported" }
    }
}

