rootProject.name = "LinkedLicense"

pluginManagement {
    repositories {
        google()
        gradlePluginPortal()
        mavenCentral()
    }

    // Declared centrally so the plugin marker resolves once for the whole build - applying
    // org.jetbrains.kotlin.jvm and org.jetbrains.kotlin.multiplatform (same underlying
    // artifact) in sibling projects with separately-specified versions trips Gradle's
    // "plugin already on classpath with unknown version" check otherwise.
    plugins {
        id("org.jetbrains.kotlin.jvm") version "2.3.21"
    }
}

@Suppress("UnstableApiUsage")
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
    }
}

include(":linkedlicense-plugin")
include(":linkedlicense-compose")
