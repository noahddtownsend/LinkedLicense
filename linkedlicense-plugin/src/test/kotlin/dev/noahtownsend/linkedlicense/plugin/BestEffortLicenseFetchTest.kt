package dev.noahtownsend.linkedlicense.plugin

import dev.noahtownsend.linkedlicense.License
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class LicenseTextMatcherTest {
    @Test
    fun `match() recognizes canonical MIT license text`() {
        val text =
            """
            MIT License

            Copyright (c) 2024 Jane Doe

            Permission is hereby granted, free of charge, to any person obtaining a copy
            of this software and associated documentation files (the "Software"), to deal ...

            THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
            IMPLIED ...
            """.trimIndent()

        assertEquals(License.MIT::class, LicenseTextMatcher.match(text))
    }

    @Test
    fun `match() recognizes MIT-0 license text`() {
        val text =
            """
            MIT No Attribution

            Copyright 2024 Jane Doe

            Permission is hereby granted, free of charge, to any person obtaining a copy
            of this software and associated documentation files (the "Software"), to deal ...

            THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
            IMPLIED ...
            """.trimIndent()

        assertEquals(License.Mit0::class, LicenseTextMatcher.match(text))
    }

    @Test
    fun `match() recognizes Eclipse Public License 1_0 text`() {
        val text =
            """
            Eclipse Public License - v 1.0

            THE ACCOMPANYING PROGRAM IS PROVIDED UNDER THE TERMS OF THIS ECLIPSE PUBLIC LICENSE ("AGREEMENT").
            ANY USE, REPRODUCTION OR DISTRIBUTION OF THE PROGRAM CONSTITUTES RECIPIENT'S ACCEPTANCE OF THIS AGREEMENT.
            """.trimIndent()

        assertEquals(License.Epl1::class, LicenseTextMatcher.match(text))
    }

    @Test
    fun `match() recognizes AGPL 3_0 text`() {
        val text =
            """
            GNU AFFERO GENERAL PUBLIC LICENSE
            Version 3, 19 November 2007

            Copyright (C) 2007 Free Software Foundation, Inc. <https://fsf.org/>
            Everyone is permitted to copy and distribute verbatim copies
            """.trimIndent()

        assertEquals(License.Agpl3::class, LicenseTextMatcher.match(text))
    }

    @Test
    fun `match() recognizes CDDL 1_1 text`() {
        val text =
            """
            COMMON DEVELOPMENT AND DISTRIBUTION LICENSE (CDDL)
            Version 1.1

            1. Definitions.
            1.1. "Contributor" means each individual or entity that creates or contributes to the creation of Modifications.
            """.trimIndent()

        assertEquals(License.Cddl1_1::class, LicenseTextMatcher.match(text))
    }

    @Test
    fun `match() recognizes CDDL 1_0 text`() {
        val text =
            """
            COMMON DEVELOPMENT AND DISTRIBUTION LICENSE (CDDL)
            Version 1.0

            1. Definitions.
            1.1. "Contributor" means each individual or entity that creates or contributes to the creation of Modifications.
            """.trimIndent()

        assertEquals(License.Cddl1::class, LicenseTextMatcher.match(text))
    }

    @Test
    fun `match() recognizes canonical Apache 2 license text`() {
        val text =
            """
            Apache License
            Version 2.0, January 2004
            http://www.apache.org/licenses/

            ...

            Licensed under the Apache License, Version 2.0 (the "License");
            """.trimIndent()

        assertEquals(License.Apache2::class, LicenseTextMatcher.match(text))
    }

    @Test
    fun `match() distinguishes Bsd3Clause from Bsd2Clause via the endorsement clause`() {
        val bsd3 =
            """
            Redistribution and use in source and binary forms, with or without
            modification, are permitted provided that the following conditions are met:

            Neither the name of the copyright holder nor the names of its contributors
            may be used to endorse or promote products derived from this software
            without specific prior written permission.
            """.trimIndent()

        val bsd2 =
            """
            Redistribution and use in source and binary forms, with or without
            modification, are permitted provided that the following conditions are met:

            1. Redistributions of source code must retain the above copyright notice.
            """.trimIndent()

        assertEquals(License.Bsd3Clause::class, LicenseTextMatcher.match(bsd3))
        assertEquals(License.Bsd2Clause::class, LicenseTextMatcher.match(bsd2))
    }

    @Test
    fun `match() recognizes 0BSD license text`() {
        val text =
            """
            BSD Zero Clause License

            Copyright (C) 2024 Jane Doe

            Permission to use, copy, modify, and/or distribute this software for any purpose with or without fee is hereby granted.
            """.trimIndent()

        assertEquals(License.Bsd0Clause::class, LicenseTextMatcher.match(text))
    }

    @Test
    fun `match() recognizes MPL 1_1 and MPL 1_0 text`() {
        val mpl1_1 = "Mozilla Public License Version 1.1\n1. Definitions..."
        val mpl1_0 = "Mozilla Public License Version 1.0\n1. Definitions..."

        assertEquals(License.Mpl1_1::class, LicenseTextMatcher.match(mpl1_1))
        assertEquals(License.Mpl1::class, LicenseTextMatcher.match(mpl1_0))
    }

    @Test
    fun `match() recognizes EPL 2_0 text`() {
        val text = "Eclipse Public License - v 2.0\nTHE ACCOMPANYING PROGRAM IS PROVIDED UNDER THE TERMS..."

        assertEquals(License.Epl2::class, LicenseTextMatcher.match(text))
    }

    @Test
    fun `match() recognizes EDL 1_0 text`() {
        val text = "Eclipse Distribution License - v 1.0\nCopyright (c) 2007, Eclipse Foundation, Inc."

        assertEquals(License.Edl1::class, LicenseTextMatcher.match(text))
    }

    @Test
    fun `match() recognizes Boost Software License text`() {
        val text = "Boost Software License - Version 1.0 - August 17th, 2003\nPermission is hereby granted..."

        assertEquals(License.Bsl1::class, LicenseTextMatcher.match(text))
    }

    @Test
    fun `match() recognizes Zlib license text`() {
        val text = "This software is provided 'as-is', without any express or implied warranty.\n" +
            "Altered source versions must be plainly marked as such..."

        assertEquals(License.Zlib::class, LicenseTextMatcher.match(text))
    }

    @Test
    fun `match() recognizes MS-PL and MS-RL text`() {
        val msPl = "Microsoft Public License (MS-PL)\nThis license governs use of the accompanying software..."
        val msRl = "Microsoft Reciprocal License (MS-RL)\nThis license governs use of the accompanying software..."

        assertEquals(License.MsPl::class, LicenseTextMatcher.match(msPl))
        assertEquals(License.MsRl::class, LicenseTextMatcher.match(msRl))
    }

    @Test
    fun `match() recognizes Amazon Software License text`() {
        val text = "Amazon Software License\n1. Definitions...\n3.3 Use Limitation. The Work and any derivative works..."

        assertEquals(License.AmazonSoftwareLicense::class, LicenseTextMatcher.match(text))
    }

    @Test
    fun `match() recognizes Embrace license text`() {
        val text = "Embrace Software Notice\nTerms and conditions for Embrace Android SDK"

        assertEquals(License.Embrace::class, LicenseTextMatcher.match(text))
    }

    @Test
    fun `match() recognizes Firebase and Android SDK text`() {
        val firebase = "Firebase Software Development Kit Terms\nhttps://firebase.google.com/terms/"
        val androidSdk = "Android Software Development Kit License Agreement\nhttps://developer.android.com/studio/terms.html"

        assertEquals(License.Firebase::class, LicenseTextMatcher.match(firebase))
        assertEquals(License.AndroidSdk::class, LicenseTextMatcher.match(androidSdk))
    }

    @Test
    fun `match() returns null for unrecognized text`() {
        assertNull(LicenseTextMatcher.match("This is a completely bespoke internal license agreement."))
    }
}

class BestEffortLicenseFetchTest {
    @Test
    fun `candidateRawUrls() builds raw githubusercontent urls for a github repo`() {
        val urls = BestEffortLicenseFetch.candidateRawUrls("https://github.com/apple/swift-log", "1.5.3")

        assertEquals(
            "https://raw.githubusercontent.com/apple/swift-log/1.5.3/LICENSE",
            urls?.first(),
        )
    }

    @Test
    fun `candidateRawUrls() strips a trailing dot-git suffix`() {
        val urls = BestEffortLicenseFetch.candidateRawUrls("https://github.com/apple/swift-log.git", "1.5.3")

        assertEquals(
            "https://raw.githubusercontent.com/apple/swift-log/1.5.3/LICENSE",
            urls?.first(),
        )
    }

    @Test
    fun `candidateRawUrls() returns null for a non-github url`() {
        assertNull(BestEffortLicenseFetch.candidateRawUrls("https://gitlab.com/foo/bar", "1.0.0"))
    }

    @Test
    fun `guessLicense() returns the matched license type using an injected fetch function`() {
        val mitText = "Permission is hereby granted, free of charge, to any person obtaining a copy ... " +
            "the software is provided \"as is\", without warranty of any kind, express or implied."

        val result =
            BestEffortLicenseFetch.guessLicense(
                repoUrl = "https://github.com/example/foo",
                ref = "1.0.0",
                fetch = { url -> if (url.endsWith("/LICENSE")) mitText else null },
            )

        assertEquals(License.MIT::class, result)
    }

    @Test
    fun `guessLicense() tries the next candidate file name when the first is not found`() {
        val mitText = "Permission is hereby granted, free of charge, to any person obtaining a copy ... " +
            "the software is provided \"as is\", without warranty of any kind, express or implied."

        val result =
            BestEffortLicenseFetch.guessLicense(
                repoUrl = "https://github.com/example/foo",
                ref = "1.0.0",
                fetch = { url -> if (url.endsWith("/LICENSE.md")) mitText else null },
            )

        assertEquals(License.MIT::class, result)
    }

    @Test
    fun `guessLicense() returns null when nothing can be fetched`() {
        val result = BestEffortLicenseFetch.guessLicense("https://github.com/example/foo", "1.0.0") { null }

        assertNull(result)
    }

    @Test
    fun `guessLicense() returns null for a non-github repo url without invoking fetch`() {
        var fetchCalled = false

        val result =
            BestEffortLicenseFetch.guessLicense("https://gitlab.com/example/foo", "1.0.0") {
                fetchCalled = true
                null
            }

        assertNull(result)
        assertEquals(false, fetchCalled)
    }
}
