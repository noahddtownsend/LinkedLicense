rootProject.name = "LinkedLicense"

pluginManagement {
    repositories {
        // Dogfooding: `dev.noahtownsend.linkedlicense` isn't live yet on either Maven Central
        // (pending manual Central Portal release confirmation) or the Gradle Plugin Portal
        // (pending first-publish review), so `linkedlicense-compose`'s `plugins { id(...) }`
        // block can't resolve it from either. This local repo is the same one
        // `linkedlicense-plugin`'s functionalTest fixtures already publish
        // `linkedlicense-plugin` + root `linkedlicense` into and resolve the plugin from via
        // ordinary `plugins { id(...) version ... }` DSL.
        //
        // Populate it first with:
        //   ./gradlew :publishJvmPublicationToFunctionalTestRepository \
        //             :publishKotlinMultiplatformPublicationToFunctionalTestRepository \
        //             :linkedlicense-plugin:publishAllPublicationsToFunctionalTestRepository
        maven(rootDir.resolve("build/functionalTestRepo"))
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
        if (rootDir.resolve("build/functionalTestRepo").exists()) {
            maven(rootDir.resolve("build/functionalTestRepo"))
        }
        google()
        mavenCentral()
    }
}

include(":linkedlicense-plugin")
if (rootDir.resolve("build/functionalTestRepo").exists()) {
    include(":linkedlicense-compose")
}
