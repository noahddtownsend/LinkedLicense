package dev.noahtownsend.linkedlicense.plugin

import kotlin.test.Test
import kotlin.test.assertEquals

class NoticeCollectorTest {
    @Test
    fun `stripLicenseBoilerplate leaves plain notice untouched`() {
        val notice = "Copyright 2024 Example Inc.\nAll rights reserved."
        assertEquals(notice, stripLicenseBoilerplate(notice))
    }

    @Test
    fun `stripLicenseBoilerplate strips trailing Apache 2 license text`() {
        val raw =
            """
            Futures Kotlin Extensions
            Copyright 2020-2024 The Android Open Source Project

            This product includes software developed by The Android Open Source Project.

            *************************************************************************
            kotlinx.coroutines library.
            Copyright 2016-2024 JetBrains s.r.o and contributors

            =========================================================================
            Apache License
            Version 2.0, January 2004
            http://www.apache.org/licenses/

            TERMS AND CONDITIONS FOR USE, REPRODUCTION, AND DISTRIBUTION
            """.trimIndent()

        val expected =
            """
            Futures Kotlin Extensions
            Copyright 2020-2024 The Android Open Source Project

            This product includes software developed by The Android Open Source Project.

            *************************************************************************
            kotlinx.coroutines library.
            Copyright 2016-2024 JetBrains s.r.o and contributors
            """.trimIndent()

        assertEquals(expected, stripLicenseBoilerplate(raw))
    }

    @Test
    fun `stripLicenseBoilerplate strips pure license text with no preceding attribution`() {
        val raw =
            """
            Apache License
            Version 2.0, January 2004
            http://www.apache.org/licenses/

            TERMS AND CONDITIONS FOR USE, REPRODUCTION, AND DISTRIBUTION
            """.trimIndent()

        assertEquals("", stripLicenseBoilerplate(raw))
    }
}
