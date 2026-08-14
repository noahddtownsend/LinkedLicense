package dev.noahtownsend.linkedlicense.plugin

import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class NpmLicensesTest {
    private val tempDir = Files.createTempDirectory("linkedlicense-npm-test").toFile()

    @AfterTest
    fun cleanup() {
        tempDir.deleteRecursively()
    }

    @Test
    fun `parseNpmPackageJson() reads a plain SPDX string license field`() {
        val info = parseNpmPackageJson("""{"name": "left-pad", "version": "1.3.0", "license": "MIT"}""")

        assertEquals("left-pad", info?.name)
        assertEquals("1.3.0", info?.version)
        assertEquals(PomLicense(name = "MIT", url = null), info?.license)
    }

    @Test
    fun `parseNpmPackageJson() reads the older type-url object license field`() {
        val info =
            parseNpmPackageJson(
                """{"name": "old-pkg", "version": "0.1.0", "license": {"type": "Apache-2.0", "url": "https://example.com/LICENSE"}}""",
            )

        assertEquals(PomLicense(name = "Apache-2.0", url = "https://example.com/LICENSE"), info?.license)
    }

    @Test
    fun `parseNpmPackageJson() falls back to the legacy licenses array`() {
        val info =
            parseNpmPackageJson(
                """{"name": "legacy-pkg", "version": "0.1.0", "licenses": [{"type": "ISC", "url": "https://example.com"}]}""",
            )

        assertEquals(PomLicense(name = "ISC", url = "https://example.com"), info?.license)
    }

    @Test
    fun `parseNpmPackageJson() returns null license when the field is absent`() {
        val info = parseNpmPackageJson("""{"name": "no-license-pkg", "version": "0.1.0"}""")

        assertNull(info?.license)
    }

    @Test
    fun `parseNpmPackageJson() reads dependency names and normalizes a repository url`() {
        val info =
            parseNpmPackageJson(
                """
                {
                  "name": "foo",
                  "version": "1.0.0",
                  "dependencies": {"bar": "^1.0.0", "baz": "^2.0.0"},
                  "repository": {"type": "git", "url": "git+https://github.com/example/foo.git"}
                }
                """.trimIndent(),
            )

        assertEquals(setOf("bar", "baz"), info?.dependencyNames)
        assertEquals("https://github.com/example/foo", info?.repositoryUrl)
    }

    @Test
    fun `parseNpmPackageJson() returns null for a missing file`() {
        assertNull(parseNpmPackageJson(tempDir.resolve("missing.json")))
    }

    private fun writePackage(
        dir: java.io.File,
        name: String,
        version: String,
        license: String? = "MIT",
        dependencies: Map<String, String> = emptyMap(),
    ) {
        dir.mkdirs()
        val deps = dependencies.entries.joinToString(",") { (k, v) -> "\"$k\": \"$v\"" }
        val licenseJson = if (license != null) "\"license\": \"$license\"," else ""
        java.io.File(dir, "package.json").writeText(
            """{"name": "$name", "version": "$version", $licenseJson "dependencies": {$deps}}""",
        )
    }

    @Test
    fun `scanNpmDependencyGraph() walks direct and nested transitive packages`() {
        val nodeModules = tempDir.resolve("node_modules")
        writePackage(nodeModules.resolve("left-pad"), "left-pad", "1.3.0")
        writePackage(nodeModules.resolve("chalk"), "chalk", "4.1.2", dependencies = mapOf("ansi-styles" to "^4.0.0"))
        writePackage(nodeModules.resolve("ansi-styles"), "ansi-styles", "4.3.0")

        // A nested override: a different version of ansi-styles required by a different package.
        writePackage(
            nodeModules.resolve("some-tool").resolve("node_modules").resolve("ansi-styles"),
            "ansi-styles",
            "3.2.1",
        )
        writePackage(nodeModules.resolve("some-tool"), "some-tool", "1.0.0")

        val coordinates = scanNpmDependencyGraph(nodeModules)

        assertEquals(
            setOf(
                Coordinate("npm", "left-pad", "1.3.0"),
                Coordinate("npm", "chalk", "4.1.2"),
                Coordinate("npm", "ansi-styles", "4.3.0"),
                Coordinate("npm", "ansi-styles", "3.2.1"),
                Coordinate("npm", "some-tool", "1.0.0"),
            ),
            coordinates.toSet(),
        )
    }

    @Test
    fun `scanNpmDependencyGraph() walks scoped packages`() {
        val nodeModules = tempDir.resolve("node_modules")
        writePackage(nodeModules.resolve("@scope").resolve("pkg"), "@scope/pkg", "2.0.0")

        val coordinates = scanNpmDependencyGraph(nodeModules)

        assertTrue(coordinates.contains(Coordinate("npm", "@scope/pkg", "2.0.0")))
    }

    @Test
    fun `scanNpmDependencyGraph() returns empty list when node_modules does not exist`() {
        assertEquals(emptyList(), scanNpmDependencyGraph(tempDir.resolve("does-not-exist")))
    }

    @Test
    fun `scanNpmPackageInfo() moduleId matches the npm colon package-name coordinate format`() {
        val nodeModules = tempDir.resolve("node_modules")
        writePackage(nodeModules.resolve("left-pad"), "left-pad", "1.3.0")

        val info = scanNpmPackageInfo(nodeModules)
        val coordinate = info.keys.single()

        assertEquals("npm:left-pad", coordinate.moduleId)
    }

    @Test
    fun `toPomInfo() carries the license and repository url through`() {
        val npmInfo =
            NpmPackageInfo(
                name = "foo",
                version = "1.0.0",
                license = PomLicense(name = "MIT", url = null),
                dependencyNames = emptySet(),
                repositoryUrl = "https://github.com/example/foo",
            )

        val pomInfo = npmInfo.toPomInfo()

        assertEquals(listOf(PomLicense(name = "MIT", url = null)), pomInfo.licenses)
        assertEquals("https://github.com/example/foo", pomInfo.scmUrl)
    }
}
