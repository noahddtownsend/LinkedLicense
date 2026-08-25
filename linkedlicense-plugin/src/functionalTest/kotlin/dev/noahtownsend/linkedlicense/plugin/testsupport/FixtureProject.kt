package dev.noahtownsend.linkedlicense.plugin.testsupport

import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner
import java.io.File

/**
 * A disposable Gradle project applying the plugin under test plus a Kotlin Gradle plugin
 * (`org.jetbrains.kotlin.jvm`, `.multiplatform`, or `.android`), wired against a [MavenFixtureRepo].
 */
class FixtureProject(
    val projectDir: File,
    val repo: MavenFixtureRepo,
) {
    fun writeSettings(catalogAliases: Map<String, String> = emptyMap()) {
        val catalogBlock =
            if (catalogAliases.isNotEmpty()) {
                """
                |dependencyResolutionManagement {
                |    versionCatalogs {
                |        create("libs") {
                ${catalogAliases.entries.joinToString("\n") { (alias, coordinate) ->
                    val (group, artifact) = coordinate.split(":")
                    "|            library(\"$alias\", \"$group\", \"$artifact\").version(\"1.0\")"
                }}
                |        }
                |    }
                |}
                """.trimMargin()
            } else {
                ""
            }

        File(projectDir, "settings.gradle.kts").writeText(
            """
            |rootProject.name = "fixture"
            |
            |pluginManagement {
            |    repositories {
            |        google()
            |        gradlePluginPortal()
            |        mavenCentral()
            |    }
            |}
            |
            $catalogBlock
            """.trimMargin(),
        )
    }

    /**
     * Like [writeSettings], but resolves the plugin under test from the local Maven repo
     * `functionalTest` publishes to (`linkedlicense-plugin/build.gradle.kts`) instead of relying
     * on `GradleRunner.withPluginClasspath()`. Required for Kotlin Multiplatform fixtures - see
     * [writeMultiplatformBuildFile]'s doc for why.
     */
    fun writeSettingsForPublishedPlugin() {
        val repoDir = requireNotNull(System.getProperty("linkedlicense.functionalTestRepo")) {
            "linkedlicense.functionalTestRepo system property not set - run via the functionalTest Gradle task."
        }

        File(projectDir, "settings.gradle.kts").writeText(
            """
            |rootProject.name = "fixture"
            |
            |pluginManagement {
            |    repositories {
            |        maven { url = uri("$repoDir") }
            |        google()
            |        gradlePluginPortal()
            |        mavenCentral()
            |    }
            |}
            """.trimMargin(),
        )
    }

    fun writeLocalProperties(sdkDir: String = "/Users/noahtownsend/Library/Android/sdk") {
        File(projectDir, "local.properties").writeText("sdk.dir=$sdkDir\n")
    }

    fun writeBuildFile(
        dependencyCoordinates: List<String>,
        linkedLicenseBlock: String = "",
    ) {
        val functionalTestRepoDir = System.getProperty("linkedlicense.functionalTestRepo")
        File(projectDir, "build.gradle.kts").writeText(
            """
            |import java.net.URI
            |
            |plugins {
            |    id("org.jetbrains.kotlin.jvm") version "2.3.21"
            |    id("dev.noahtownsend.linkedlicense")
            |}
            |
            |repositories {
            |    maven { url = URI.create("${repo.dir.toURI()}") }
            ${if (functionalTestRepoDir != null) "|    maven { url = URI.create(\"${File(functionalTestRepoDir).toURI()}\") }" else ""}
            |    mavenCentral()
            |}
            |
            |dependencies {
            ${dependencyCoordinates.joinToString("\n") { "|    implementation(\"$it\")" }}
            |}
            |
            $linkedLicenseBlock
            """.trimMargin(),
        )
    }

    fun writeAndroidBuildFile(
        dependencyCoordinates: List<String> = emptyList(),
        productFlavors: List<String> = emptyList(),
        linkedLicenseBlock: String = "",
        isLibrary: Boolean = false,
    ) {
        val functionalTestRepoDir = System.getProperty("linkedlicense.functionalTestRepo")
        val androidPlugin = if (isLibrary) "com.android.library" else "com.android.application"

        val flavorsBlock =
            if (productFlavors.isNotEmpty()) {
                """
                |    flavorDimensions += "env"
                |    productFlavors {
                ${productFlavors.joinToString("\n") { "|        create(\"$it\") { dimension = \"env\" }" }}
                |    }
                """.trimMargin()
            } else {
                ""
            }

        File(projectDir, "build.gradle.kts").writeText(
            """
            |import java.net.URI
            |
            |plugins {
            |    id("$androidPlugin") version "8.13.2"
            |    id("org.jetbrains.kotlin.android") version "2.3.21"
            |    id("dev.noahtownsend.linkedlicense")
            |}
            |
            |repositories {
            |    google()
            |    maven { url = URI.create("${repo.dir.toURI()}") }
            ${if (functionalTestRepoDir != null) "|    maven { url = URI.create(\"${File(functionalTestRepoDir).toURI()}\") }" else ""}
            |    mavenCentral()
            |}
            |
            |android {
            |    namespace = "com.example.app"
            |    compileSdk = 34
            |
            |    defaultConfig {
            |        minSdk = 24
            |    }
            $flavorsBlock
            |}
            |
            |dependencies {
            ${dependencyCoordinates.joinToString("\n") { "|    implementation(\"$it\")" }}
            |}
            |
            $linkedLicenseBlock
            """.trimMargin(),
        )
    }

    fun writeUnrecognizedPluginBuildFile() {
        File(projectDir, "build.gradle.kts").writeText(
            """
            |plugins {
            |    id("base")
            |    id("dev.noahtownsend.linkedlicense")
            |}
            """.trimMargin(),
        )
    }

    /**
     * A Kotlin Multiplatform equivalent of [writeBuildFile]: declares a `jvm()` target and,
     * optionally, one other platform target (e.g. `iosX64()`), each with its own dependency
     * set, so functional tests can assert per-target catalogs differ and `commonMain`'s is
     * their union.
     */
    fun writeMultiplatformBuildFile(
        otherTargetDsl: String? = null,
        otherTargetSourceSetName: String? = null,
        jvmDependencyCoordinates: List<String> = emptyList(),
        otherTargetDependencyCoordinates: List<String> = emptyList(),
        commonDependencyCoordinates: List<String> = emptyList(),
        linkedLicenseBlock: String = "",
    ) {
        val pluginVersion = System.getProperty("linkedlicense.version") ?: "0.1.0"
        val functionalTestRepoDir = System.getProperty("linkedlicense.functionalTestRepo")

        val otherTargetBlock =
            if (otherTargetDsl != null && otherTargetSourceSetName != null) {
                """
                |    $otherTargetDsl
                |
                |    sourceSets {
                |        $otherTargetSourceSetName.dependencies {
                ${otherTargetDependencyCoordinates.joinToString("\n") { "|            implementation(\"$it\")" }}
                |        }
                |    }
                """.trimMargin()
            } else {
                ""
            }

        File(projectDir, "build.gradle.kts").writeText(
            """
            |import java.net.URI
            |
            |plugins {
            |    kotlin("multiplatform") version "2.3.21"
            |    id("dev.noahtownsend.linkedlicense") version "$pluginVersion"
            |}
            |
            |repositories {
            |    maven { url = URI.create("${repo.dir.toURI()}") }
            ${if (functionalTestRepoDir != null) "|    maven { url = URI.create(\"${File(functionalTestRepoDir).toURI()}\") }" else ""}
            |    mavenCentral()
            |}
            |
            |kotlin {
            |    jvm()
            $otherTargetBlock
            |
            |    sourceSets {
            |        commonMain.dependencies {
            ${commonDependencyCoordinates.joinToString("\n") { "|            implementation(\"$it\")" }}
            |        }
            |        jvmMain.dependencies {
            ${jvmDependencyCoordinates.joinToString("\n") { "|            implementation(\"$it\")" }}
            |        }
            |    }
            |}
            |
            $linkedLicenseBlock
            """.trimMargin(),
        )
    }

    fun writeOverridesToml(toml: String) {
        File(projectDir, "linkedlicense.toml").writeText(toml)
    }

    fun run(vararg tasks: String): BuildResult =
        runner(*tasks).build()

    fun runAndFail(vararg tasks: String): BuildResult =
        runner(*tasks).buildAndFail()

    /**
     * For fixtures built with [writeMultiplatformBuildFile]/[writeSettingsForPublishedPlugin]:
     * resolves the plugin under test from the local Maven repo instead of injecting it via
     * `GradleRunner.withPluginClasspath()`. See [writeMultiplatformBuildFile]'s doc for why.
     */
    fun runPublished(vararg tasks: String): BuildResult =
        publishedPluginRunner(*tasks).build()

    fun runPublishedAndFail(vararg tasks: String): BuildResult =
        publishedPluginRunner(*tasks).buildAndFail()

    private fun runner(vararg tasks: String): GradleRunner =
        GradleRunner
            .create()
            .withProjectDir(projectDir)
            .withPluginClasspath()
            .withArguments(tasks.toList() + listOf("--stacktrace"))
            .forwardOutput()

    private fun publishedPluginRunner(vararg tasks: String): GradleRunner =
        GradleRunner
            .create()
            .withProjectDir(projectDir)
            .withArguments(tasks.toList() + listOf("--stacktrace"))
            .forwardOutput()

    fun generatedLicensesFile(): File = generatedLicensesFile("main")

    fun generatedLicensesFile(sourceSetName: String): File =
        File(
            projectDir,
            "build/generated/linkedlicense/$sourceSetName/dev/noahtownsend/linkedlicense/generated/" +
                "${sourceSetName.lowercase()}/GeneratedLicenses.kt",
        )

    fun thirdPartyNoticesFile(): File = File(projectDir, "THIRD-PARTY-NOTICES")
}
