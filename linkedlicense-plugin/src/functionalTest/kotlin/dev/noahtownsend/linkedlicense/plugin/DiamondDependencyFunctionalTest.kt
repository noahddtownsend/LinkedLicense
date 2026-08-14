package dev.noahtownsend.linkedlicense.plugin

import dev.noahtownsend.linkedlicense.plugin.testsupport.FixtureProject
import dev.noahtownsend.linkedlicense.plugin.testsupport.MavenFixtureRepo
import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DiamondDependencyFunctionalTest {
    @TempDir
    lateinit var projectDir: File

    @Test
    fun `a shared transitive dependency reached via a diamond graph and declared directly appears exactly once`() {
        val repo = MavenFixtureRepo(File(projectDir, "repo"))

        // shared-transitive is reachable via direct-a, via direct-b, AND declared directly.
        repo.publish(group = "com.example", artifact = "shared-transitive", version = "1.0", licenseName = "Apache-2.0")
        repo.publish(
            group = "com.example",
            artifact = "direct-a",
            version = "1.0",
            licenseName = "MIT",
            dependencies = listOf(MavenFixtureRepo.Dep("com.example", "shared-transitive", "1.0")),
        )
        repo.publish(
            group = "com.example",
            artifact = "direct-b",
            version = "1.0",
            licenseName = "MIT",
            dependencies = listOf(MavenFixtureRepo.Dep("com.example", "shared-transitive", "1.0")),
        )

        val fixture = FixtureProject(projectDir, repo)
        fixture.writeSettings()
        fixture.writeBuildFile(
            dependencyCoordinates =
                listOf(
                    "com.example:direct-a:1.0",
                    "com.example:direct-b:1.0",
                    "com.example:shared-transitive:1.0",
                ),
        )

        val result = fixture.run("generateLicenseCatalog")

        assertTrue(result.output.contains("BUILD SUCCESSFUL"), result.output)
        val generated = fixture.generatedLicensesFile().readText()

        // Each entry is a top-level list item; every element source line starts with
        // "elementLicensed = \"shared-transitive\"" exactly once.
        val occurrences = Regex("elementLicensed = \"shared-transitive\"").findAll(generated).count()
        assertEquals(1, occurrences, generated)
    }
}
