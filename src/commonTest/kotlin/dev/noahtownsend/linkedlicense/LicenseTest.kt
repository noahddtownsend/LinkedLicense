package dev.noahtownsend.linkedlicense

import dev.noahtownsend.linkedlicense.License.CcVariant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LicenseTest {
    @Test
    fun `MIT licenseText contains year and author substitutions`() {
        val license =
            License.MIT(
                elementLicensed = "Kotlin",
                author = "JetBrains",
                year = "2011",
            )

        assertTrue(license.licenseText.isNotBlank())
        assertTrue(license.licenseText.contains("2011"))
        assertTrue(license.licenseText.contains("JetBrains"))
        assertTrue(license.licenseText.contains("MIT License"))
    }

    @Test
    fun `MIT is not copyleft`() {
        val license = License.MIT(elementLicensed = "Kotlin", author = "JetBrains", year = "2011")

        assertFalse(license.isCopyleft)
    }

    @Test
    fun `Apache1_1 licenseText is non-blank and contains license name`() {
        val license = License.Apache1_1(elementLicensed = "Foo", author = "Bar")

        assertTrue(license.licenseText.isNotBlank())
        assertTrue(license.licenseText.contains("Apache License, Version 1.1"))
    }

    @Test
    fun `Apache1_1 is not copyleft`() {
        val license = License.Apache1_1(elementLicensed = "Foo", author = "Bar")

        assertFalse(license.isCopyleft)
    }

    @Test
    fun `Apache2 licenseText is non-blank and contains license name`() {
        val license = License.Apache2(elementLicensed = "Ktor", author = "JetBrains")

        assertTrue(license.licenseText.isNotBlank())
        assertTrue(license.licenseText.contains("Apache License"))
        assertTrue(license.licenseText.contains("Version 2.0"))
    }

    @Test
    fun `Apache2 is not copyleft`() {
        val license = License.Apache2(elementLicensed = "Ktor", author = "JetBrains")

        assertFalse(license.isCopyleft)
    }

    @Test
    fun `Bsd2Clause licenseText contains year and author substitutions`() {
        val license = License.Bsd2Clause(elementLicensed = "Foo", author = "Jane Doe", year = "2020")

        assertTrue(license.licenseText.isNotBlank())
        assertTrue(license.licenseText.contains("2020"))
        assertTrue(license.licenseText.contains("Jane Doe"))
        assertFalse(license.isCopyleft)
    }

    @Test
    fun `Bsd3Clause licenseText contains year and author substitutions`() {
        val license = License.Bsd3Clause(elementLicensed = "Foo", author = "Jane Doe", year = "2020")

        assertTrue(license.licenseText.isNotBlank())
        assertTrue(license.licenseText.contains("2020"))
        assertTrue(license.licenseText.contains("Jane Doe"))
        assertTrue(license.licenseText.contains("endorse"))
        assertFalse(license.isCopyleft)
    }

    @Test
    fun `Isc licenseText contains year and author substitutions`() {
        val license = License.Isc(elementLicensed = "Foo", author = "Jane Doe", year = "2020")

        assertTrue(license.licenseText.isNotBlank())
        assertTrue(license.licenseText.contains("2020"))
        assertTrue(license.licenseText.contains("Jane Doe"))
        assertFalse(license.isCopyleft)
    }

    @Test
    fun `Unlicense licenseText is non-blank and not copyleft`() {
        val license = License.Unlicense(elementLicensed = "Foo", author = "Jane Doe")

        assertTrue(license.licenseText.isNotBlank())
        assertTrue(license.licenseText.contains("public domain"))
        assertFalse(license.isCopyleft)
    }

    @Test
    fun `PublicDomain licenseText is non-blank and not copyleft`() {
        val license = License.PublicDomain(elementLicensed = "Foo", author = "Jane Doe")

        assertTrue(license.licenseText.isNotBlank())
        assertFalse(license.isCopyleft)
    }

    @Test
    fun `UsGovernmentPublicDomain licenseText references 17 USC 105`() {
        val license = License.UsGovernmentPublicDomain(elementLicensed = "Foo", author = "U.S. Government")

        assertTrue(license.licenseText.isNotBlank())
        assertTrue(license.licenseText.contains("17 U.S.C"))
        assertFalse(license.isCopyleft)
    }

    @Test
    fun `Gpl2 licenseText is non-blank and is copyleft`() {
        val license = License.Gpl2(elementLicensed = "Foo", author = "Jane Doe")

        assertTrue(license.licenseText.isNotBlank())
        assertTrue(license.licenseText.contains("GNU General Public License"))
        assertTrue(license.licenseText.contains("Version 2"))
        assertTrue(license.licenseText.contains("gnu.org/licenses/old-licenses/gpl-2.0.html"))
        assertTrue(license.isCopyleft)
    }

    @Test
    fun `Gpl3 licenseText is non-blank and is copyleft`() {
        val license = License.Gpl3(elementLicensed = "Foo", author = "Jane Doe")

        assertTrue(license.licenseText.isNotBlank())
        assertTrue(license.licenseText.contains("GNU General Public License"))
        assertTrue(license.licenseText.contains("Version 3"))
        assertTrue(license.licenseText.contains("gnu.org/licenses/gpl-3.0.html"))
        assertTrue(license.isCopyleft)
    }

    @Test
    fun `Lgpl2_1 licenseText is non-blank and is copyleft`() {
        val license = License.Lgpl2_1(elementLicensed = "Foo", author = "Jane Doe")

        assertTrue(license.licenseText.isNotBlank())
        assertTrue(license.licenseText.contains("GNU Lesser General Public License"))
        assertTrue(license.licenseText.contains("Version 2.1"))
        assertTrue(license.licenseText.contains("gnu.org/licenses/old-licenses/lgpl-2.1.html"))
        assertTrue(license.isCopyleft)
    }

    @Test
    fun `Lgpl3 licenseText is non-blank and is copyleft`() {
        val license = License.Lgpl3(elementLicensed = "Foo", author = "Jane Doe")

        assertTrue(license.licenseText.isNotBlank())
        assertTrue(license.licenseText.contains("GNU Lesser General Public License"))
        assertTrue(license.licenseText.contains("Version 3"))
        assertTrue(license.licenseText.contains("gnu.org/licenses/lgpl-3.0.html"))
        assertTrue(license.isCopyleft)
    }

    @Test
    fun `Mpl2 licenseText is non-blank and is copyleft`() {
        val license = License.Mpl2(elementLicensed = "Foo", author = "Jane Doe")

        assertTrue(license.licenseText.isNotBlank())
        assertTrue(license.licenseText.contains("Mozilla Public License"))
        assertTrue(license.licenseText.contains("2.0"))
        assertTrue(license.isCopyleft)
    }

    @Test
    fun `Cc0 licenseText is non-blank and not copyleft`() {
        val license = License.Cc0(elementLicensed = "Foo", author = "Jane Doe")

        assertTrue(license.licenseText.isNotBlank())
        assertTrue(license.licenseText.contains("CC0"))
        assertTrue(license.licenseText.contains("creativecommons.org/publicdomain/zero/1.0/legalcode"))
        assertFalse(license.isCopyleft)
    }

    @Test
    fun `CreativeCommons BY_SA licenseText contains ShareAlike condition and correct legalcode link`() {
        val license =
            License.CreativeCommons(
                variant = CcVariant.BY_SA,
                version = "4.0",
                elementLicensed = "Wikimedia Commons Media",
                author = "Wikimedia Contributors",
            )

        assertTrue(license.licenseText.isNotBlank())
        assertTrue(license.licenseText.contains("ShareAlike"))
        assertTrue(license.licenseText.contains("Attribution"))
        assertTrue(license.licenseText.contains("creativecommons.org/licenses/by-sa/4.0/legalcode"))
        assertFalse(license.isCopyleft)
    }

    @Test
    fun `CreativeCommons BY_ND licenseText contains NoDerivatives condition and no Adapt permission`() {
        val license =
            License.CreativeCommons(
                variant = CcVariant.BY_ND,
                version = "4.0",
                elementLicensed = "Photo",
                author = "Photographer",
            )

        assertTrue(license.licenseText.contains("NoDerivatives"))
        assertFalse(license.licenseText.contains("Adapt"))
    }

    @Test
    fun `exactly the copyleft family is classified as copyleft`() {
        val allLicenses =
            listOf(
                License.MIT(elementLicensed = "e", author = "a", year = "2020"),
                License.Apache1_1(elementLicensed = "e", author = "a"),
                License.Apache2(elementLicensed = "e", author = "a"),
                License.Bsd2Clause(elementLicensed = "e", author = "a", year = "2020"),
                License.Bsd3Clause(elementLicensed = "e", author = "a", year = "2020"),
                License.Isc(elementLicensed = "e", author = "a", year = "2020"),
                License.Gpl2(elementLicensed = "e", author = "a"),
                License.Gpl3(elementLicensed = "e", author = "a"),
                License.Lgpl2_1(elementLicensed = "e", author = "a"),
                License.Lgpl3(elementLicensed = "e", author = "a"),
                License.Mpl2(elementLicensed = "e", author = "a"),
                License.Unlicense(elementLicensed = "e", author = "a"),
                License.PublicDomain(elementLicensed = "e", author = "a"),
                License.UsGovernmentPublicDomain(elementLicensed = "e", author = "a"),
                License.Cc0(elementLicensed = "e", author = "a"),
                License.CreativeCommons(variant = CcVariant.BY, version = "4.0", elementLicensed = "e", author = "a"),
                License.CreativeCommons(variant = CcVariant.BY_SA, version = "4.0", elementLicensed = "e", author = "a"),
                License.CreativeCommons(variant = CcVariant.BY_ND, version = "4.0", elementLicensed = "e", author = "a"),
                License.CreativeCommons(variant = CcVariant.BY_NC, version = "4.0", elementLicensed = "e", author = "a"),
                License.CreativeCommons(variant = CcVariant.BY_NC_SA, version = "4.0", elementLicensed = "e", author = "a"),
                License.CreativeCommons(variant = CcVariant.BY_NC_ND, version = "4.0", elementLicensed = "e", author = "a"),
                License.Ofl(elementLicensed = "e", author = "a"),
                License.OpenGovernmentLicence(jurisdiction = "UK", version = "3.0", elementLicensed = "e", author = "a"),
                License.Mapbox(),
                License.Custom(elementLicensed = "e", author = "a", text = "t"),
            )

        val copyleftTypes = allLicenses.filter { it.isCopyleft }.map { it::class.simpleName }.toSet()

        assertEquals(
            setOf("Gpl2", "Gpl3", "Lgpl2_1", "Lgpl3", "Mpl2"),
            copyleftTypes,
        )
    }

    @Test
    fun `Ofl licenseText is non-blank and not copyleft`() {
        val license = License.Ofl(elementLicensed = "Cinzel Decorative Font", author = "Matt Tindal")

        assertTrue(license.licenseText.isNotBlank())
        assertTrue(license.licenseText.contains("SIL OPEN FONT LICENSE"))
        assertFalse(license.isCopyleft)
    }

    @Test
    fun `OpenGovernmentLicence licenseText contains jurisdiction and version substitutions`() {
        val license =
            License.OpenGovernmentLicence(
                jurisdiction = "United Kingdom",
                version = "3.0",
                elementLicensed = "UK FCDO Travel Advice",
                author = "Foreign, Commonwealth & Development Office",
            )

        assertTrue(license.licenseText.isNotBlank())
        assertTrue(license.licenseText.contains("Open Government Licence"))
        assertTrue(license.licenseText.contains("United Kingdom"))
        assertTrue(license.licenseText.contains("3.0"))
        assertFalse(license.isCopyleft)
    }

    @Test
    fun `Mapbox licenseText has sensible defaults`() {
        val license = License.Mapbox()

        assertTrue(license.licenseText.isNotBlank())
        assertEquals("Mapbox Maps", license.elementLicensed)
        assertEquals("Mapbox", license.author)
        assertFalse(license.isCopyleft)
    }

    @Test
    fun `Custom licenseText is exactly the provided text`() {
        val license =
            License.Custom(
                elementLicensed = "Acme Internal SDK",
                author = "Acme Corp",
                text = "Some bespoke license text.",
            )

        assertEquals("Some bespoke license text.", license.licenseText)
        assertFalse(license.isCopyleft)
    }

    @Test
    fun `elementLicensed and author are exposed on the base type`() {
        val license = License.Apache2(elementLicensed = "Ktor", author = "JetBrains", url = "https://ktor.io")

        assertEquals("Ktor", license.elementLicensed)
        assertEquals("JetBrains", license.author)
        assertEquals("https://ktor.io", license.url)
    }

    @Test
    fun `every built-in License type has a non-blank shortName`() {
        val allLicenses =
            listOf(
                License.MIT(elementLicensed = "e", author = "a", year = "2020"),
                License.Apache1_1(elementLicensed = "e", author = "a"),
                License.Apache2(elementLicensed = "e", author = "a"),
                License.Bsd2Clause(elementLicensed = "e", author = "a", year = "2020"),
                License.Bsd3Clause(elementLicensed = "e", author = "a", year = "2020"),
                License.Isc(elementLicensed = "e", author = "a", year = "2020"),
                License.Gpl2(elementLicensed = "e", author = "a"),
                License.Gpl3(elementLicensed = "e", author = "a"),
                License.Lgpl2_1(elementLicensed = "e", author = "a"),
                License.Lgpl3(elementLicensed = "e", author = "a"),
                License.Mpl2(elementLicensed = "e", author = "a"),
                License.Unlicense(elementLicensed = "e", author = "a"),
                License.PublicDomain(elementLicensed = "e", author = "a"),
                License.UsGovernmentPublicDomain(elementLicensed = "e", author = "a"),
                License.Cc0(elementLicensed = "e", author = "a"),
                License.CreativeCommons(variant = CcVariant.BY, version = "4.0", elementLicensed = "e", author = "a"),
                License.CreativeCommons(variant = CcVariant.BY_SA, version = "4.0", elementLicensed = "e", author = "a"),
                License.CreativeCommons(variant = CcVariant.BY_ND, version = "4.0", elementLicensed = "e", author = "a"),
                License.CreativeCommons(variant = CcVariant.BY_NC, version = "4.0", elementLicensed = "e", author = "a"),
                License.CreativeCommons(variant = CcVariant.BY_NC_SA, version = "4.0", elementLicensed = "e", author = "a"),
                License.CreativeCommons(variant = CcVariant.BY_NC_ND, version = "4.0", elementLicensed = "e", author = "a"),
                License.Ofl(elementLicensed = "e", author = "a"),
                License.OpenGovernmentLicence(jurisdiction = "UK", version = "3.0", elementLicensed = "e", author = "a"),
                License.Mapbox(),
                License.Custom(elementLicensed = "e", author = "a", text = "t"),
            )

        allLicenses.forEach { license ->
            assertTrue(license.shortName.isNotBlank(), "${license::class.simpleName} has a blank shortName")
        }
    }

    @Test
    fun `CreativeCommons shortName reflects variant and version`() {
        val license =
            License.CreativeCommons(
                variant = CcVariant.BY_SA,
                version = "4.0",
                elementLicensed = "Wikimedia Commons Media",
                author = "Wikimedia Contributors",
            )

        assertEquals("CC BY-SA 4.0", license.shortName)
    }

    @Test
    fun `OpenGovernmentLicence shortName includes version`() {
        val license =
            License.OpenGovernmentLicence(
                jurisdiction = "United Kingdom",
                version = "3.0",
                elementLicensed = "e",
                author = "a",
            )

        assertEquals("OGL v3.0", license.shortName)
    }
}
