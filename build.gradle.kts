// Build layout adapted from Moriafly/spw-workshop-api (Apache-2.0).
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    kotlin("jvm") version "2.3.0"
    `java-library`
}
group = "io.github.gaboron"
java { toolchain { languageVersion.set(JavaLanguageVersion.of(21)) } }
kotlin { compilerOptions { jvmTarget.set(JvmTarget.JVM_21) } }
val workshop = "com.github.Moriafly:spw-workshop-api:0.1.0-dev20"
val projectUrl = providers.gradleProperty("projectUrl")
val metadataSources by configurations.creating { isTransitive = false }
dependencies {
    compileOnly(kotlin("stdlib"))
    compileOnly(workshop) { isTransitive = false }
    compileOnly("org.pf4j:pf4j:3.12.0")
    implementation("net.java.dev.jna:jna:5.17.0")
    implementation("net.java.dev.jna:jna-platform:5.17.0")
    implementation("net.jthink:jaudiotagger:3.0.1")
    metadataSources("net.jthink:jaudiotagger:3.0.1:sources")
    testImplementation(kotlin("stdlib"))
    testImplementation(workshop) { isTransitive = false }
    testImplementation("org.pf4j:pf4j:3.12.0")
    testImplementation("junit:junit:4.13.2")
}
tasks.test { useJUnit(); systemProperty("java.awt.headless", "true") }
tasks.processResources {
    dependsOn("buildSpectrum")
    from(layout.buildDirectory.file("native/spw-spectrum.exe")) { into("native") }
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
    from("native") { into("native") }
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
    doFirst { output.get().asFile.parentFile.mkdirs() }
    executable = "${System.getenv("WINDIR") ?: "C:/Windows"}/Microsoft.NET/Framework64/v4.0.30319/csc.exe"
    args("/nologo", "/target:winexe", "/platform:x64", "/optimize+", "/out:${output.get().asFile.absolutePath}",
        file("native/AudioInterop.cs").absolutePath, file("native/Spectrum.cs").absolutePath,
        file("native/ProcessLoopback.cs").absolutePath, file("native/SpectrumLevels.cs").absolutePath)
}

tasks.register<Exec>("compileSpectrumTests") {
    val output = layout.buildDirectory.file("native/spectrum-tests.exe")
    val sources = listOf("native/Spectrum.cs", "native/SpectrumLevels.cs", "src/test/csharp/SpectrumTests.cs").map(::file)
    inputs.files(sources); outputs.file(output)
    doFirst { output.get().asFile.parentFile.mkdirs() }
    executable = "${System.getenv("WINDIR") ?: "C:/Windows"}/Microsoft.NET/Framework64/v4.0.30319/csc.exe"
    args("/nologo", "/target:exe", "/platform:x64", "/optimize+", "/out:${output.get().asFile.absolutePath}")
    args(sources.map { it.absolutePath })
}
tasks.register<Exec>("testSpectrum") {
    dependsOn("compileSpectrumTests")
    executable = layout.buildDirectory.file("native/spectrum-tests.exe").get().asFile.absolutePath
}
tasks.test { dependsOn("testSpectrum") }
tasks.register<Zip>("plugin") {
    dependsOn(tasks.jar, "sourceArchive")
    archiveFileName.set("dynamic-lyrics-island-for-spw-${project.version}.zip")
    destinationDirectory.set(layout.buildDirectory.dir("distributions"))
    into("classes") { from(tasks.jar.map { zipTree(it.archiveFile) }) }
    into("lib") { from(configurations.runtimeClasspath) }
    into("licenses") { from("licenses") }
    into("source") { from(tasks.named("sourceArchive")); from(metadataSources) }
    from("LICENSE", "NOTICE", "THIRD_PARTY_NOTICES.md", "README.md")
}
tasks.register<JavaExec>("preview") {
    description = "Show an interactive island using synthetic lyrics, without SPW."
    classpath = sourceSets.test.get().runtimeClasspath
    mainClass.set("io.github.gaboron.spwisland.PreviewKt")
}
tasks.register<JavaExec>("smoke") {
    description = "Verify hidden Windows overlay lifecycle with an isolated simulated SPW host."
    classpath = sourceSets.test.get().runtimeClasspath
    mainClass.set("io.github.gaboron.spwisland.LifecycleSmokeKt")
}
