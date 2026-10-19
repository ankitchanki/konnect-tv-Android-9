pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // libadb-android (ADB pairing + TLS) is only published here.
        maven("https://jitpack.io")
    }
}

rootProject.name = "KonnectTV"
include(":app")
