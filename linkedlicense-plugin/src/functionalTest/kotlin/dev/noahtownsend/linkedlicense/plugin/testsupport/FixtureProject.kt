package dev.noahtownsend.linkedlicense.plugin.testsupport

import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner
import java.io.File

/**
 * A disposable Gradle project applying the plugin under test (injected via
 * `GradleRunner.withPluginClasspath()`) plus `org.jetbrains.kotlin.jvm`, wired against a
 * [MavenFixtureRepo].
 *
 * Functional tests here run only the `generateLicenseCatalog` task (not `build`) and assert
 * on its outcome / the generated source text - they don't compile the generated
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

    fun writeOverridesToml(toml: String) {
        File(projectDir, "linkedlicense.toml").writeText(toml)
    }

    fun run(vararg tasks: String): BuildResult =
        runner(*tasks).build()

    fun runAndFail(vararg tasks: String): BuildResult =
        runner(*tasks).buildAndFail()

    private fun runner(vararg tasks: String): GradleRunner =
        GradleRunner
            .create()
            .withProjectDir(projectDir)
            .withPluginClasspath()
            .withArguments(tasks.toList() + listOf("--stacktrace"))
            .forwardOutput()

    fun generatedLicensesFile(): File =
        File(projectDir, "build/generated/linkedlicense/main/dev/noahtownsend/linkedlicense/generated/GeneratedLicenses.kt")

    fun thirdPartyNoticesFile(): File = File(projectDir, "THIRD-PARTY-NOTICES")
}
