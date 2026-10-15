package dev.noahtownsend.linkedlicense.plugin

import dev.noahtownsend.linkedlicense.plugin.testsupport.FixtureProject
import dev.noahtownsend.linkedlicense.plugin.testsupport.MavenFixtureRepo
import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MultiplatformAndroidFunctionalTest {
    @TempDir
    lateinit var projectDir: File

    @Test
    fun `kmp with androidTarget registers generateAndroidMainLicenseCatalog and commonMain includes android dependencies`() {
        val repo = MavenFixtureRepo(File(projectDir, "repo"))
        repo.publish(group = "com.example", artifact = "jvm-lib", version = "1.0", licenseName = "MIT")
        repo.publish(group = "com.example", artifact = "android-lib", version = "1.0", licenseName = "Apache-2.0")

        val fixture = FixtureProject(projectDir, repo)
        fixture.writeSettingsForPublishedPlugin()
        fixture.writeLocalProperties()
        fixture.writeMultiplatformAndroidBuildFile(
            jvmDependencyCoordinates = listOf("com.example:jvm-lib:1.0"),
            androidDependencyCoordinates = listOf("com.example:android-lib:1.0"),
        )

        val result =
            fixture.runPublished(
                "generateAndroidMainLicenseCatalog",
                "generateCommonMainLicenseCatalog",
            )

        assertTrue(result.output.contains("BUILD SUCCESSFUL"), result.output)

        val androidGenerated = fixture.generatedLicensesFile("androidMain").readText()
        assertTrue(androidGenerated.contains("elementLicensed = \"android-lib\""), androidGenerated)
        assertFalse(androidGenerated.contains("elementLicensed = \"jvm-lib\""), androidGenerated)

        val commonGenerated = fixture.generatedLicensesFile("commonMain").readText()
        assertTrue(commonGenerated.contains("elementLicensed = \"android-lib\""), commonGenerated)
        assertTrue(commonGenerated.contains("elementLicensed = \"jvm-lib\""), commonGenerated)
    }

    @Test
    fun `catalog tasks are compatible with gradle configuration cache and store and reuse cleanly`() {
        val repo = MavenFixtureRepo(File(projectDir, "repo"))
        repo.publish(group = "com.example", artifact = "jvm-lib", version = "1.0", licenseName = "MIT")

        val fixture = FixtureProject(projectDir, repo)
        fixture.writeSettingsForPublishedPlugin()
        fixture.writeOverridesToml("")
        fixture.writeMultiplatformBuildFile(
            jvmDependencyCoordinates = listOf("com.example:jvm-lib:1.0"),
        )

        // Run 1: Store configuration cache
        val storeResult = fixture.runPublished("generateCommonMainLicenseCatalog", "--configuration-cache")
        assertTrue(storeResult.output.contains("BUILD SUCCESSFUL"), storeResult.output)
        assertTrue(
            storeResult.output.contains("Configuration cache entry stored") ||
                storeResult.output.contains("Calculating task graph"),
            storeResult.output,
        )

        // Run 2: Reuse configuration cache
        val reuseResult = fixture.runPublished("generateCommonMainLicenseCatalog", "--configuration-cache")
        assertTrue(reuseResult.output.contains("BUILD SUCCESSFUL"), reuseResult.output)
        assertTrue(
            reuseResult.output.contains("Configuration cache entry reused") ||
                reuseResult.output.contains("Reusing configuration cache"),
            reuseResult.output,
        )
    }
}
