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

    private fun publishLgplLib(repo: MavenFixtureRepo) {
        repo.publish(group = "com.example", artifact = "lgpl-lib", version = "1.0", licenseName = "LGPL-2.1")
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

    @Test
    fun `a weak-copyleft LGPL dependency fails the build by default`() {
        val repo = MavenFixtureRepo(File(projectDir, "repo"))
        publishLgplLib(repo)

        val fixture = FixtureProject(projectDir, repo)
        fixture.writeSettings()
        fixture.writeBuildFile(dependencyCoordinates = listOf("com.example:lgpl-lib:1.0"))

        val result = fixture.runAndFail("generateLicenseCatalog")

        assertTrue(result.output.contains("com.example:lgpl-lib:1.0"), result.output)
    }

    @Test
    fun `failOnSoftCopyleft = false lets a weak-copyleft LGPL dependency through while strong GPL still fails`() {
        val repo = MavenFixtureRepo(File(projectDir, "repo"))
        publishGplLib(repo)
        publishLgplLib(repo)

        val fixture = FixtureProject(projectDir, repo)
        fixture.writeSettings()
        fixture.writeBuildFile(
            dependencyCoordinates = listOf("com.example:gpl-lib:1.0", "com.example:lgpl-lib:1.0"),
            linkedLicenseBlock = "linkedLicense { failOnCopyleft = true; failOnSoftCopyleft = false }",
        )

        val result = fixture.runAndFail("generateLicenseCatalog")

        assertTrue(result.output.contains("com.example:gpl-lib:1.0"), result.output)
        assertTrue(!result.output.contains("com.example:lgpl-lib:1.0"), result.output)
    }

    @Test
    fun `failOnSoftCopyleft = true fails a weak-copyleft LGPL dependency even when failOnCopyleft is false`() {
        val repo = MavenFixtureRepo(File(projectDir, "repo"))
        publishLgplLib(repo)

        val fixture = FixtureProject(projectDir, repo)
        fixture.writeSettings()
        fixture.writeBuildFile(
            dependencyCoordinates = listOf("com.example:lgpl-lib:1.0"),
            linkedLicenseBlock = "linkedLicense { failOnCopyleft = false; failOnSoftCopyleft = true }",
        )

        val result = fixture.runAndFail("generateLicenseCatalog")

        assertTrue(result.output.contains("com.example:lgpl-lib:1.0"), result.output)
    }

    @Test
    fun `a copyleft-allowed entry lets a weak-copyleft LGPL dependency through regardless of the boolean settings`() {
        val repo = MavenFixtureRepo(File(projectDir, "repo"))
        publishLgplLib(repo)

        val fixture = FixtureProject(projectDir, repo)
        fixture.writeSettings()
        fixture.writeBuildFile(
            dependencyCoordinates = listOf("com.example:lgpl-lib:1.0"),
            linkedLicenseBlock = "linkedLicense { failOnCopyleft = true; failOnSoftCopyleft = true }",
        )
        fixture.writeOverridesToml(
            """
            [copyleft-allowed]
            "com.example:lgpl-lib" = "Used only at build time."
            """.trimIndent(),
        )

        val result = fixture.run("generateLicenseCatalog")

        assertTrue(result.output.contains("BUILD SUCCESSFUL"), result.output)
        assertTrue(fixture.generatedLicensesFile().readText().contains("License.Lgpl2_1"))
    }

    @Test
    fun `a copyleft-allowed entry still lets a strong-copyleft GPL dependency through`() {
        val repo = MavenFixtureRepo(File(projectDir, "repo"))
        publishGplLib(repo)

        val fixture = FixtureProject(projectDir, repo)
        fixture.writeSettings()
        fixture.writeBuildFile(
            dependencyCoordinates = listOf("com.example:gpl-lib:1.0"),
            linkedLicenseBlock = "linkedLicense { failOnCopyleft = true; failOnSoftCopyleft = true }",
        )
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
}
