rootProject.name = "LinkedLicense"

pluginManagement {
    repositories {
        google()
        gradlePluginPortal()
        mavenCentral()

        // Dogfooding: `dev.noahtownsend.linkedlicense` isn't live yet on either Maven Central
        // (pending manual Central Portal release confirmation) or the Gradle Plugin Portal
        // (pending first-publish review), so `linkedlicense-compose`'s `plugins { id(...) }`
        // block (below) can't resolve it from either. This local repo is the same one
        // `linkedlicense-plugin`'s functionalTest fixtures already publish
        // `linkedlicense-plugin` + root `linkedlicense` into and resolve the plugin from via
        // ordinary `plugins { id(...) version ... }` DSL - see that module's build script for
        // the full explanation of why that's the supported mechanism (java-gradle-plugin's
        // auto-generated marker publication + maven-publish) rather than e.g.
        // `includeBuild(".")`, which doesn't apply here since `linkedlicense-plugin` is already
        // an `include()`d subproject of *this* build, not a separate build with its own
        // settings file.
        //
        // Populate it first with:
        //   ./gradlew :publishJvmPublicationToFunctionalTestRepository \
        //             :publishKotlinMultiplatformPublicationToFunctionalTestRepository \
        //             :linkedlicense-plugin:publishAllPublicationsToFunctionalTestRepository
        maven(rootDir.resolve("build/functionalTestRepo"))
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
