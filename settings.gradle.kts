pluginManagement {
    repositories {
        google()
        maven("https://maven-central.storage-download.googleapis.com/maven2/")
        gradlePluginPortal()
        mavenCentral()
    }
}
dependencyResolutionManagement {
    repositories {
        google()
        maven("https://maven-central.storage-download.googleapis.com/maven2/")
        mavenCentral()
        maven("https://jitpack.io") { content { includeGroup("com.github.Moriafly") } }
    }
}
rootProject.name = "dynamic-lyrics-island-for-spw"
