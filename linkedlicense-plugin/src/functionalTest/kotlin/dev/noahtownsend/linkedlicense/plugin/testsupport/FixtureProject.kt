package dev.noahtownsend.linkedlicense.plugin.testsupport

import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner
import java.io.File

/**
 * A disposable Gradle project applying the plugin under test plus a Kotlin Gradle plugin
 * (`org.jetbrains.kotlin.jvm` or `.multiplatform`), wired against a [MavenFixtureRepo].
 *
 * Plain-JVM fixtures ([writeSettings]/[writeBuildFile]) inject the plugin under test via
 * `GradleRunner.withPluginClasspath()` and run with [run]/[runAndFail]. Multiplatform fixtures
 * ([writeSettingsForPublishedPlugin]/[writeMultiplatformBuildFile]) instead resolve it from a
 * local Maven repo and run with [runPublished]/[runPublishedAndFail] - see
 * [writeMultiplatformBuildFile]'s doc for why the two need different injection mechanisms.
 *
 * Functional tests here run only `generate*LicenseCatalog` tasks (not `build`) and assert on
 * their outcome / the generated source text - they don't compile the generated
 * `GeneratedLicenses.kt` against the real `dev.noahtownsend:linkedlicense` artifact, since
 * that library isn't published anywhere a fixture build could resolve it from. Simplification
 * flagged in the report.
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
            |        gradlePluginPortal()
            |        mavenCentral()
            |    }
            |}
            """.trimMargin(),
        )
    }

    fun writeBuildFile(
        dependencyCoordinates: List<String>,
        linkedLicenseBlock: String = "",
    ) {
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

    /**
     * A Kotlin Multiplatform equivalent of [writeBuildFile]: declares a `jvm()` target and,
     * optionally, one other platform target (e.g. `iosX64()`), each with its own dependency
     * set, so functional tests can assert per-target catalogs differ and `commonMain`'s is
     * their union. [otherTargetDsl]/[otherTargetSourceSetName] are both null to declare only
     * the `jvm()` target - e.g. for a fixture that needs to run a real Kotlin *compile* task
     * (not just the `generate*LicenseCatalog` tasks), where a second, native target would
     * require downloading the Kotlin/Native toolchain.
     *
     * Requires [writeSettingsForPublishedPlugin], not [writeSettings]: applying
     * `kotlin("multiplatform")` alongside a plugin injected via
     * `GradleRunner.withPluginClasspath()` (as [writeBuildFile]'s plain-JVM fixtures do) doesn't
     * exercise the same runtime classloader sharing a real consumer gets from resolving both
     * plugins together through the normal `plugins {}` DSL, so it throws NoClassDefFoundError
     * even though the underlying compileOnly fix (`linkedlicense-plugin/build.gradle.kts`)
     * genuinely works - confirmed separately via a real `includeBuild` consumer project.
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

        // Only needed when a fixture wants to resolve the real `dev.noahtownsend:linkedlicense`
        // artifact as an ordinary dependency (e.g. to actually *compile* the generated
        // `GeneratedLicenses.kt` against it) - see MultiplatformCompilationFunctionalTest. The
        // property is always set by the `functionalTest` Gradle task (linkedlicense-plugin's
        // build script), so this is safe to read unconditionally.
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
