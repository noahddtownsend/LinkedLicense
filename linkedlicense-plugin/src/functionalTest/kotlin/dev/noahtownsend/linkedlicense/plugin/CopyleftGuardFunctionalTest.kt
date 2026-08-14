package dev.noahtownsend.linkedlicense.plugin

import dev.noahtownsend.linkedlicense.plugin.testsupport.FixtureProject
import dev.noahtownsend.linkedlicense.plugin.testsupport.MavenFixtureRepo
import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

class CopyleftGuardFunctionalTest {
    @TempDir
    lateinit var projectDir: File

    private fun publishGplLib(repo: MavenFixtureRepo) {
        repo.publish(group = "com.example", artifact = "gpl-lib", version = "1.0", licenseName = "GPL-3.0")
    }

    @Test
    fun `a GPL-licensed dependency fails the build by default`() {
        val repo = MavenFixtureRepo(File(projectDir, "repo"))
        publishGplLib(repo)

        val fixture = FixtureProject(projectDir, repo)
        fixture.writeSettings()
        fixture.writeBuildFile(dependencyCoordinates = listOf("com.example:gpl-lib:1.0"))

        val result = fixture.runAndFail("generateLicenseCatalog")

        assertTrue(result.output.contains("com.example:gpl-lib:1.0"), result.output)
    }

    @Test
    fun `a copyleft-allowed entry lets a GPL dependency through`() {
        val repo = MavenFixtureRepo(File(projectDir, "repo"))
        publishGplLib(repo)

        val fixture = FixtureProject(projectDir, repo)
        fixture.writeSettings()
        fixture.writeBuildFile(dependencyCoordinates = listOf("com.example:gpl-lib:1.0"))
        fixture.writeOverridesToml(
            """
            [copyleft-allowed]
            "com.example:gpl-lib" = "Used only at build time."
            """.trimIndent(),
        )

        val result = fixture.run("generateLicenseCatalog")

        assertTrue(result.output.contains("BUILD SUCCESSFUL"), result.output)
        assertTrue(fixture.generatedLicensesFile().readText().contains("License.Gpl3"))
    }

    @Test
    fun `failOnCopyleft = false lets a GPL dependency through project-wide`() {
        val repo = MavenFixtureRepo(File(projectDir, "repo"))
        publishGplLib(repo)

        val fixture = FixtureProject(projectDir, repo)
        fixture.writeSettings()
        fixture.writeBuildFile(
            dependencyCoordinates = listOf("com.example:gpl-lib:1.0"),
            linkedLicenseBlock = "linkedLicense { failOnCopyleft = false }",
        )

        val result = fixture.run("generateLicenseCatalog")

        assertTrue(result.output.contains("BUILD SUCCESSFUL"), result.output)
        assertTrue(fixture.generatedLicensesFile().readText().contains("License.Gpl3"))
    }
}
