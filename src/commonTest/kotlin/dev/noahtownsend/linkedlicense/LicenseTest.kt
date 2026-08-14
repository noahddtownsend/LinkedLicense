package dev.noahtownsend.linkedlicense

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
    fun `elementLicensed and author are exposed on the base type`() {
        val license = License.Apache2(elementLicensed = "Ktor", author = "JetBrains", url = "https://ktor.io")

        assertEquals("Ktor", license.elementLicensed)
        assertEquals("JetBrains", license.author)
        assertEquals("https://ktor.io", license.url)
    }
}
