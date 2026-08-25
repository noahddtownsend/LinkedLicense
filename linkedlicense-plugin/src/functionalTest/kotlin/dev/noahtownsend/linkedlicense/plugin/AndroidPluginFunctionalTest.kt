package dev.noahtownsend.linkedlicense.plugin

import dev.noahtownsend.linkedlicense.plugin.testsupport.FixtureProject
import dev.noahtownsend.linkedlicense.plugin.testsupport.MavenFixtureRepo
import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AndroidPluginFunctionalTest {
    @TempDir
    lateinit var projectDir: File

    @Test
    fun `android application with product flavors registers per-variant catalog tasks and resolves flavor classpath`() {
        val repo = MavenFixtureRepo(File(projectDir, "repo"))
        repo.publish(group = "com.example", artifact = "android-lib", version = "1.0", licenseName = "MIT")

        val fixture = FixtureProject(projectDir, repo)
        fixture.writeSettings()
        fixture.writeLocalProperties()
        fixture.writeAndroidBuildFile(
            dependencyCoordinates = listOf("com.example:android-lib:1.0"),
            productFlavors = listOf("dev", "beta"),
        )

        val result = fixture.run("generateDevDebugLicenseCatalog")

        assertTrue(result.output.contains("BUILD SUCCESSFUL"), result.output)
        val generated = fixture.generatedLicensesFile("devDebug").readText()
        assertTrue(generated.contains("package dev.noahtownsend.linkedlicense.generated.devdebug"), generated)
        assertTrue(generated.contains("elementLicensed = \"android-lib\""), generated)
        assertTrue(generated.contains("License.MIT"), generated)
    }

    @Test
    fun `android aggregate generateLicenseCatalog task executes all variant tasks`() {
        val repo = MavenFixtureRepo(File(projectDir, "repo"))
        repo.publish(group = "com.example", artifact = "android-lib", version = "1.0", licenseName = "MIT")

        val fixture = FixtureProject(projectDir, repo)
        fixture.writeSettings()
        fixture.writeLocalProperties()
        fixture.writeAndroidBuildFile(
            dependencyCoordinates = listOf("com.example:android-lib:1.0"),
            productFlavors = listOf("dev", "beta"),
        )

        val result = fixture.run("generateLicenseCatalog")

        assertTrue(result.output.contains("BUILD SUCCESSFUL"), result.output)
        assertTrue(fixture.generatedLicensesFile("devDebug").exists())
        assertTrue(fixture.generatedLicensesFile("betaRelease").exists())
    }

    @Test
    fun `android library with missing license POM respects failOnUnknown and overrides or ignored tables`() {
        val repo = MavenFixtureRepo(File(projectDir, "repo"))
        repo.publish(group = "com.example", artifact = "unlicensed-internal", version = "1.0", licenseName = null)

        val fixture = FixtureProject(projectDir, repo)
        fixture.writeSettings()
        fixture.writeLocalProperties()
        fixture.writeAndroidBuildFile(
            dependencyCoordinates = listOf("com.example:unlicensed-internal:1.0"),
            isLibrary = true,
        )

        // 1. By default failOnUnknown = true -> should fail
        val failedResult = fixture.runAndFail("generateReleaseLicenseCatalog")
        assertTrue(failedResult.output.contains("com.example:unlicensed-internal:1.0"), failedResult.output)

        // 2. Add override in linkedlicense.toml -> should succeed
        fixture.writeOverridesToml(
            """
            [overrides]
            "com.example:unlicensed-internal" = { license = "Apache2", reason = "Internal private package" }
            """.trimIndent(),
        )

        val overrideResult = fixture.run("generateReleaseLicenseCatalog", "--rerun-tasks")
        assertTrue(overrideResult.output.contains("BUILD SUCCESSFUL"), overrideResult.output)
        val generated = fixture.generatedLicensesFile("release").readText()
        assertTrue(generated.contains("License.Apache2"), generated)

        // 3. Alternatively with [ignored]
        fixture.writeOverridesToml(
            """
            [ignored]
            "com.example:unlicensed-internal" = "Internal proprietary code"
            """.trimIndent(),
        )

        val ignoredResult = fixture.run("generateReleaseLicenseCatalog", "--rerun-tasks")
        assertTrue(ignoredResult.output.contains("BUILD SUCCESSFUL"), ignoredResult.output)
        val generatedIgnored = fixture.generatedLicensesFile("release").readText()
        assertFalse(generatedIgnored.contains("unlicensed-internal"), "Expected not to contain unlicensed-internal but was:\n$generatedIgnored")
    }

    @Test
    fun `applying plugin to project without jvm, multiplatform, or android kotlin plugins fails loudly`() {
        val repo = MavenFixtureRepo(File(projectDir, "repo"))
        val fixture = FixtureProject(projectDir, repo)
        fixture.writeSettings()
        fixture.writeUnrecognizedPluginBuildFile()

        val result = fixture.runAndFail("tasks")

        assertTrue(
            result.output.contains("LinkedLicensePlugin requires one of the following Kotlin plugins to be applied: 'org.jetbrains.kotlin.jvm', 'org.jetbrains.kotlin.multiplatform', or 'org.jetbrains.kotlin.android'."),
            result.output,
        )
    }
}
