package dev.noahtownsend.linkedlicense.plugin

import dev.noahtownsend.linkedlicense.plugin.testsupport.FixtureProject
import dev.noahtownsend.linkedlicense.plugin.testsupport.MavenFixtureRepo
import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * De-risking sanity check for README §2/§2.1: applying the plugin alongside
 * `org.jetbrains.kotlin.multiplatform` must not throw at apply time. Earlier attempts to
 * reference KGP multiplatform extension classes threw NoClassDefFoundError/ClassCastException
 * because the plugin module never declared a `compileOnly` dependency on the Kotlin Gradle
 * Plugin API - see `linkedlicense-plugin/build.gradle.kts`.
 *
 * Uses [FixtureProject.writeSettingsForPublishedPlugin]/[FixtureProject.runPublished], not
 * `withPluginClasspath()` - see [FixtureProject.writeMultiplatformBuildFile]'s doc for why.
 */
class MultiplatformApplyFunctionalTest {
    @TempDir
    lateinit var projectDir: File

    @Test
    fun `applying the plugin alongside kotlin multiplatform does not throw`() {
        val fixture = FixtureProject(projectDir, MavenFixtureRepo(File(projectDir, "repo")))
        fixture.writeSettingsForPublishedPlugin()
        fixture.writeMultiplatformBuildFile(otherTargetDsl = "iosX64()", otherTargetSourceSetName = "iosX64Main")

        val result = fixture.runPublished("tasks")

        assertTrue(result.output.contains("BUILD SUCCESSFUL"), result.output)
        assertTrue(result.output.contains("generateJvmMainLicenseCatalog"), result.output)
        assertTrue(result.output.contains("generateCommonMainLicenseCatalog"), result.output)
    }
}
