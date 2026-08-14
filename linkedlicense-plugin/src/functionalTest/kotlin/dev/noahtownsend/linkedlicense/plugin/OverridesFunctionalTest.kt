package dev.noahtownsend.linkedlicense.plugin

import dev.noahtownsend.linkedlicense.plugin.testsupport.FixtureProject
import dev.noahtownsend.linkedlicense.plugin.testsupport.MavenFixtureRepo
import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

class OverridesFunctionalTest {
    @TempDir
    lateinit var projectDir: File

    @Test
    fun `an overrides entry resolves an otherwise-unmatched coordinate instead of failing the build`() {
        val repo = MavenFixtureRepo(File(projectDir, "repo"))
        repo.publish(group = "com.example", artifact = "unknown-lib", version = "1.0", licenseName = null)

        val fixture = FixtureProject(projectDir, repo)
        fixture.writeSettings()
        fixture.writeBuildFile(dependencyCoordinates = listOf("com.example:unknown-lib:1.0"))
        fixture.writeOverridesToml(
            """
            [overrides]
            "com.example:unknown-lib" = { license = "Apache2" }
            """.trimIndent(),
        )

        val result = fixture.run("generateLicenseCatalog")

        assertTrue(result.output.contains("BUILD SUCCESSFUL"), result.output)
        val generated = fixture.generatedLicensesFile().readText()
        assertTrue(generated.contains("License.Apache2"), generated)
    }

    @Test
    fun `a libs-dot-alias override key resolves to the same coordinate as the raw group-artifact form`() {
        val repo = MavenFixtureRepo(File(projectDir, "repo"))
        repo.publish(group = "com.example", artifact = "alias-lib", version = "1.0", licenseName = null)

        val fixture = FixtureProject(projectDir, repo)
        fixture.writeSettings(catalogAliases = mapOf("aliasLib" to "com.example:alias-lib"))
        fixture.writeBuildFile(dependencyCoordinates = listOf("com.example:alias-lib:1.0"))
        fixture.writeOverridesToml(
            """
            [overrides]
            "libs.aliasLib" = { license = "MIT" }
            """.trimIndent(),
        )

        val result = fixture.run("generateLicenseCatalog")

        assertTrue(result.output.contains("BUILD SUCCESSFUL"), result.output)
        val generated = fixture.generatedLicensesFile().readText()
        assertTrue(generated.contains("License.MIT"), generated)
    }

    @Test
    fun `a custom colon override emits a reference to the given symbol rather than a built-in License instance`() {
        val repo = MavenFixtureRepo(File(projectDir, "repo"))
        repo.publish(group = "com.example", artifact = "custom-lib", version = "1.0", licenseName = null)

        val fixture = FixtureProject(projectDir, repo)
        fixture.writeSettings()
        fixture.writeBuildFile(dependencyCoordinates = listOf("com.example:custom-lib:1.0"))
        fixture.writeOverridesToml(
            """
            [overrides]
            "com.example:custom-lib" = { license = "custom:com.acme.licenses.MyCompanyLicense" }
            """.trimIndent(),
        )

        val result = fixture.run("generateLicenseCatalog")

        assertTrue(result.output.contains("BUILD SUCCESSFUL"), result.output)
        val generated = fixture.generatedLicensesFile().readText()
        assertTrue(generated.contains("import com.acme.licenses.MyCompanyLicense"), generated)
        assertTrue(generated.contains("MyCompanyLicense"), generated)
        assertTrue(!generated.contains("License.Custom"), generated)
    }
}
