// settings.gradle.kts

import org.gradle.api.initialization.resolve.RepositoriesMode
// Removed: No need to import VersionCatalogsExtension or explicitly get 'libs' here
// because plugin versions will be hardcoded for stability in pluginManagement.

pluginManagement {
    repositories {
        gradlePluginPortal()
        google()
        mavenCentral()
    }
    plugins {
        // Corrected: Declare all plugins directly with their ID and version strings.
        // This is the most robust way to ensure they are resolved by pluginManagement
        // at the earliest stage, bypassing 'libs' resolution issues in settings.gradle.kts.
        id("org.gradle.version-catalog") // This plugin still makes 'libs' available for *subprojects*.
        id("com.google.devtools.ksp") version "1.9.20-1.0.14" apply false
        id("com.android.application") version "8.10.1" apply false
        id("org.jetbrains.kotlin.android") version "1.9.20" apply false
        // Removed kotlin.plugin.compose as per previous discussion, but leaving as a comment if you intended to keep it
        // id("org.jetbrains.kotlin.plugin.compose") version "1.9.20" apply false
    }
}

// Removed: The 'libs' variable declaration is no longer needed in settings.gradle.kts itself
// as plugin versions are now hardcoded in the pluginManagement block.
// It remains available for app/build.gradle.kts through the standard mechanism.


dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "CookieTimer"
include(":app")
