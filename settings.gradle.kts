pluginManagement { repositories { gradlePluginPortal(); mavenCentral() } }
dependencyResolutionManagement {
    repositories {
        mavenCentral()
        maven("https://jitpack.io") { content { includeGroup("com.github.Moriafly") } }
    }
}
rootProject.name = "dynamic-lyrics-island-for-spw"
