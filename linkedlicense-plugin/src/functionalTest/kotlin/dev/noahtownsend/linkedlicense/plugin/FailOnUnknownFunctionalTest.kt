package dev.noahtownsend.linkedlicense.plugin

import dev.noahtownsend.linkedlicense.plugin.testsupport.FixtureProject
import dev.noahtownsend.linkedlicense.plugin.testsupport.MavenFixtureRepo
import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FailOnUnknownFunctionalTest {
    @TempDir
    lateinit var projectDir: File

    @Test
    fun `generateLicenseCatalog fails and lists an unmatched, non-overridden coordinate`() {
        val repo = MavenFixtureRepo(File(projectDir, "repo"))
        repo.publish(group = "com.example", artifact = "unknown-lib", version = "1.0", licenseName = null)

        val fixture = FixtureProject(projectDir, repo)
        fixture.writeSettings()
        fixture.writeBuildFile(dependencyCoordinates = listOf("com.example:unknown-lib:1.0"))

        val result = fixture.runAndFail("generateLicenseCatalog")

        assertTrue(result.output.contains("com.example:unknown-lib:1.0"), result.output)
    }

    @Test
    fun `generateLicenseCatalog omits an unmatched coordinate instead of failing when failOnUnknown is false`() {
        val repo = MavenFixtureRepo(File(projectDir, "repo"))
        repo.publish(group = "com.example", artifact = "unknown-lib", version = "1.0", licenseName = null)

        val fixture = FixtureProject(projectDir, repo)
        fixture.writeSettings()
        fixture.writeBuildFile(
            dependencyCoordinates = listOf("com.example:unknown-lib:1.0"),
            linkedLicenseBlock = "linkedLicense { failOnUnknown = false }",
        )

        val result = fixture.run("generateLicenseCatalog")

        assertTrue(result.output.contains("BUILD SUCCESSFUL"), result.output)
        val generated = fixture.generatedLicensesFile().readText()
        assertFalse(generated.contains("unknown-lib"))
    }
}
