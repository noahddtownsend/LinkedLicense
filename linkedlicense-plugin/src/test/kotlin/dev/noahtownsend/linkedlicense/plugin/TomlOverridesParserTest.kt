package dev.noahtownsend.linkedlicense.plugin

import dev.noahtownsend.linkedlicense.License
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TomlOverridesParserTest {
    private val tempDir = Files.createTempDirectory("linkedlicense-toml-test").toFile()

    @AfterTest
    fun cleanup() {
        tempDir.deleteRecursively()
    }

    private fun parse(
        toml: String,
        resolveAlias: (String) -> String = { it },
    ): OverridesConfig {
        val file = tempDir.resolve("linkedlicense.toml")
        file.writeText(toml)
        return TomlOverridesParser.parse(file, resolveAlias)
    }

    @Test
    fun `parse() returns empty config when the file does not exist`() {
        val config = TomlOverridesParser.parse(tempDir.resolve("missing.toml"), resolveAlias = { it })

        assertEquals(OverridesConfig.EMPTY, config)
    }

    @Test
    fun `parse() reads a built-in override entry keyed by raw group-colon-artifact`() {
        val config =
            parse(
                """
                [overrides]
                "com.example:foo" = { license = "Apache2" }
                """.trimIndent(),
            )

        val entry = config.overrides.getValue("com.example:foo") as OverrideSpec.BuiltIn
        assertEquals(License.Apache2::class, entry.kClass)
    }

    @Test
    fun `parse() reads a custom override entry`() {
        val config =
            parse(
                """
                [overrides]
                "com.mapbox.maps:android" = { license = "custom:com.acme.licenses.MyLicense" }
                """.trimIndent(),
            )

        val entry = config.overrides.getValue("com.mapbox.maps:android") as OverrideSpec.Custom
        assertEquals("com.acme.licenses.MyLicense", entry.fullyQualifiedName)
    }

    @Test
    fun `parse() resolves libs-dot-alias override keys via the alias resolver`() {
        val config =
            parse(
                toml =
                    """
                    [overrides]
                    "libs.okio" = { license = "Apache2" }
                    """.trimIndent(),
                resolveAlias = { alias -> if (alias == "libs.okio") "com.squareup.okio:okio" else error("unexpected $alias") },
            )

        assertTrue(config.overrides.containsKey("com.squareup.okio:okio"))
        assertTrue(!config.overrides.containsKey("libs.okio"))
    }

    @Test
    fun `parse() reads ignored entries with their reason string`() {
        val config =
            parse(
                """
                [ignored]
                "com.example:internal-tool" = "Vendored fork, not redistributed."
                """.trimIndent(),
            )

        assertEquals("Vendored fork, not redistributed.", config.ignored["com.example:internal-tool"])
    }

    @Test
    fun `parse() reads copyleft-allowed entries with their reason string`() {
        val config =
            parse(
                """
                [copyleft-allowed]
                "org.gnu:some-lib" = "Used only at build time."
                """.trimIndent(),
            )

        assertEquals("Used only at build time.", config.copyleftAllowed["org.gnu:some-lib"])
    }

    @Test
    fun `parse() reads license-policy allow and block arrays`() {
        val config =
            parse(
                """
                [license-policy]
                allow = ["MIT", "Apache2"]
                block = ["Gpl3"]
                """.trimIndent(),
            )

        assertEquals(setOf("MIT", "Apache2"), config.licensePolicy.allow)
        assertEquals(setOf("Gpl3"), config.licensePolicy.block)
    }

    @Test
    fun `parse() defaults license-policy to empty allow and block when absent`() {
        val config = parse("[ignored]\n\"com.example:x\" = \"reason\"")

        assertEquals(LicensePolicy.EMPTY, config.licensePolicy)
    }
}
