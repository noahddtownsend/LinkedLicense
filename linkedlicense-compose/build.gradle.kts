import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.vanniktechMavenPublish)

    // Dogfooding: scans this module's own dependency graph (Compose Multiplatform
    // runtime/foundation/material3/resources et al.) and generates its license catalog.
    // Resolved from the local `functionalTestRepo` staged by `linkedlicense-plugin` - see
    // settings.gradle.kts's `pluginManagement` block for why, and the populate command. Not
    // self-referential the way applying this to `linkedlicense-plugin` itself would be: this
    // module doesn't build the plugin, it only consumes it.
    id("dev.noahtownsend.linkedlicense") version "0.9.4"
}

group = "dev.noahtownsend"
version = "0.9.4"

// See the root build script's `mavenPublishing` block for the credentials/signing notes -
// identical convention applies here.
mavenPublishing {
    publishToMavenCentral()

    // See the root build script's `mavenPublishing` block for why this is conditional.
    if (project.findProperty("signingInMemoryKey") != null) {
        signAllPublications()
    }

    pom {
        name = "LinkedLicense Compose"
        description = "Ready-made Compose Multiplatform UI components for displaying LinkedLicense catalogs."
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

kotlin {
    androidTarget {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_11)
        }
    }

    listOf(
        iosX64(),
        iosArm64(),
        iosSimulatorArm64(),
    ).forEach {
        it.binaries.framework {
            baseName = "linkedlicenseCompose"
            isStatic = true
        }
    }

    jvm()

    // Compose Multiplatform does not currently support the plain js(IR) target - only
    // wasmJs. The root `linkedlicense` module keeps js(IR) since License itself has no UI
    // dependency; this UI module drops it.
    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        browser()
    }

    sourceSets {
        commonMain.dependencies {
            api(project(":"))
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.ui)
            implementation(compose.components.resources)
        }

        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }

        val jvmTest by getting {
            dependencies {
                implementation(compose.desktop.currentOs)
                @OptIn(org.jetbrains.compose.ExperimentalComposeLibrary::class)
                implementation(compose.uiTest)
            }
        }
    }
}

android {
    namespace = "dev.noahtownsend.linkedlicense.compose"
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

compose.resources {
    packageOfResClass = "dev.noahtownsend.linkedlicense.compose.generated.resources"
}
