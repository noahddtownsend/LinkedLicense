plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidLibrary)
    `maven-publish`
    alias(libs.plugins.vanniktechMavenPublish)
}

// README §"Group / artifact": dev.noahtownsend:linkedlicense. Without an explicit group here,
// `linkedlicense-plugin`'s `implementation(project(":"))` dependency serializes into published
// Gradle Module Metadata with an empty "group" - which is invalid and fails to parse for any
// consumer (see linkedlicense-plugin/build.gradle.kts's functionalTest local-repo publishing).
group = "dev.noahtownsend"
version = "0.1.0"

// Publishes this module's `jvm`/`kotlinMultiplatform` (and other target) publications to
// Maven Central via the Central Portal. Credentials/signing come from environment variables
// only (ORG_GRADLE_PROJECT_mavenCentralUsername/Password, ORG_GRADLE_PROJECT_signingInMemory*)
// - see this repo's publish-config notes; nothing here reads a file or prompts interactively.
mavenPublishing {
    publishToMavenCentral()

    // Only sign when an in-memory signing key is actually supplied (ORG_GRADLE_PROJECT_
    // signingInMemoryKey, surfaced here as the un-prefixed `signingInMemoryKey` Gradle
    // property). signAllPublications() unconditionally makes every publish task for every
    // publication - including `publishJvmPublicationToFunctionalTestRepository` and
    // `publishKotlinMultiplatformPublicationToFunctionalTestRepository`, which
    // `linkedlicense-plugin`'s `functionalTest` task depends on - require a configured
    // signatory, which fails locally/in CI runs that never touch Maven Central at all.
    if (project.findProperty("signingInMemoryKey") != null) {
        signAllPublications()
    }

    pom {
        name = "LinkedLicense"
        description =
            "A Kotlin Multiplatform library of open-source license templates, plus an opt-in " +
                "Gradle plugin that scans your project's dependency graph and generates the " +
                "license catalog for you."
        url = "https://github.com/noahddtownsend/LinkedLicense"

        licenses {
            license {
                name = "MIT"
                url = "https://github.com/noahddtownsend/LinkedLicense/blob/main/LICENSE"
            }
        }

        developers {
            developer {
                id = "noahddtownsend"
                name = "Noah Townsend"
                email = "noah@noahtownsend.com"
            }
        }

        scm {
            connection = "scm:git:git://github.com/noahddtownsend/LinkedLicense.git"
            developerConnection = "scm:git:git://github.com/noahddtownsend/LinkedLicense.git"
            url = "https://github.com/noahddtownsend/LinkedLicense"
        }
    }
}

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
