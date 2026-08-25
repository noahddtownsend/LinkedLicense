package dev.noahtownsend.linkedlicense.plugin

import dev.noahtownsend.linkedlicense.plugin.testsupport.FixtureProject
import dev.noahtownsend.linkedlicense.plugin.testsupport.MavenFixtureRepo
import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Reproduces the bug fixed alongside this test (README §2.1 step 7): before the fix, every
 * source set's `GeneratedLicenses.kt` was emitted into the same hardcoded package
 * (`dev.noahtownsend.linkedlicense.generated`) with the same object name (`GeneratedLicenses`).
 * Running `generateJvmMainLicenseCatalog`/`generateCommonMainLicenseCatalog` independently
 * always succeeded either way - the redeclaration only surfaces once Kotlin actually *compiles*
 * `jvmMain`, because KGP's default hierarchy template merges `commonMain`'s sources into every
 * platform target's own compilation. [MultiplatformSourceSetFunctionalTest] (which only runs
 * the `generate*` tasks, never a real compile) is exactly the kind of test that was already
 * "passing" while this bug shipped - see that class's doc.
 *
 * This fixture therefore does what those don't: it runs the real `compileKotlinJvm` task
 * (which depends on, and so also exercises, both `generate*LicenseCatalog` tasks via their
 * `kotlin.srcDir(...)` wiring), against a `commonMain` + `jvm()`-only project - deliberately no
 * second platform target, since a real (non-`jvm`) target would need to download the
 * Kotlin/Native toolchain just to compile. A real dependency on `dev.noahtownsend:linkedlicense`
 * itself (resolved from the same local repo [FixtureProject.writeMultiplatformBuildFile] already
 * knows how to reach) is required for the generated file's `License.MIT(...)` etc. references to
 * resolve at compile time - unlike [MultiplatformSourceSetFunctionalTest], which only inspects
 * generated source text and never needs the real library on the fixture's classpath.
 */
class MultiplatformCompilationFunctionalTest {
    @TempDir
    lateinit var projectDir: File

    @Test
    fun `commonMain and jvmMain generated catalogs compile together without a redeclaration error`() {
        val repo = MavenFixtureRepo(File(projectDir, "repo"))
        repo.publish(group = "com.example", artifact = "common-lib", version = "1.0", licenseName = "MIT")
        repo.publish(group = "com.example", artifact = "jvm-lib", version = "1.0", licenseName = "Apache-2.0")

        val pluginVersion = System.getProperty("linkedlicense.version") ?: "0.1.0"

        val fixture = FixtureProject(projectDir, repo)
        fixture.writeSettingsForPublishedPlugin()
        fixture.writeMultiplatformBuildFile(
            commonDependencyCoordinates =
                listOf(
                    "com.example:common-lib:1.0",
                    // Coordinates: root project's Gradle Module Metadata "available-at" pointer
                    // resolves the real `jvm` variant for this fixture's jvm() target - see
                    // linkedlicense-plugin/build.gradle.kts's comment on functionalTestRepo.
                    "dev.noahtownsend:linkedlicense:$pluginVersion",
                ),
            jvmDependencyCoordinates = listOf("com.example:jvm-lib:1.0"),
        )

        val result = fixture.runPublished("compileKotlinJvm")

        assertTrue(result.output.contains("BUILD SUCCESSFUL"), result.output)

        val commonGenerated = fixture.generatedLicensesFile("commonMain").readText()
        val jvmGenerated = fixture.generatedLicensesFile("jvmMain").readText()

        assertTrue(commonGenerated.contains("package dev.noahtownsend.linkedlicense.generated.commonmain"), commonGenerated)
        assertTrue(jvmGenerated.contains("package dev.noahtownsend.linkedlicense.generated.jvmmain"), jvmGenerated)
    }
}
