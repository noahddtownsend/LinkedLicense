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
    fun `match() returns Mit0 for common MIT-0 name variants and URL`() {
        assertEquals(License.Mit0::class, LicenseMatcher.match(name = "MIT-0"))
        assertEquals(License.Mit0::class, LicenseMatcher.match(name = "MIT No Attribution"))
        assertEquals(License.Mit0::class, LicenseMatcher.match(name = "The MIT-0 License"))
        assertEquals(License.Mit0::class, LicenseMatcher.match(name = "Unlisted", url = "https://opensource.org/licenses/MIT-0"))
        assertEquals(License.Mit0::class, LicenseMatcher.match(name = "Unlisted", url = "https://spdx.org/licenses/MIT-0.html"))
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
    fun `match() returns Epl1 for Eclipse Public License 1_0 name variants and URL`() {
        assertEquals(License.Epl1::class, LicenseMatcher.match(name = "EPL-1.0"))
        assertEquals(License.Epl1::class, LicenseMatcher.match(name = "Eclipse Public License - v 1.0"))
        assertEquals(License.Epl1::class, LicenseMatcher.match(name = "Eclipse Public License 1.0"))
        assertEquals(License.Epl1::class, LicenseMatcher.match(name = "Eclipse Public License, Version 1.0"))
        assertEquals(License.Epl1::class, LicenseMatcher.match(name = "The Eclipse Public License 1.0"))
        assertEquals(License.Epl1::class, LicenseMatcher.match(name = "EPL"))
        assertEquals(License.Epl1::class, LicenseMatcher.match(name = "Unlisted", url = "https://www.eclipse.org/legal/epl-v10.html"))
        assertEquals(License.Epl1::class, LicenseMatcher.match(name = "Unlisted", url = "https://opensource.org/licenses/EPL-1.0"))
    }

    @Test
    fun `match() returns Agpl3 for AGPL 3 name variants and URLs`() {
        assertEquals(License.Agpl3::class, LicenseMatcher.match(name = "AGPL-3.0"))
        assertEquals(License.Agpl3::class, LicenseMatcher.match(name = "GNU Affero General Public License v3.0"))
        assertEquals(License.Agpl3::class, LicenseMatcher.match(name = "AGPLv3"))
        assertEquals(License.Agpl3::class, LicenseMatcher.match(name = "Unlisted", url = "https://www.gnu.org/licenses/agpl-3.0.html"))
        assertEquals(License.Agpl3::class, LicenseMatcher.match(name = "Unlisted", url = "https://spdx.org/licenses/AGPL-3.0.html"))
    }

    @Test
    fun `match() returns Cddl1 and Cddl1_1 for CDDL name variants and URLs`() {
        assertEquals(License.Cddl1::class, LicenseMatcher.match(name = "CDDL-1.0"))
        assertEquals(License.Cddl1::class, LicenseMatcher.match(name = "CDDL 1.0"))
        assertEquals(License.Cddl1::class, LicenseMatcher.match(name = "Common Development and Distribution License 1.0"))
        assertEquals(License.Cddl1::class, LicenseMatcher.match(name = "CDDL"))
        assertEquals(License.Cddl1::class, LicenseMatcher.match(name = "Unlisted", url = "https://opensource.org/licenses/CDDL-1.0"))

        assertEquals(License.Cddl1_1::class, LicenseMatcher.match(name = "CDDL-1.1"))
        assertEquals(License.Cddl1_1::class, LicenseMatcher.match(name = "CDDL 1.1"))
        assertEquals(License.Cddl1_1::class, LicenseMatcher.match(name = "Common Development and Distribution License 1.1"))
        assertEquals(License.Cddl1_1::class, LicenseMatcher.match(name = "Unlisted", url = "https://spdx.org/licenses/CDDL-1.1.html"))
    }

    @Test
    fun `match() returns Cddl1_1 for CDDL and GPL dual license variants`() {
        assertEquals(License.Cddl1_1::class, LicenseMatcher.match(name = "CDDL+GPL License"))
        assertEquals(License.Cddl1_1::class, LicenseMatcher.match(name = "CDDL/GPLv2+CE"))
        assertEquals(License.Cddl1_1::class, LicenseMatcher.match(name = "CDDL 1.1 / GPLv2+CE"))
        assertEquals(License.Cddl1_1::class, LicenseMatcher.match(name = "Dual license consisting of the CDDL v1.1 and GPL v2"))
        assertEquals(License.Cddl1_1::class, LicenseMatcher.match(name = "Unlisted", url = "https://glassfish.java.net/public/CDDL+GPL_1_1.html"))
    }

    @Test
    fun `match() picks the least restrictive license in disjunctive OR expressions`() {
        assertEquals(License.Apache2::class, LicenseMatcher.match(name = "Apache-2.0 or GPL-3.0"))
        assertEquals(License.MIT::class, LicenseMatcher.match(name = "GPL-2.0 or MIT"))
        assertEquals(License.Cddl1_1::class, LicenseMatcher.match(name = "CDDL-1.1 or GPL-3.0"))
        assertEquals(License.MIT::class, LicenseMatcher.match(name = "MIT / Apache-2.0"))
    }

    @Test
    fun `match() is case-insensitive and tolerates extra whitespace`() {
        assertEquals(License.Apache2::class, LicenseMatcher.match(name = "  apache-2.0  "))
        assertEquals(License.MIT::class, LicenseMatcher.match(name = "mit   license"))
    }

    @Test
    fun `match() returns Bsd0Clause for 0BSD name variants and URL`() {
        assertEquals(License.Bsd0Clause::class, LicenseMatcher.match(name = "0BSD"))
        assertEquals(License.Bsd0Clause::class, LicenseMatcher.match(name = "BSD-0-Clause"))
        assertEquals(License.Bsd0Clause::class, LicenseMatcher.match(name = "BSD Zero Clause License"))
        assertEquals(License.Bsd0Clause::class, LicenseMatcher.match(name = "Unlisted", url = "https://opensource.org/licenses/0BSD"))
        assertEquals(License.Bsd0Clause::class, LicenseMatcher.match(name = "Unlisted", url = "https://spdx.org/licenses/0BSD.html"))
    }

    @Test
    fun `match() returns Mpl1 and Mpl1_1 for MPL 1_0 and MPL 1_1 name variants and URLs`() {
        assertEquals(License.Mpl1::class, LicenseMatcher.match(name = "MPL-1.0"))
        assertEquals(License.Mpl1::class, LicenseMatcher.match(name = "Mozilla Public License 1.0"))
        assertEquals(License.Mpl1::class, LicenseMatcher.match(name = "Unlisted", url = "https://www.mozilla.org/MPL/1.0/"))
        assertEquals(License.Mpl1::class, LicenseMatcher.match(name = "Unlisted", url = "https://spdx.org/licenses/MPL-1.0.html"))

        assertEquals(License.Mpl1_1::class, LicenseMatcher.match(name = "MPL-1.1"))
        assertEquals(License.Mpl1_1::class, LicenseMatcher.match(name = "Mozilla Public License 1.1"))
        assertEquals(License.Mpl1_1::class, LicenseMatcher.match(name = "Mozilla Public License (MPL) 1.1"))
        assertEquals(License.Mpl1_1::class, LicenseMatcher.match(name = "Unlisted", url = "https://www.mozilla.org/MPL/1.1/"))
        assertEquals(License.Mpl1_1::class, LicenseMatcher.match(name = "Unlisted", url = "https://spdx.org/licenses/MPL-1.1.html"))
    }

    @Test
    fun `match() returns Epl2 for Eclipse Public License 2_0 name variants and URL`() {
        assertEquals(License.Epl2::class, LicenseMatcher.match(name = "EPL-2.0"))
        assertEquals(License.Epl2::class, LicenseMatcher.match(name = "Eclipse Public License - v 2.0"))
        assertEquals(License.Epl2::class, LicenseMatcher.match(name = "Eclipse Public License 2.0"))
        assertEquals(License.Epl2::class, LicenseMatcher.match(name = "Unlisted", url = "https://www.eclipse.org/legal/epl-2.0/"))
        assertEquals(License.Epl2::class, LicenseMatcher.match(name = "Unlisted", url = "https://spdx.org/licenses/EPL-2.0.html"))
    }

    @Test
    fun `match() returns Edl1 for Eclipse Distribution License 1_0 name variants and URL`() {
        assertEquals(License.Edl1::class, LicenseMatcher.match(name = "EDL-1.0"))
        assertEquals(License.Edl1::class, LicenseMatcher.match(name = "Eclipse Distribution License - v 1.0"))
        assertEquals(License.Edl1::class, LicenseMatcher.match(name = "EDL"))
        assertEquals(License.Edl1::class, LicenseMatcher.match(name = "Unlisted", url = "https://www.eclipse.org/org/documents/edl-v10.php"))
        assertEquals(License.Edl1::class, LicenseMatcher.match(name = "Unlisted", url = "https://spdx.org/licenses/EDL-1.0.html"))
    }

    @Test
    fun `match() returns Bsl1 for Boost Software License name variants and URL`() {
        assertEquals(License.Bsl1::class, LicenseMatcher.match(name = "BSL-1.0"))
        assertEquals(License.Bsl1::class, LicenseMatcher.match(name = "Boost Software License"))
        assertEquals(License.Bsl1::class, LicenseMatcher.match(name = "Boost Software License 1.0"))
        assertEquals(License.Bsl1::class, LicenseMatcher.match(name = "Unlisted", url = "https://www.boost.org/LICENSE_1_0.txt"))
        assertEquals(License.Bsl1::class, LicenseMatcher.match(name = "Unlisted", url = "https://spdx.org/licenses/BSL-1.0.html"))
    }

    @Test
    fun `match() returns Zlib for zlib name variants and URL`() {
        assertEquals(License.Zlib::class, LicenseMatcher.match(name = "Zlib"))
        assertEquals(License.Zlib::class, LicenseMatcher.match(name = "zlib license"))
        assertEquals(License.Zlib::class, LicenseMatcher.match(name = "The zlib/libpng License"))
        assertEquals(License.Zlib::class, LicenseMatcher.match(name = "Unlisted", url = "https://zlib.net/zlib_license.html"))
        assertEquals(License.Zlib::class, LicenseMatcher.match(name = "Unlisted", url = "https://spdx.org/licenses/Zlib.html"))
    }

    @Test
    fun `match() returns MsPl and MsRl for Microsoft public and reciprocal licenses`() {
        assertEquals(License.MsPl::class, LicenseMatcher.match(name = "MS-PL"))
        assertEquals(License.MsPl::class, LicenseMatcher.match(name = "Microsoft Public License"))
        assertEquals(License.MsPl::class, LicenseMatcher.match(name = "Unlisted", url = "https://opensource.org/licenses/MS-PL"))

        assertEquals(License.MsRl::class, LicenseMatcher.match(name = "MS-RL"))
        assertEquals(License.MsRl::class, LicenseMatcher.match(name = "Microsoft Reciprocal License"))
        assertEquals(License.MsRl::class, LicenseMatcher.match(name = "Unlisted", url = "https://opensource.org/licenses/MS-RL"))
    }

    @Test
    fun `match() returns AmazonSoftwareLicense for ASL name variants and URL`() {
        assertEquals(License.AmazonSoftwareLicense::class, LicenseMatcher.match(name = "Amazon Software License"))
        assertEquals(License.AmazonSoftwareLicense::class, LicenseMatcher.match(name = "Amazon Software License 1.0"))
        assertEquals(License.AmazonSoftwareLicense::class, LicenseMatcher.match(name = "amazon-sl"))
        assertEquals(License.AmazonSoftwareLicense::class, LicenseMatcher.match(name = "ASL"))
        assertEquals(License.AmazonSoftwareLicense::class, LicenseMatcher.match(name = "Unlisted", url = "https://aws.amazon.com/asl/"))
    }

    @Test
    fun `match() returns Embrace for Embrace name variants and URL`() {
        assertEquals(License.Embrace::class, LicenseMatcher.match(name = "Embrace License"))
        assertEquals(License.Embrace::class, LicenseMatcher.match(name = "Embrace Software License"))
        assertEquals(License.Embrace::class, LicenseMatcher.match(name = "Embrace"))
        assertEquals(License.Embrace::class, LicenseMatcher.match(name = "Unlisted", url = "https://embrace.io/docs/embrace-software-notice/"))
        assertEquals(License.Embrace::class, LicenseMatcher.match(name = "Unlisted", url = "https://embrace.io/docs/terms-of-service/"))
    }

    @Test
    fun `match() returns Firebase for Firebase terms name variants and URL`() {
        assertEquals(License.Firebase::class, LicenseMatcher.match(name = "Firebase Terms"))
        assertEquals(License.Firebase::class, LicenseMatcher.match(name = "Firebase Terms of Service"))
        assertEquals(License.Firebase::class, LicenseMatcher.match(name = "Firebase SDK Terms of Service"))
        assertEquals(License.Firebase::class, LicenseMatcher.match(name = "Unlisted", url = "https://firebase.google.com/terms/"))
    }

    @Test
    fun `match() returns AndroidSdk for Android SDK license name variants and URL`() {
        assertEquals(License.AndroidSdk::class, LicenseMatcher.match(name = "Android Software Development Kit License"))
        assertEquals(License.AndroidSdk::class, LicenseMatcher.match(name = "Android Software Development Kit License Agreement"))
        assertEquals(License.AndroidSdk::class, LicenseMatcher.match(name = "Android Software Development License Agreement"))
        assertEquals(License.AndroidSdk::class, LicenseMatcher.match(name = "Android SDK License"))
        assertEquals(License.AndroidSdk::class, LicenseMatcher.match(name = "Android SDK"))
        assertEquals(License.AndroidSdk::class, LicenseMatcher.match(name = "Google Play Services License"))
        assertEquals(License.AndroidSdk::class, LicenseMatcher.match(name = "Unlisted", url = "https://developer.android.com/studio/terms.html"))
        assertEquals(License.AndroidSdk::class, LicenseMatcher.match(name = "Unlisted", url = "https://developer.android.com/sdk/terms.html"))
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
