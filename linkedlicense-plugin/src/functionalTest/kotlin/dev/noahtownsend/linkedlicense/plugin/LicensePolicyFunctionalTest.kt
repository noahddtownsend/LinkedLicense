package dev.noahtownsend.linkedlicense.plugin

import dev.noahtownsend.linkedlicense.plugin.testsupport.FixtureProject
import dev.noahtownsend.linkedlicense.plugin.testsupport.MavenFixtureRepo
import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

class LicensePolicyFunctionalTest {
    @TempDir
    lateinit var projectDir: File

    @Test
    fun `an allow-list rejects a license type not on it and accepts one that is`() {
        val repo = MavenFixtureRepo(File(projectDir, "repo"))
        repo.publish(group = "com.example", artifact = "mit-lib", version = "1.0", licenseName = "MIT")
        repo.publish(group = "com.example", artifact = "apache-lib", version = "1.0", licenseName = "Apache-2.0")

        val fixture = FixtureProject(projectDir, repo)
        fixture.writeSettings()
        fixture.writeBuildFile(dependencyCoordinates = listOf("com.example:mit-lib:1.0"))
        fixture.writeOverridesToml(
            """
            [license-policy]
            allow = ["Apache2"]
            """.trimIndent(),
        )

        val failing = fixture.runAndFail("generateLicenseCatalog")
        assertTrue(failing.output.contains("com.example:mit-lib:1.0"), failing.output)

        fixture.writeBuildFile(dependencyCoordinates = listOf("com.example:apache-lib:1.0"))
        val passing = fixture.run("generateLicenseCatalog")
        assertTrue(passing.output.contains("BUILD SUCCESSFUL"), passing.output)
    }

    @Test
    fun `a block-list-only policy rejects the blocked type and accepts everything else`() {
        val repo = MavenFixtureRepo(File(projectDir, "repo"))
        repo.publish(group = "com.example", artifact = "mit-lib", version = "1.0", licenseName = "MIT")

        val fixture = FixtureProject(projectDir, repo)
        fixture.writeSettings()
        fixture.writeBuildFile(dependencyCoordinates = listOf("com.example:mit-lib:1.0"))
        fixture.writeOverridesToml(
            """
            [license-policy]
            block = ["MIT"]
            """.trimIndent(),
        )

        val failing = fixture.runAndFail("generateLicenseCatalog")
        assertTrue(failing.output.contains("com.example:mit-lib:1.0"), failing.output)

        fixture.writeOverridesToml(
            """
            [license-policy]
            block = ["Gpl3"]
            """.trimIndent(),
        )
        val passing = fixture.run("generateLicenseCatalog")
        assertTrue(passing.output.contains("BUILD SUCCESSFUL"), passing.output)
    }

    @Test
    fun `allow wins over block when both are set`() {
        val repo = MavenFixtureRepo(File(projectDir, "repo"))
        repo.publish(group = "com.example", artifact = "mit-lib", version = "1.0", licenseName = "MIT")

        val fixture = FixtureProject(projectDir, repo)
        fixture.writeSettings()
        fixture.writeBuildFile(dependencyCoordinates = listOf("com.example:mit-lib:1.0"))
        fixture.writeOverridesToml(
            // kotlin-stdlib/annotations (Apache2) are unavoidable transitive deps of any
            // Kotlin/JVM project, so they must be allow-listed too alongside MIT.
            """
            [license-policy]
            allow = ["MIT", "Apache2"]
            block = ["MIT"]
            """.trimIndent(),
        )

        val result = fixture.run("generateLicenseCatalog")

        assertTrue(result.output.contains("BUILD SUCCESSFUL"), result.output)
    }
}
