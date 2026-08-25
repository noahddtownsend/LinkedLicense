package dev.noahtownsend.linkedlicense.plugin

import dev.noahtownsend.linkedlicense.plugin.testsupport.FixtureProject
import dev.noahtownsend.linkedlicense.plugin.testsupport.MavenFixtureRepo
import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Regression check for the plain `org.jetbrains.kotlin.jvm` path (a single `main` source set,
 * `generateLicenseCatalog`): adding Kotlin Multiplatform / Android support must not change its behavior,
 * and wiring generated sources into Kotlin source sets must not fail with SourceTask cast exceptions.
 */
class PlainJvmRegressionFunctionalTest {
    @TempDir
    lateinit var projectDir: File

    @Test
    fun `a plain kotlin-jvm project still generates a single-source-set catalog via generateLicenseCatalog`() {
        val repo = MavenFixtureRepo(File(projectDir, "repo"))
        repo.publish(group = "com.example", artifact = "plain-jvm-lib", version = "1.0", licenseName = "MIT")

        val fixture = FixtureProject(projectDir, repo)
        fixture.writeSettings()
        fixture.writeBuildFile(dependencyCoordinates = listOf("com.example:plain-jvm-lib:1.0"))

        val result = fixture.run("generateLicenseCatalog")

        assertTrue(result.output.contains("BUILD SUCCESSFUL"), result.output)
        val generated = fixture.generatedLicensesFile().readText()
        assertTrue(generated.contains("elementLicensed = \"plain-jvm-lib\""), generated)
        assertTrue(generated.contains("License.MIT"), generated)
    }

    @Test
    fun `tasks --all lists generateLicenseCatalog without configuration errors`() {
        val repo = MavenFixtureRepo(File(projectDir, "repo"))
        val fixture = FixtureProject(projectDir, repo)
        fixture.writeSettings()
        fixture.writeBuildFile(dependencyCoordinates = emptyList())

        val result = fixture.run("tasks", "--all")

        assertTrue(result.output.contains("BUILD SUCCESSFUL"), result.output)
        assertTrue(result.output.contains("generateLicenseCatalog"), result.output)
    }

    @Test
    fun `gradle build compiles generated catalog without SourceTask cast exceptions`() {
        val repo = MavenFixtureRepo(File(projectDir, "repo"))
        repo.publish(group = "com.example", artifact = "plain-jvm-lib", version = "1.0", licenseName = "MIT")
        val pluginVersion = System.getProperty("linkedlicense.version") ?: "0.1.0"

        val fixture = FixtureProject(projectDir, repo)
        fixture.writeSettings()
        fixture.writeBuildFile(
            dependencyCoordinates = listOf(
                "com.example:plain-jvm-lib:1.0",
                "dev.noahtownsend:linkedlicense:$pluginVersion",
            ),
        )

        val srcDir = File(projectDir, "src/main/kotlin")
        srcDir.mkdirs()
        File(srcDir, "Consumer.kt").writeText(
            """
            package com.example.consumer
            import dev.noahtownsend.linkedlicense.generated.main.GeneratedLicenses

            fun printLicenses() {
                println(GeneratedLicenses.all.size)
            }
            """.trimIndent(),
        )

        val result = fixture.run("build")

        assertTrue(result.output.contains("BUILD SUCCESSFUL"), result.output)
        val generated = fixture.generatedLicensesFile().readText()
        assertTrue(generated.contains("elementLicensed = \"plain-jvm-lib\""), generated)
    }

    @Test
    fun `a dependency inheriting license from parent POM is resolved with organization author and project name`() {
        val repo = MavenFixtureRepo(File(projectDir, "repo"))
        // Publish parent POM
        repo.publish(
            group = "com.google.guava",
            artifact = "guava-parent",
            version = "33.0.0",
            licenseName = "Apache-2.0",
            organizationName = "Google LLC",
            packaging = "pom",
        )
        // Publish child library without license but pointing to parent
        repo.publish(
            group = "com.google.guava",
            artifact = "guava",
            version = "33.0.0",
            projectName = "Guava: Google Core Libraries for Java",
            parentGroup = "com.google.guava",
            parentArtifact = "guava-parent",
            parentVersion = "33.0.0",
        )

        val fixture = FixtureProject(projectDir, repo)
        fixture.writeSettings()
        fixture.writeBuildFile(dependencyCoordinates = listOf("com.google.guava:guava:33.0.0"))

        val result = fixture.run("generateLicenseCatalog")

        assertTrue(result.output.contains("BUILD SUCCESSFUL"), result.output)
        val generated = fixture.generatedLicensesFile().readText()
        assertTrue(generated.contains("License.Apache2("), generated)
        assertTrue(generated.contains("elementLicensed = \"Guava: Google Core Libraries for Java\""), generated)
        assertTrue(generated.contains("author = \"Google LLC\""), generated)
    }
}
