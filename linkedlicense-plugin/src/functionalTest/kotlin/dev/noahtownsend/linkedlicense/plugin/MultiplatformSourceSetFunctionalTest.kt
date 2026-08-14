package dev.noahtownsend.linkedlicense.plugin

import dev.noahtownsend.linkedlicense.plugin.testsupport.FixtureProject
import dev.noahtownsend.linkedlicense.plugin.testsupport.MavenFixtureRepo
import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * README §2/§2.1: a Kotlin Multiplatform project gets one `generate<SourceSet>LicenseCatalog`
 * task per platform target's main compilation, plus `generateCommonMainLicenseCatalog` as the
 * union of every platform target's resolved coordinates.
 */
class MultiplatformSourceSetFunctionalTest {
    @TempDir
    lateinit var projectDir: File

    @Test
    fun `jvmMain catalog only contains the jvm-only dependency, commonMain contains the union of both targets`() {
        val repo = MavenFixtureRepo(File(projectDir, "repo"))
        repo.publish(group = "com.example", artifact = "jvm-only-lib", version = "1.0", licenseName = "MIT")
        repo.publish(group = "com.example", artifact = "ios-only-lib", version = "1.0", licenseName = "Apache-2.0")

        val fixture = FixtureProject(projectDir, repo)
        fixture.writeSettingsForPublishedPlugin()
        fixture.writeMultiplatformBuildFile(
            otherTargetDsl = "iosX64()",
            otherTargetSourceSetName = "iosX64Main",
            jvmDependencyCoordinates = listOf("com.example:jvm-only-lib:1.0"),
            otherTargetDependencyCoordinates = listOf("com.example:ios-only-lib:1.0"),
        )

        val result =
            fixture.runPublished(
                "generateJvmMainLicenseCatalog",
                "generateIosX64MainLicenseCatalog",
                "generateCommonMainLicenseCatalog",
            )

        assertTrue(result.output.contains("BUILD SUCCESSFUL"), result.output)

        val jvmGenerated = fixture.generatedLicensesFile("jvmMain").readText()
        assertTrue(jvmGenerated.contains("elementLicensed = \"jvm-only-lib\""), jvmGenerated)
        assertFalse(jvmGenerated.contains("elementLicensed = \"ios-only-lib\""), jvmGenerated)

        val iosGenerated = fixture.generatedLicensesFile("iosX64Main").readText()
        assertTrue(iosGenerated.contains("elementLicensed = \"ios-only-lib\""), iosGenerated)
        assertFalse(iosGenerated.contains("elementLicensed = \"jvm-only-lib\""), iosGenerated)

        val commonGenerated = fixture.generatedLicensesFile("commonMain").readText()
        assertTrue(commonGenerated.contains("elementLicensed = \"jvm-only-lib\""), commonGenerated)
        assertTrue(commonGenerated.contains("elementLicensed = \"ios-only-lib\""), commonGenerated)
    }

    @Test
    fun `a dependency declared in commonMain is resolved on every target and appears once in the commonMain union`() {
        val repo = MavenFixtureRepo(File(projectDir, "repo"))
        repo.publish(group = "com.example", artifact = "shared-lib", version = "1.0", licenseName = "MIT")

        val fixture = FixtureProject(projectDir, repo)
        fixture.writeSettingsForPublishedPlugin()
        fixture.writeMultiplatformBuildFile(
            otherTargetDsl = "iosX64()",
            otherTargetSourceSetName = "iosX64Main",
            commonDependencyCoordinates = listOf("com.example:shared-lib:1.0"),
        )

        val result = fixture.runPublished("generateCommonMainLicenseCatalog")

        assertTrue(result.output.contains("BUILD SUCCESSFUL"), result.output)

        val commonGenerated = fixture.generatedLicensesFile("commonMain").readText()
        val occurrences = Regex("elementLicensed = \"shared-lib\"").findAll(commonGenerated).count()
        assertTrue(occurrences == 1, commonGenerated)
    }
}
