package dev.noahtownsend.linkedlicense.plugin

import dev.noahtownsend.linkedlicense.License
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class LicenseMatcherTest {
    @Test
    fun `match() returns Apache2 for the common Apache 2 name variants`() {
        assertEquals(License.Apache2::class, LicenseMatcher.match(name = "Apache-2.0"))
        assertEquals(License.Apache2::class, LicenseMatcher.match(name = "The Apache Software License, Version 2.0"))
        assertEquals(License.Apache2::class, LicenseMatcher.match(name = "Apache License 2.0"))
    }

    @Test
    fun `match() returns Apache2 for the Apache 2 license URL when name is unrecognized`() {
        val result = LicenseMatcher.match(name = "Some Unlisted Name", url = "https://www.apache.org/licenses/LICENSE-2.0")

        assertEquals(License.Apache2::class, result)
    }

    @Test
    fun `match() returns MIT for common MIT name variants`() {
        assertEquals(License.MIT::class, LicenseMatcher.match(name = "MIT"))
        assertEquals(License.MIT::class, LicenseMatcher.match(name = "MIT License"))
        assertEquals(License.MIT::class, LicenseMatcher.match(name = "The MIT License"))
    }

    @Test
    fun `match() returns Bsd2Clause and Bsd3Clause for their respective name variants`() {
        assertEquals(License.Bsd2Clause::class, LicenseMatcher.match(name = "BSD-2-Clause"))
        assertEquals(License.Bsd2Clause::class, LicenseMatcher.match(name = "The BSD 2-Clause License"))
        assertEquals(License.Bsd3Clause::class, LicenseMatcher.match(name = "BSD-3-Clause"))
        assertEquals(License.Bsd3Clause::class, LicenseMatcher.match(name = "New BSD License"))
    }

    @Test
    fun `match() returns Isc for ISC name variants`() {
        assertEquals(License.Isc::class, LicenseMatcher.match(name = "ISC"))
        assertEquals(License.Isc::class, LicenseMatcher.match(name = "ISC License"))
    }

    @Test
    fun `match() returns the correct GPL family type per version`() {
        assertEquals(License.Gpl2::class, LicenseMatcher.match(name = "GNU General Public License v2.0"))
        assertEquals(License.Gpl3::class, LicenseMatcher.match(name = "GNU General Public License v3.0"))
        assertEquals(License.Lgpl2_1::class, LicenseMatcher.match(name = "GNU Lesser General Public License v2.1"))
        assertEquals(License.Lgpl3::class, LicenseMatcher.match(name = "GNU Lesser General Public License v3.0"))
    }

    @Test
    fun `match() returns Mpl2 for Mozilla Public License 2 name variants`() {
        assertEquals(License.Mpl2::class, LicenseMatcher.match(name = "MPL-2.0"))
        assertEquals(License.Mpl2::class, LicenseMatcher.match(name = "Mozilla Public License 2.0"))
    }

    @Test
    fun `match() is case-insensitive and tolerates extra whitespace`() {
        assertEquals(License.Apache2::class, LicenseMatcher.match(name = "  apache-2.0  "))
        assertEquals(License.MIT::class, LicenseMatcher.match(name = "mit   license"))
    }

    @Test
    fun `match() returns null for an unrecognized name and url`() {
        assertNull(LicenseMatcher.match(name = "Some Bespoke License", url = "https://example.com/license"))
    }

    @Test
    fun `match() returns null when name and url are both null`() {
        assertNull(LicenseMatcher.match(name = null, url = null))
    }
}
