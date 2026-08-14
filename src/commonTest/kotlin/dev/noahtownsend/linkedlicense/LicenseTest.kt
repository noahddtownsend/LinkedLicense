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
    fun `elementLicensed and author are exposed on the base type`() {
        val license = License.Apache2(elementLicensed = "Ktor", author = "JetBrains", url = "https://ktor.io")

        assertEquals("Ktor", license.elementLicensed)
        assertEquals("JetBrains", license.author)
        assertEquals("https://ktor.io", license.url)
    }
}
