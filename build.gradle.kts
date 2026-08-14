plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidLibrary)
    `maven-publish`
}

// README §"Group / artifact": dev.noahtownsend:linkedlicense. Without an explicit group here,
// `linkedlicense-plugin`'s `implementation(project(":"))` dependency serializes into published
// Gradle Module Metadata with an empty "group" - which is invalid and fails to parse for any
// consumer (see linkedlicense-plugin/build.gradle.kts's functionalTest local-repo publishing).
group = "dev.noahtownsend"
version = "0.1.0"

// `linkedlicense-plugin`'s `implementation(project(":"))` publishes as a variant-aware
// dependency on this (root, multiplatform) module's coordinates, not directly on the `jvm`
// target's own artifact - Gradle Module Metadata resolves the actual `jvm` variant via this
// root module's "available-at" pointer at consumption time. That means a consumer resolving
// `linkedlicense-plugin` from a repo needs *this* root module (plus its `jvm` target module)
// published there too, or resolution fails with "Could not find dev.noahtownsend:LinkedLicense".
// Only the `jvm` and root (`kotlinMultiplatform`) publications are published here - see
// `linkedlicense-plugin/build.gradle.kts`'s `functionalTest` task, which depends on exactly
// those two tasks (not "publish all") to avoid requiring an Android SDK/iOS toolchain just to
// run functional tests; Gradle only ever fetches the specific target module a consumer's
// resolved variant needs, so the other targets' publications simply not existing in this repo
// is fine for a JVM-only consumer like the plugin's own functional test fixtures.
publishing {
    repositories {
        maven {
            name = "functionalTest"
            url = uri(layout.buildDirectory.dir("functionalTestRepo"))
        }
    }
}

kotlin {
    androidTarget {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11)
        }
    }

    listOf(
        iosX64(),
        iosArm64(),
        iosSimulatorArm64(),
    ).forEach {
        it.binaries.framework {
            baseName = "linkedlicense"
            isStatic = true
        }
    }

    jvm()

    js(IR) {
        browser()
    }

    @OptIn(org.jetbrains.kotlin.gradle.ExperimentalWasmDsl::class)
    wasmJs {
        browser()
    }

    sourceSets {
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
    }
}

android {
    namespace = "dev.noahtownsend.linkedlicense"
    compileSdk =
        libs.versions.android.compileSdk
            .get()
            .toInt()

    defaultConfig {
        minSdk =
            libs.versions.android.minSdk
                .get()
                .toInt()
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}
