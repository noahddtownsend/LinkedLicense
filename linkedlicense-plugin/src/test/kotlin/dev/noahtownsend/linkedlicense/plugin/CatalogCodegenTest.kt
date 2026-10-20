package dev.noahtownsend.linkedlicense.plugin

import kotlin.test.Test
import kotlin.test.assertEquals

class CatalogCodegenTest {
    @Test
    fun `renderGeneratedLicensesFile renders distinct entries`() {
        val entry1 = CatalogEntry.BuiltIn("License.Apache2(elementLicensed = \"Koin\", author = \"Arnaud Giuliani\")")
        val entry2 = CatalogEntry.BuiltIn("License.Apache2(elementLicensed = \"Koin\", author = \"Arnaud Giuliani\")")
        val rendered = renderGeneratedLicensesFile("com.example", listOf(entry1, entry2))
        val occurrences = rendered.lines().count { it.contains("License.Apache2") }
        assertEquals(1, occurrences)
    }

    @Test
    fun `renderGeneratedLicensesFile deduplicates by name and author across differing URLs`() {
        val entry1 = CatalogEntry.BuiltIn(
            expression = "License.Apache2(elementLicensed = \"okhttp\", author = \"Square, Inc.\", url = \"https://example.com\")",
            elementLicensed = "okhttp",
            author = "Square, Inc.",
        )
        val entry2 = CatalogEntry.BuiltIn(
            expression = "License.Apache2(elementLicensed = \"okhttp\", author = \"Square, Inc.\", url = \"http://example.com\")",
            elementLicensed = "okhttp",
            author = "Square, Inc.",
        )
        val rendered = renderGeneratedLicensesFile("com.example", listOf(entry1, entry2))
        val occurrences = rendered.lines().count { it.contains("License.Apache2") }
        assertEquals(1, occurrences)
    }
}
