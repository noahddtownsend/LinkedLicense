package dev.noahtownsend.linkedlicense.plugin

import dev.noahtownsend.linkedlicense.License
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CatalogGeneratorTest {
    private val apacheCoord = Coordinate("com.example", "apache-lib", "1.0")
    private val unknownCoord = Coordinate("com.example", "unknown-lib", "1.0")
    private val gplCoord = Coordinate("com.example", "gpl-lib", "1.0")
    private val lgplCoord = Coordinate("com.example", "lgpl-lib", "1.0")

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
    fun `resolve() fails an LGPL-licensed dependency by default, same as failOnCopyleft governs GPL`() {
        val result =
            CatalogGenerator.resolve(
                coordinates = listOf(lgplCoord),
                pomInfoOf = pomInfoFor(mapOf(lgplCoord to pomInfo("LGPL-2.1"))),
                overrides = OverridesConfig.EMPTY,
                failOnCopyleft = true,
                failOnUnknown = true,
            )

        assertEquals(listOf(lgplCoord), result.copyleftOffenders)
        assertTrue(result.entries.isEmpty())
    }

    @Test
    fun `resolve() lets an LGPL dependency through when failOnSoftCopyleft is false while GPL still fails`() {
        val result =
            CatalogGenerator.resolve(
                coordinates = listOf(gplCoord, lgplCoord),
                pomInfoOf = pomInfoFor(mapOf(gplCoord to pomInfo("GPL-3.0"), lgplCoord to pomInfo("LGPL-2.1"))),
                overrides = OverridesConfig.EMPTY,
                failOnCopyleft = true,
                failOnSoftCopyleft = false,
                failOnUnknown = true,
            )

        assertEquals(listOf(gplCoord), result.copyleftOffenders)
        assertEquals(1, result.entries.size)
    }

    @Test
    fun `resolve() fails an LGPL dependency when failOnSoftCopyleft is true even though failOnCopyleft is false`() {
        val result =
            CatalogGenerator.resolve(
                coordinates = listOf(lgplCoord),
                pomInfoOf = pomInfoFor(mapOf(lgplCoord to pomInfo("LGPL-2.1"))),
                overrides = OverridesConfig.EMPTY,
                failOnCopyleft = false,
                failOnSoftCopyleft = true,
                failOnUnknown = true,
            )

        assertEquals(listOf(lgplCoord), result.copyleftOffenders)
        assertTrue(result.entries.isEmpty())
    }

    @Test
    fun `resolve() lets an LGPL dependency through when copyleft-allowed even with failOnSoftCopyleft true`() {
        val overrides = OverridesConfig(copyleftAllowed = mapOf(lgplCoord.moduleId to "build-time only"))

        val result =
            CatalogGenerator.resolve(
                coordinates = listOf(lgplCoord),
                pomInfoOf = pomInfoFor(mapOf(lgplCoord to pomInfo("LGPL-2.1"))),
                overrides = overrides,
                failOnCopyleft = false,
                failOnSoftCopyleft = true,
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
    fun `resolve() threads a Custom built-in override's licenseName into the generated expression`() {
        val overrides =
            OverridesConfig(
                overrides =
                    mapOf(
                        unknownCoord.moduleId to
                            OverrideSpec.BuiltIn(
                                kClass = License.Custom::class,
                                elementLicensed = "Mapbox Maps SDK",
                                author = "Mapbox",
                                url = null,
                                text = "Some bespoke license text.",
                                licenseName = "Mapbox ToS",
                            ),
                    ),
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
        assertTrue(entry is CatalogEntry.BuiltIn)
        assertTrue(entry.expression.contains("licenseName = \"Mapbox ToS\""), entry.expression)
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
    fun `resolve() uses organization name for author when present`() {
        val coord = Coordinate("com.squareup.okhttp3", "okhttp", "4.12.0")
        val info = PomInfo(
            name = "OkHttp",
            licenses = listOf(PomLicense("Apache-2.0", null)),
            organizationName = "Square, Inc.",
            developers = listOf(PomDeveloper("Square")),
        )

        val result = CatalogGenerator.resolve(
            coordinates = listOf(coord),
            pomInfoOf = pomInfoFor(mapOf(coord to info)),
            overrides = OverridesConfig.EMPTY,
            failOnCopyleft = true,
            failOnUnknown = true,
        )

        val entry = result.entries.single().second as CatalogEntry.BuiltIn
        assertTrue(entry.expression.contains("author = \"Square, Inc.\""), entry.expression)
        assertTrue(entry.expression.contains("elementLicensed = \"OkHttp\""), entry.expression)
    }

    @Test
    fun `resolve() uses developer name for author when organization name is absent`() {
        val coord = Coordinate("org.example", "my-lib", "1.0")
        val info = PomInfo(
            name = null,
            licenses = listOf(PomLicense("MIT", null)),
            organizationName = null,
            developers = listOf(PomDeveloper("Jane Developer")),
        )

        val result = CatalogGenerator.resolve(
            coordinates = listOf(coord),
            pomInfoOf = pomInfoFor(mapOf(coord to info)),
            overrides = OverridesConfig.EMPTY,
            failOnCopyleft = true,
            failOnUnknown = true,
        )

        val entry = result.entries.single().second as CatalogEntry.BuiltIn
        assertTrue(entry.expression.contains("author = \"Jane Developer\""), entry.expression)
        assertTrue(entry.expression.contains("elementLicensed = \"my-lib\""), entry.expression)
    }

    @Test
    fun `resolve() falls back to groupId for author and artifactId for elementLicensed when metadata is absent`() {
        val coord = Coordinate("org.example", "my-lib", "1.0")
        val info = PomInfo(
            name = null,
            licenses = listOf(PomLicense("MIT", null)),
            organizationName = null,
            developers = emptyList(),
        )

        val result = CatalogGenerator.resolve(
            coordinates = listOf(coord),
            pomInfoOf = pomInfoFor(mapOf(coord to info)),
            overrides = OverridesConfig.EMPTY,
            failOnCopyleft = true,
            failOnUnknown = true,
        )

        val entry = result.entries.single().second as CatalogEntry.BuiltIn
        assertTrue(entry.expression.contains("author = \"org.example\""), entry.expression)
        assertTrue(entry.expression.contains("elementLicensed = \"my-lib\""), entry.expression)
    }

    @Test
    fun `withParent merges licenses, organizationName, developers, and scmUrl from parent when child is empty`() {
        val parent = PomInfo(
            name = "Parent Project",
            licenses = listOf(PomLicense("Apache-2.0", "https://apache.org/license")),
            organizationName = "Parent Org",
            developers = listOf(PomDeveloper("Parent Dev")),
            scmUrl = "https://github.com/parent/repo",
            parentRef = null,
        )
        val child = PomInfo(
            name = "Child Library",
            licenses = emptyList(),
            organizationName = null,
            developers = emptyList(),
            scmUrl = null,
            parentRef = ParentPomRef("com.example", "parent", "1.0"),
        )

        val merged = child.withParent(parent)

        assertEquals("Child Library", merged.name)
        assertEquals(listOf(PomLicense("Apache-2.0", "https://apache.org/license")), merged.licenses)
        assertEquals("Parent Org", merged.organizationName)
        assertEquals(listOf(PomDeveloper("Parent Dev")), merged.developers)
        assertEquals("https://github.com/parent/repo", merged.scmUrl)
        assertEquals("Parent Org", merged.resolveAuthor("com.example"))
        assertEquals("Child Library", merged.resolveElementLicensed("child"))
    }

    @Test
    fun `resolve() allows overriding only author while auto-matching license and populating other fields`() {
        val coord = Coordinate("com.squareup.okhttp3", "okhttp", "4.12.0")
        val info = PomInfo(
            name = "OkHttp Client",
            licenses = listOf(PomLicense("Apache-2.0", "https://square.github.io/okhttp/")),
            organizationName = "Square",
        )
        val overrides = OverridesConfig(
            overrides = mapOf(coord.moduleId to OverrideSpec.BuiltIn(author = "Square, Inc.")),
        )

        val result = CatalogGenerator.resolve(
            coordinates = listOf(coord),
            pomInfoOf = pomInfoFor(mapOf(coord to info)),
            overrides = overrides,
            failOnCopyleft = true,
            failOnUnknown = true,
        )

        assertEquals(1, result.entries.size)
        val entry = result.entries.single().second as CatalogEntry.BuiltIn
        assertTrue(entry.expression.contains("License.Apache2("), entry.expression)
        assertTrue(entry.expression.contains("author = \"Square, Inc.\""), entry.expression)
        assertTrue(entry.expression.contains("elementLicensed = \"OkHttp Client\""), entry.expression)
        assertTrue(entry.expression.contains("url = \"https://square.github.io/okhttp/\""), entry.expression)
    }

    @Test
    fun `resolve() auto-populates url and elementLicensed when only license is overridden`() {
        val coord = Coordinate("com.example", "foo", "1.0")
        val info = PomInfo(
            name = "Foo Project",
            licenses = listOf(PomLicense("Unrecognized", "https://example.com/foo")),
            organizationName = "Example Org",
        )
        val overrides = OverridesConfig(
            overrides = mapOf(coord.moduleId to OverrideSpec.BuiltIn(kClass = License.MIT::class)),
        )

        val result = CatalogGenerator.resolve(
            coordinates = listOf(coord),
            pomInfoOf = pomInfoFor(mapOf(coord to info)),
            overrides = overrides,
            failOnCopyleft = true,
            failOnUnknown = true,
        )

        val entry = result.entries.single().second as CatalogEntry.BuiltIn
        assertTrue(entry.expression.contains("License.MIT("), entry.expression)
        assertTrue(entry.expression.contains("author = \"Example Org\""), entry.expression)
        assertTrue(entry.expression.contains("elementLicensed = \"Foo Project\""), entry.expression)
        assertTrue(entry.expression.contains("url = \"https://example.com/foo\""), entry.expression)
    }

    @Test
    fun `resolve() respects autoPopulate = false on override entry by not pulling POM metadata`() {
        val coord = Coordinate("com.example", "foo", "1.0")
        val info = PomInfo(
            name = "Foo Project",
            licenses = listOf(PomLicense("MIT", "https://example.com/foo")),
            organizationName = "Example Org",
        )
        val overrides = OverridesConfig(
            overrides = mapOf(
                coord.moduleId to OverrideSpec.BuiltIn(
                    kClass = License.MIT::class,
                    autoPopulate = false,
                ),
            ),
        )

        val result = CatalogGenerator.resolve(
            coordinates = listOf(coord),
            pomInfoOf = pomInfoFor(mapOf(coord to info)),
            overrides = overrides,
            failOnCopyleft = true,
            failOnUnknown = true,
        )

        val entry = result.entries.single().second as CatalogEntry.BuiltIn
        assertTrue(entry.expression.contains("author = \"com.example\""), entry.expression)
        assertTrue(entry.expression.contains("elementLicensed = \"foo\""), entry.expression)
        assertTrue(!entry.expression.contains("url ="), entry.expression)
    }

    @Test
    fun `resolve() respects global autoPopulate = false`() {
        val coord = Coordinate("com.example", "foo", "1.0")
        val info = PomInfo(
            name = "Foo Project",
            licenses = listOf(PomLicense("MIT", "https://example.com/foo")),
            organizationName = "Example Org",
        )

        val result = CatalogGenerator.resolve(
            coordinates = listOf(coord),
            pomInfoOf = pomInfoFor(mapOf(coord to info)),
            overrides = OverridesConfig.EMPTY,
            failOnCopyleft = true,
            failOnUnknown = true,
            autoPopulate = false,
        )

        val entry = result.entries.single().second as CatalogEntry.BuiltIn
        assertTrue(entry.expression.contains("author = \"com.example\""), entry.expression)
        assertTrue(entry.expression.contains("elementLicensed = \"foo\""), entry.expression)
        assertTrue(!entry.expression.contains("url ="), entry.expression)
    }

    @Test
    fun `resolve() interpolates project and pom built-in placeholders in POM name and metadata`() {
        val coord = Coordinate("com.contentful.java", "java-sdk", "18.5.25")
        val rawInfo = PomInfo(
            name = "\${project.groupId}:\${project.artifactId}",
            organizationName = "\${project.groupId} Team",
            licenses = listOf(PomLicense("Apache-2.0", "https://github.com/\${project.artifactId}/license")),
        )
        val interpolatedInfo = rawInfo.interpolated(coord)

        val result = CatalogGenerator.resolve(
            coordinates = listOf(coord),
            pomInfoOf = pomInfoFor(mapOf(coord to interpolatedInfo)),
            overrides = OverridesConfig.EMPTY,
            failOnCopyleft = true,
            failOnUnknown = true,
        )

        val entry = result.entries.single().second as CatalogEntry.BuiltIn
        assertTrue(entry.expression.contains("elementLicensed = \"com.contentful.java:java-sdk\""), entry.expression)
        assertTrue(entry.expression.contains("author = \"com.contentful.java Team\""), entry.expression)
        assertTrue(entry.expression.contains("url = \"https://github.com/java-sdk/license\""), entry.expression)
    }

    @Test
    fun `resolve() interpolates custom properties and inherited parent properties`() {
        val parent = PomInfo(
            properties = mapOf("company.name" to "Acme Corp", "base.url" to "https://acme.org"),
        )
        val child = PomInfo(
            name = "\${prefix}-\${project.artifactId}",
            organizationName = "\${company.name}",
            properties = mapOf("prefix" to "awesome"),
            licenses = listOf(PomLicense("MIT", "\${base.url}/terms")),
        )
        val coord = Coordinate("org.acme", "super-widget", "2.0.0")
        val merged = child.withParent(parent).interpolated(coord)

        val result = CatalogGenerator.resolve(
            coordinates = listOf(coord),
            pomInfoOf = pomInfoFor(mapOf(coord to merged)),
            overrides = OverridesConfig.EMPTY,
            failOnCopyleft = true,
            failOnUnknown = true,
        )

        val entry = result.entries.single().second as CatalogEntry.BuiltIn
        assertTrue(entry.expression.contains("elementLicensed = \"awesome-super-widget\""), entry.expression)
        assertTrue(entry.expression.contains("author = \"Acme Corp\""), entry.expression)
        assertTrue(entry.expression.contains("url = \"https://acme.org/terms\""), entry.expression)
    }

    @Test
    fun `resolve() falls back to artifact coordinate and triggers warning callback when placeholder is unresolved`() {
        val coord = Coordinate("com.example", "broken-meta", "1.0")
        val info = PomInfo(
            name = "\${unresolvable.variable}",
            organizationName = "\${unresolvable.author}",
            licenses = listOf(PomLicense("MIT", null)),
        ).interpolated(coord)

        var warnedCoord: Coordinate? = null
        var warnedName: String? = null

        val result = CatalogGenerator.resolve(
            coordinates = listOf(coord),
            pomInfoOf = pomInfoFor(mapOf(coord to info)),
            overrides = OverridesConfig.EMPTY,
            failOnCopyleft = true,
            failOnUnknown = true,
            onUnresolvedPlaceholder = { c, n ->
                warnedCoord = c
                warnedName = n
            },
        )

        val entry = result.entries.single().second as CatalogEntry.BuiltIn
        assertTrue(entry.expression.contains("elementLicensed = \"broken-meta\""), entry.expression)
        assertTrue(entry.expression.contains("author = \"com.example\""), entry.expression)
        assertEquals(coord, warnedCoord)
        assertEquals("\${unresolvable.variable}", warnedName)
    }

    @Test
    fun `resolve() propagates notice text from noticeOf to generated expression`() {
        val noticeText = "Copyright 2016-2024 JetBrains s.r.o and contributors"
        val result =
            CatalogGenerator.resolve(
                coordinates = listOf(apacheCoord),
                pomInfoOf = pomInfoFor(mapOf(apacheCoord to pomInfo("Apache-2.0"))),
                overrides = OverridesConfig.EMPTY,
                failOnCopyleft = true,
                failOnUnknown = true,
                noticeOf = { if (it == apacheCoord) noticeText else null },
            )

        val entry = result.entries.single().second as CatalogEntry.BuiltIn
        assertTrue(entry.expression.contains("notice = \"Copyright 2016-2024 JetBrains s.r.o and contributors\""), entry.expression)
    }

    @Test
    fun `resolve() uses override notice over noticeOf`() {
        val scanNotice = "Scan Notice Text"
        val overrideNotice = "Explicit Override Notice"
        val overrides =
            OverridesConfig(
                overrides =
                    mapOf(
                        apacheCoord.moduleId to
                            OverrideSpec.BuiltIn(
                                kClass = License.Apache2::class,
                                notice = overrideNotice,
                            ),
                    ),
            )

        val result =
            CatalogGenerator.resolve(
                coordinates = listOf(apacheCoord),
                pomInfoOf = pomInfoFor(mapOf(apacheCoord to pomInfo("Apache-2.0"))),
                overrides = overrides,
                failOnCopyleft = true,
                failOnUnknown = true,
                noticeOf = { scanNotice },
            )

        val entry = result.entries.single().second as CatalogEntry.BuiltIn
        assertTrue(entry.expression.contains("notice = \"Explicit Override Notice\""), entry.expression)
    }

    @Test
    fun `resolve() includes notice in asset entries when present`() {
        val assetNotice = "Asset Notice Text"
        val overrides =
            OverridesConfig(
                assets =
                    mapOf(
                        "my-font" to
                            OverrideSpec.BuiltIn(
                                kClass = License.Ofl::class,
                                notice = assetNotice,
                            ),
                    ),
            )

        val result =
            CatalogGenerator.resolve(
                coordinates = emptyList(),
                pomInfoOf = { PomInfo(emptyList(), null) },
                overrides = overrides,
                failOnCopyleft = true,
                failOnUnknown = true,
                includeAssets = true,
            )

        val entry = result.assetEntries.single().second as CatalogEntry.BuiltIn
        assertTrue(entry.expression.contains("notice = \"Asset Notice Text\""), entry.expression)
    }
}

