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
        maven("https://jitpack.io") {
            content { includeGroup("com.github.Swordfish90") }
        }
    }
}

rootProject.name = "TitanSlot"
include(":app")
// The emulator cores, one on-demand feature module each (see app/build.gradle.kts).
include(":core_gambatte", ":core_mgba")
