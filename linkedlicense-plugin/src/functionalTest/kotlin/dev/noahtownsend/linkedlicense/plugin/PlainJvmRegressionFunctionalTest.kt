package dev.noahtownsend.linkedlicense.plugin

import dev.noahtownsend.linkedlicense.plugin.testsupport.FixtureProject
import dev.noahtownsend.linkedlicense.plugin.testsupport.MavenFixtureRepo
import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Regression check for the plain `org.jetbrains.kotlin.jvm` path (a single `main` source set,
 * `generateLicenseCatalog`): adding Kotlin Multiplatform support must not change its behavior.
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
}
