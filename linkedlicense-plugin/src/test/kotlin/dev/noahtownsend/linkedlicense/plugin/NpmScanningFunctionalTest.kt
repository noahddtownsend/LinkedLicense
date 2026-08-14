package dev.noahtownsend.linkedlicense.plugin

import org.gradle.api.GradleException
import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * End-to-end coverage for npm scanning (README §2.3) through the *real*
 * [CatalogTaskExecution.generateCatalog] entry point - JSON parsing, `node_modules` graph
 * walking, SPDX matching, override lookup, fail-on-unknown, and `GeneratedLicenses.kt` codegen
 * all exercised together, the same call path `generate<SourceSet>LicenseCatalog`'s task action
 * uses in a real build.
 *
 * This uses a hand-built fake `node_modules` tree (README §2.3 explicitly allows this over a
 * real `npm install`) and a [ProjectBuilder]-created [org.gradle.api.Project] with an empty
 * detached configuration standing in for the Maven side of the classpath, rather than a real
 * `GradleRunner` build applying `kotlin("multiplatform") { js() }` - doing that for real would
 * require downloading the Node.js/Yarn toolchain over the network at task-execution time, which
 * this sandboxed environment doesn't have, and isn't actually necessary to prove out the npm
 * resolution mechanism itself (KGP's own `jsMain`/`wasmJsMain` task-graph wiring is exercised
 * separately, at the unit level, by [NpmNodeModulesLocator] and [MultiplatformCatalogTasks]
 * calling [CatalogTaskExecution.generateCatalog] with the located directory).
 */
class NpmScanningFunctionalTest {
    @TempDir
    lateinit var tempDir: File

    private fun writePackage(
        dir: File,
        name: String,
        version: String,
        license: String,
    ) {
        dir.mkdirs()
        File(dir, "package.json").writeText("""{"name": "$name", "version": "$version", "license": "$license"}""")
    }

    @Test
    fun `generateCatalog() resolves matches and codegens an npm dependency end-to-end`() {
        val nodeModules = File(tempDir, "node_modules")
        writePackage(nodeModules.resolve("left-pad"), "left-pad", "1.3.0", "MIT")

        val project = ProjectBuilder.builder().withProjectDir(tempDir).build()
        val extension = LinkedLicenseExtension(project)
        extension.overridesFile = File(tempDir, "linkedlicense.toml")
        val outputDir = File(tempDir, "out")
        val emptyConfiguration = project.configurations.detachedConfiguration()

        CatalogTaskExecution.generateCatalog(
            project = project,
            extension = extension,
            configuration = emptyConfiguration,
            outputDir = outputDir,
            includeAssets = false,
            npmNodeModulesDir = nodeModules,
        )

        val generated =
            File(outputDir, "dev/noahtownsend/linkedlicense/generated/GeneratedLicenses.kt").readText()

        assertTrue(generated.contains("License.MIT("), generated)
        assertTrue(generated.contains("elementLicensed = \"left-pad\""), generated)
    }

    @Test
    fun `generateCatalog() walks transitive and scoped npm dependencies`() {
        val nodeModules = File(tempDir, "node_modules")
        writePackage(nodeModules.resolve("chalk"), "chalk", "4.1.2", "MIT")
        writePackage(nodeModules.resolve("chalk").resolve("node_modules").resolve("ansi-styles"), "ansi-styles", "4.3.0", "MIT")
        writePackage(nodeModules.resolve("@scope").resolve("pkg"), "@scope/pkg", "2.0.0", "ISC")

        val project = ProjectBuilder.builder().withProjectDir(tempDir).build()
        val extension = LinkedLicenseExtension(project)
        extension.overridesFile = File(tempDir, "linkedlicense.toml")
        val outputDir = File(tempDir, "out")

        CatalogTaskExecution.generateCatalog(
            project = project,
            extension = extension,
            configuration = project.configurations.detachedConfiguration(),
            outputDir = outputDir,
            includeAssets = false,
            npmNodeModulesDir = nodeModules,
        )

        val generated =
            File(outputDir, "dev/noahtownsend/linkedlicense/generated/GeneratedLicenses.kt").readText()

        assertTrue(generated.contains("elementLicensed = \"chalk\""), generated)
        assertTrue(generated.contains("elementLicensed = \"ansi-styles\""), generated)
        assertTrue(generated.contains("elementLicensed = \"@scope/pkg\""), generated)
    }

    @Test
    fun `generateCatalog() fails the build on an unrecognized npm license string`() {
        val nodeModules = File(tempDir, "node_modules")
        writePackage(nodeModules.resolve("weird-pkg"), "weird-pkg", "1.0.0", "Some Bespoke License")

        val project = ProjectBuilder.builder().withProjectDir(tempDir).build()
        val extension = LinkedLicenseExtension(project)
        extension.overridesFile = File(tempDir, "linkedlicense.toml")
        val outputDir = File(tempDir, "out")

        val exception =
            assertFailsWith<GradleException> {
                CatalogTaskExecution.generateCatalog(
                    project = project,
                    extension = extension,
                    configuration = project.configurations.detachedConfiguration(),
                    outputDir = outputDir,
                    includeAssets = false,
                    npmNodeModulesDir = nodeModules,
                )
            }

        assertTrue(exception.message?.contains("npm:weird-pkg") == true, exception.message.orEmpty())
    }

    @Test
    fun `generateCatalog() resolves an npm dependency via an npm-prefixed overrides entry`() {
        val nodeModules = File(tempDir, "node_modules")
        writePackage(nodeModules.resolve("weird-pkg"), "weird-pkg", "1.0.0", "Some Bespoke License")

        val project = ProjectBuilder.builder().withProjectDir(tempDir).build()
        val extension = LinkedLicenseExtension(project)
        extension.overridesFile = File(tempDir, "linkedlicense.toml")
        extension.overridesFile.writeText(
            """
            [overrides]
            "npm:weird-pkg" = { license = "MIT" }
            """.trimIndent(),
        )
        val outputDir = File(tempDir, "out")

        CatalogTaskExecution.generateCatalog(
            project = project,
            extension = extension,
            configuration = project.configurations.detachedConfiguration(),
            outputDir = outputDir,
            includeAssets = false,
            npmNodeModulesDir = nodeModules,
        )

        val generated =
            File(outputDir, "dev/noahtownsend/linkedlicense/generated/GeneratedLicenses.kt").readText()

        assertTrue(generated.contains("License.MIT("), generated)
    }

    @Test
    fun `generateCatalog() is a no-op for npm when the node_modules directory does not exist`() {
        val project = ProjectBuilder.builder().withProjectDir(tempDir).build()
        val extension = LinkedLicenseExtension(project)
        extension.overridesFile = File(tempDir, "linkedlicense.toml")
        val outputDir = File(tempDir, "out")

        CatalogTaskExecution.generateCatalog(
            project = project,
            extension = extension,
            configuration = project.configurations.detachedConfiguration(),
            outputDir = outputDir,
            includeAssets = false,
            npmNodeModulesDir = File(tempDir, "does-not-exist"),
        )

        val generated =
            File(outputDir, "dev/noahtownsend/linkedlicense/generated/GeneratedLicenses.kt").readText()

        assertFalse(generated.contains("License."), generated)
    }
}
