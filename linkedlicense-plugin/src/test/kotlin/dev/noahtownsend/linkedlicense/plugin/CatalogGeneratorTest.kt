package dev.noahtownsend.linkedlicense.plugin

import dev.noahtownsend.linkedlicense.License
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CatalogGeneratorTest {
    private val apacheCoord = Coordinate("com.example", "apache-lib", "1.0")
    private val unknownCoord = Coordinate("com.example", "unknown-lib", "1.0")
    private val gplCoord = Coordinate("com.example", "gpl-lib", "1.0")

    private fun pomInfo(licenseName: String?): PomInfo = PomInfo(licenses = listOfNotNull(licenseName?.let { PomLicense(it, null) }), organizationName = null)

    private fun pomInfoFor(coordinates: Map<Coordinate, PomInfo>): (Coordinate) -> PomInfo = { coordinates[it] ?: PomInfo(emptyList(), null) }

    @Test
    fun `resolve() auto-matches a dependency with a known POM license`() {
        val result =
            CatalogGenerator.resolve(
                coordinates = listOf(apacheCoord),
                pomInfoOf = pomInfoFor(mapOf(apacheCoord to pomInfo("Apache-2.0"))),
                overrides = OverridesConfig.EMPTY,
                failOnCopyleft = true,
                failOnUnknown = true,
            )

        assertEquals(1, result.entries.size)
        assertTrue(result.unresolved.isEmpty())
    }

    @Test
    fun `resolve() collects every unresolved coordinate rather than failing on the first`() {
        val other = Coordinate("com.example", "unknown-lib-2", "1.0")

        val result =
            CatalogGenerator.resolve(
                coordinates = listOf(unknownCoord, other),
                pomInfoOf = pomInfoFor(mapOf(unknownCoord to pomInfo(null), other to pomInfo("Some Bespoke License"))),
                overrides = OverridesConfig.EMPTY,
                failOnCopyleft = true,
                failOnUnknown = true,
            )

        assertEquals(setOf(unknownCoord, other), result.unresolved.toSet())
        assertTrue(result.entries.isEmpty())
    }

    @Test
    fun `resolve() omits unresolved coordinates instead of failing when failOnUnknown is false`() {
        val result =
            CatalogGenerator.resolve(
                coordinates = listOf(unknownCoord),
                pomInfoOf = pomInfoFor(mapOf(unknownCoord to pomInfo(null))),
                overrides = OverridesConfig.EMPTY,
                failOnCopyleft = true,
                failOnUnknown = false,
            )

        assertTrue(result.unresolved.isEmpty())
        assertTrue(result.entries.isEmpty())
    }

    @Test
    fun `resolve() applies an override instead of failing on an unmatched coordinate`() {
        val overrides =
            OverridesConfig(
                overrides = mapOf(unknownCoord.moduleId to OverrideSpec.BuiltIn(License.Apache2::class, null, null, null, null)),
            )

        val result =
            CatalogGenerator.resolve(
                coordinates = listOf(unknownCoord),
                pomInfoOf = pomInfoFor(mapOf(unknownCoord to pomInfo(null))),
                overrides = overrides,
                failOnCopyleft = true,
                failOnUnknown = true,
            )

        assertTrue(result.unresolved.isEmpty())
        assertEquals(1, result.entries.size)
    }

    @Test
    fun `resolve() excludes an ignored coordinate from both the catalog and unresolved list`() {
        val overrides = OverridesConfig(ignored = mapOf(unknownCoord.moduleId to "not redistributed"))

        val result =
            CatalogGenerator.resolve(
                coordinates = listOf(unknownCoord),
                pomInfoOf = pomInfoFor(mapOf(unknownCoord to pomInfo(null))),
                overrides = overrides,
                failOnCopyleft = true,
                failOnUnknown = true,
            )

        assertTrue(result.unresolved.isEmpty())
        assertTrue(result.entries.isEmpty())
    }

    @Test
    fun `resolve() fails a GPL-licensed dependency by default`() {
        val result =
            CatalogGenerator.resolve(
                coordinates = listOf(gplCoord),
                pomInfoOf = pomInfoFor(mapOf(gplCoord to pomInfo("GPL-3.0"))),
                overrides = OverridesConfig.EMPTY,
                failOnCopyleft = true,
                failOnUnknown = true,
            )

        assertEquals(listOf(gplCoord), result.copyleftOffenders)
        assertTrue(result.entries.isEmpty())
    }

    @Test
    fun `resolve() lets a GPL dependency through when copyleft-allowed`() {
        val overrides = OverridesConfig(copyleftAllowed = mapOf(gplCoord.moduleId to "build-time only"))

        val result =
            CatalogGenerator.resolve(
                coordinates = listOf(gplCoord),
                pomInfoOf = pomInfoFor(mapOf(gplCoord to pomInfo("GPL-3.0"))),
                overrides = overrides,
                failOnCopyleft = true,
                failOnUnknown = true,
            )

        assertTrue(result.copyleftOffenders.isEmpty())
        assertEquals(1, result.entries.size)
    }

    @Test
    fun `resolve() lets a GPL dependency through when failOnCopyleft is false`() {
        val result =
            CatalogGenerator.resolve(
                coordinates = listOf(gplCoord),
                pomInfoOf = pomInfoFor(mapOf(gplCoord to pomInfo("GPL-3.0"))),
                overrides = OverridesConfig.EMPTY,
                failOnCopyleft = false,
                failOnUnknown = true,
            )

        assertTrue(result.copyleftOffenders.isEmpty())
        assertEquals(1, result.entries.size)
    }

    @Test
    fun `resolve() fails a coordinate whose license type is not in a non-empty allow list`() {
        val overrides = OverridesConfig(licensePolicy = LicensePolicy(allow = setOf("MIT")))

        val result =
            CatalogGenerator.resolve(
                coordinates = listOf(apacheCoord),
                pomInfoOf = pomInfoFor(mapOf(apacheCoord to pomInfo("Apache-2.0"))),
                overrides = overrides,
                failOnCopyleft = true,
                failOnUnknown = true,
            )

        assertEquals(listOf(PolicyOffender(apacheCoord, "Apache2")), result.policyOffenders)
        assertTrue(result.entries.isEmpty())
    }

    @Test
    fun `resolve() passes a coordinate whose license type is in a non-empty allow list`() {
        val overrides = OverridesConfig(licensePolicy = LicensePolicy(allow = setOf("Apache2")))

        val result =
            CatalogGenerator.resolve(
                coordinates = listOf(apacheCoord),
                pomInfoOf = pomInfoFor(mapOf(apacheCoord to pomInfo("Apache-2.0"))),
                overrides = overrides,
                failOnCopyleft = true,
                failOnUnknown = true,
            )

        assertTrue(result.policyOffenders.isEmpty())
        assertEquals(1, result.entries.size)
    }

    @Test
    fun `resolve() fails a coordinate whose license type is block-listed`() {
        val overrides = OverridesConfig(licensePolicy = LicensePolicy(block = setOf("Apache2")))

        val result =
            CatalogGenerator.resolve(
                coordinates = listOf(apacheCoord),
                pomInfoOf = pomInfoFor(mapOf(apacheCoord to pomInfo("Apache-2.0"))),
                overrides = overrides,
                failOnCopyleft = true,
                failOnUnknown = true,
            )

        assertEquals(listOf(PolicyOffender(apacheCoord, "Apache2")), result.policyOffenders)
    }

    @Test
    fun `resolve() passes everything not on the block list when allow is empty`() {
        val overrides = OverridesConfig(licensePolicy = LicensePolicy(block = setOf("Gpl3")))

        val result =
            CatalogGenerator.resolve(
                coordinates = listOf(apacheCoord),
                pomInfoOf = pomInfoFor(mapOf(apacheCoord to pomInfo("Apache-2.0"))),
                overrides = overrides,
                failOnCopyleft = true,
                failOnUnknown = true,
            )

        assertTrue(result.policyOffenders.isEmpty())
        assertEquals(1, result.entries.size)
    }

    @Test
    fun `resolve() lets a non-empty allow list win over block when both are set`() {
        val overrides = OverridesConfig(licensePolicy = LicensePolicy(allow = setOf("Apache2"), block = setOf("Apache2")))

        val result =
            CatalogGenerator.resolve(
                coordinates = listOf(apacheCoord),
                pomInfoOf = pomInfoFor(mapOf(apacheCoord to pomInfo("Apache-2.0"))),
                overrides = overrides,
                failOnCopyleft = true,
                failOnUnknown = true,
            )

        assertTrue(result.policyOffenders.isEmpty())
        assertEquals(1, result.entries.size)
    }

    @Test
    fun `resolve() emits a bare reference for a custom override rather than instantiating a built-in`() {
        val overrides =
            OverridesConfig(
                overrides = mapOf(unknownCoord.moduleId to OverrideSpec.Custom("com.acme.licenses.MyCompanyLicense")),
            )

        val result =
            CatalogGenerator.resolve(
                coordinates = listOf(unknownCoord),
                pomInfoOf = pomInfoFor(mapOf(unknownCoord to pomInfo(null))),
                overrides = overrides,
                failOnCopyleft = true,
                failOnUnknown = true,
            )

        val entry = result.entries.single().second
        assertTrue(entry is CatalogEntry.CustomRef)
        assertEquals("com.acme.licenses.MyCompanyLicense", entry.fullyQualifiedName)
    }

    @Test
    fun `resolve() includes an assets entry in the generated catalog tagged ASSET`() {
        val overrides =
            OverridesConfig(
                assets = mapOf("cinzel-font" to OverrideSpec.BuiltIn(License.Ofl::class, "Cinzel Decorative Font", "Matt Tindal", null, null)),
            )

        val result =
            CatalogGenerator.resolve(
                coordinates = emptyList(),
                pomInfoOf = { PomInfo(emptyList(), null) },
                overrides = overrides,
                failOnCopyleft = true,
                failOnUnknown = true,
            )

        assertEquals(1, result.assetEntries.size)
        val entry = result.assetEntries.single()
        assertEquals("cinzel-font", entry.first)
        val builtIn = entry.second as CatalogEntry.BuiltIn
        assertTrue(builtIn.expression.contains("kind = License.Kind.ASSET"))
    }

    @Test
    fun `resolve() fails a GPL-typed assets entry by default same as a GPL dependency`() {
        val overrides =
            OverridesConfig(
                assets = mapOf("gpl-dataset" to OverrideSpec.BuiltIn(License.Gpl3::class, null, null, null, null)),
            )

        val result =
            CatalogGenerator.resolve(
                coordinates = emptyList(),
                pomInfoOf = { PomInfo(emptyList(), null) },
                overrides = overrides,
                failOnCopyleft = true,
                failOnUnknown = true,
            )

        assertEquals(listOf("gpl-dataset"), result.assetCopyleftOffenders)
        assertTrue(result.assetEntries.isEmpty())
    }

    @Test
    fun `resolve() fails an assets entry whose license type is block-listed`() {
        val overrides =
            OverridesConfig(
                assets = mapOf("mit-dataset" to OverrideSpec.BuiltIn(License.MIT::class, null, null, null, null)),
                licensePolicy = LicensePolicy(block = setOf("MIT")),
            )

        val result =
            CatalogGenerator.resolve(
                coordinates = emptyList(),
                pomInfoOf = { PomInfo(emptyList(), null) },
                overrides = overrides,
                failOnCopyleft = true,
                failOnUnknown = true,
            )

        assertEquals(listOf(AssetPolicyOffender("mit-dataset", "MIT")), result.assetPolicyOffenders)
        assertTrue(result.assetEntries.isEmpty())
    }

    @Test
    fun `resolve() auto-matches an npm coordinate the same way as a Maven one`() {
        val npmCoord = Coordinate("npm", "left-pad", "1.3.0")

        val result =
            CatalogGenerator.resolve(
                coordinates = listOf(npmCoord),
                pomInfoOf = pomInfoFor(mapOf(npmCoord to pomInfo("MIT"))),
                overrides = OverridesConfig.EMPTY,
                failOnCopyleft = true,
                failOnUnknown = true,
            )

        assertEquals(1, result.entries.size)
        assertTrue(result.unresolved.isEmpty())
    }

    @Test
    fun `resolve() looks up npm ignored and overrides entries by their npm-colon-name moduleId`() {
        val npmCoord = Coordinate("npm", "left-pad", "1.3.0")
        val overrides =
            OverridesConfig(overrides = mapOf("npm:left-pad" to OverrideSpec.BuiltIn(License.MIT::class, null, null, null, null)))

        val result =
            CatalogGenerator.resolve(
                coordinates = listOf(npmCoord),
                pomInfoOf = pomInfoFor(mapOf(npmCoord to pomInfo(null))),
                overrides = overrides,
                failOnCopyleft = true,
                failOnUnknown = true,
            )

        assertTrue(result.unresolved.isEmpty())
        assertEquals(1, result.entries.size)
    }

    @Test
    fun `resolve() fails on unknown npm license the same way as an unmatched Maven POM`() {
        val npmCoord = Coordinate("npm", "some-weird-license-pkg", "1.0.0")

        val result =
            CatalogGenerator.resolve(
                coordinates = listOf(npmCoord),
                pomInfoOf = pomInfoFor(mapOf(npmCoord to pomInfo("Some Bespoke License"))),
                overrides = OverridesConfig.EMPTY,
                failOnCopyleft = true,
                failOnUnknown = true,
            )

        assertEquals(listOf(npmCoord), result.unresolved)
    }

    @Test
    fun `resolve() uses the best-effort fetch result when the primary field is unmatched`() {
        var invokedWith: Pair<String, String>? = null

        val result =
            CatalogGenerator.resolve(
                coordinates = listOf(unknownCoord),
                pomInfoOf = pomInfoFor(mapOf(unknownCoord to pomInfo(null).copy(scmUrl = "https://github.com/example/foo"))),
                overrides = OverridesConfig.EMPTY,
                failOnCopyleft = true,
                failOnUnknown = true,
                bestEffortFetch = { repoUrl, ref -> invokedWith = repoUrl to ref; License.MIT::class },
            )

        assertTrue(result.unresolved.isEmpty())
        assertEquals(1, result.entries.size)
        assertEquals("https://github.com/example/foo" to "1.0", invokedWith)
    }

    @Test
    fun `resolve() invokes onBestGuess exactly once for a coordinate resolved via the fallback`() {
        val guesses = mutableListOf<Pair<Coordinate, kotlin.reflect.KClass<out License>>>()

        CatalogGenerator.resolve(
            coordinates = listOf(unknownCoord),
            pomInfoOf = pomInfoFor(mapOf(unknownCoord to pomInfo(null).copy(scmUrl = "https://github.com/example/foo"))),
            overrides = OverridesConfig.EMPTY,
            failOnCopyleft = true,
            failOnUnknown = true,
            bestEffortFetch = { _, _ -> License.Apache2::class },
            onBestGuess = { coordinate, kClass -> guesses += coordinate to kClass },
        )

        assertEquals(listOf<Pair<Coordinate, kotlin.reflect.KClass<out License>>>(unknownCoord to License.Apache2::class), guesses)
    }

    @Test
    fun `resolve() does not invoke onBestGuess when bestEffortFetch itself finds nothing`() {
        var called = false

        val result =
            CatalogGenerator.resolve(
                coordinates = listOf(unknownCoord),
                pomInfoOf = pomInfoFor(mapOf(unknownCoord to pomInfo(null).copy(scmUrl = "https://github.com/example/foo"))),
                overrides = OverridesConfig.EMPTY,
                failOnCopyleft = true,
                failOnUnknown = true,
                bestEffortFetch = { _, _ -> null },
                onBestGuess = { _, _ -> called = true },
            )

        assertEquals(listOf(unknownCoord), result.unresolved)
        assertTrue(!called)
    }

    @Test
    fun `resolve() still applies the copyleft guard to a best-effort-guessed license`() {
        val result =
            CatalogGenerator.resolve(
                coordinates = listOf(unknownCoord),
                pomInfoOf = pomInfoFor(mapOf(unknownCoord to pomInfo(null).copy(scmUrl = "https://github.com/example/foo"))),
                overrides = OverridesConfig.EMPTY,
                failOnCopyleft = true,
                failOnUnknown = true,
                bestEffortFetch = { _, _ -> License.Gpl3::class },
            )

        assertEquals(listOf(unknownCoord), result.copyleftOffenders)
        assertTrue(result.entries.isEmpty())
    }

    @Test
    fun `resolve() never invokes bestEffortFetch when a coordinate already matched normally`() {
        var called = false

        CatalogGenerator.resolve(
            coordinates = listOf(apacheCoord),
            pomInfoOf = pomInfoFor(mapOf(apacheCoord to pomInfo("Apache-2.0").copy(scmUrl = "https://github.com/example/foo"))),
            overrides = OverridesConfig.EMPTY,
            failOnCopyleft = true,
            failOnUnknown = true,
            bestEffortFetch = { _, _ -> called = true; License.MIT::class },
        )

        assertTrue(!called)
    }
}
