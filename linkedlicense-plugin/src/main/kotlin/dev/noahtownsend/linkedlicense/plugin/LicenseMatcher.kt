package dev.noahtownsend.linkedlicense.plugin

import dev.noahtownsend.linkedlicense.License
import kotlin.reflect.KClass

/**
 * Matches a POM `<license><name>`/`<url>` pair against common SPDX identifiers and known
 * name variants, returning the matching built-in [License] subtype (or `null` if nothing
 * matches).
 */
object LicenseMatcher {
    private fun normalize(raw: String): String =
        raw
            .trim()
            .lowercase()
            .replace(Regex("\\s+"), " ")
            .removeSuffix(".")

    private val nameTable: Map<String, KClass<out License>> =
        mapOf(
            "apache-2.0" to License.Apache2::class,
            "apache 2.0" to License.Apache2::class,
            "apache license 2.0" to License.Apache2::class,
            "apache license, version 2.0" to License.Apache2::class,
            "apache license version 2.0" to License.Apache2::class,
            "the apache software license, version 2.0" to License.Apache2::class,
            "the apache license, version 2.0" to License.Apache2::class,
            "apache software license, version 2.0" to License.Apache2::class,
            "apache-1.1" to License.Apache1_1::class,
            "apache software license, version 1.1" to License.Apache1_1::class,
            "apache license, version 1.1" to License.Apache1_1::class,
            "mit" to License.MIT::class,
            "mit license" to License.MIT::class,
            "the mit license" to License.MIT::class,
            "the mit license (mit)" to License.MIT::class,
            "mit-0" to License.Mit0::class,
            "mit 0" to License.Mit0::class,
            "mit no attribution" to License.Mit0::class,
            "the mit no attribution license" to License.Mit0::class,
            "mit-0 license" to License.Mit0::class,
            "the mit-0 license" to License.Mit0::class,
            "bsd-2-clause" to License.Bsd2Clause::class,
            "bsd 2-clause \"simplified\" license" to License.Bsd2Clause::class,
            "the bsd 2-clause license" to License.Bsd2Clause::class,
            "bsd 2-clause license" to License.Bsd2Clause::class,
            "simplified bsd license" to License.Bsd2Clause::class,
            "bsd-3-clause" to License.Bsd3Clause::class,
            "bsd 3-clause \"new\" or \"revised\" license" to License.Bsd3Clause::class,
            "the bsd 3-clause license" to License.Bsd3Clause::class,
            "bsd 3-clause license" to License.Bsd3Clause::class,
            "new bsd license" to License.Bsd3Clause::class,
            "revised bsd license" to License.Bsd3Clause::class,
            "isc" to License.Isc::class,
            "isc license" to License.Isc::class,
            "the isc license" to License.Isc::class,
            "gpl-2.0" to License.Gpl2::class,
            "gpl-2.0-only" to License.Gpl2::class,
            "gpl-2.0-or-later" to License.Gpl2::class,
            "gnu general public license v2.0" to License.Gpl2::class,
            "gnu general public license, version 2" to License.Gpl2::class,
            "gnu general public license, version 2.0" to License.Gpl2::class,
            "gpl-3.0" to License.Gpl3::class,
            "gpl-3.0-only" to License.Gpl3::class,
            "gpl-3.0-or-later" to License.Gpl3::class,
            "gnu general public license v3.0" to License.Gpl3::class,
            "gnu general public license, version 3" to License.Gpl3::class,
            "gnu general public license, version 3.0" to License.Gpl3::class,
            "agpl-3.0" to License.Agpl3::class,
            "agpl-3.0-only" to License.Agpl3::class,
            "agpl-3.0-or-later" to License.Agpl3::class,
            "gnu agpl v3.0" to License.Agpl3::class,
            "gnu agpl v3" to License.Agpl3::class,
            "gnu affero general public license" to License.Agpl3::class,
            "gnu affero general public license v3.0" to License.Agpl3::class,
            "gnu affero general public license, version 3" to License.Agpl3::class,
            "gnu affero general public license, version 3.0" to License.Agpl3::class,
            "gnu affero general public license version 3.0" to License.Agpl3::class,
            "gnu affero general public license version 3" to License.Agpl3::class,
            "agplv3" to License.Agpl3::class,
            "agpl 3" to License.Agpl3::class,
            "agpl 3.0" to License.Agpl3::class,
            "agpl" to License.Agpl3::class,
            "lgpl-2.1" to License.Lgpl2_1::class,
            "lgpl-2.1-only" to License.Lgpl2_1::class,
            "lgpl-2.1-or-later" to License.Lgpl2_1::class,
            "gnu lesser general public license v2.1" to License.Lgpl2_1::class,
            "gnu lesser general public license, version 2.1" to License.Lgpl2_1::class,
            "lgpl-3.0" to License.Lgpl3::class,
            "lgpl-3.0-only" to License.Lgpl3::class,
            "lgpl-3.0-or-later" to License.Lgpl3::class,
            "gnu lesser general public license v3.0" to License.Lgpl3::class,
            "gnu lesser general public license, version 3" to License.Lgpl3::class,
            "gnu lesser general public license, version 3.0" to License.Lgpl3::class,
            "mpl-2.0" to License.Mpl2::class,
            "mozilla public license 2.0" to License.Mpl2::class,
            "mozilla public license, version 2.0" to License.Mpl2::class,
            "mozilla public license version 2.0" to License.Mpl2::class,
            "epl-1.0" to License.Epl1::class,
            "epl 1.0" to License.Epl1::class,
            "eclipse public license - v 1.0" to License.Epl1::class,
            "eclipse public license - v1.0" to License.Epl1::class,
            "eclipse public license 1.0" to License.Epl1::class,
            "eclipse public license, version 1.0" to License.Epl1::class,
            "eclipse public license version 1.0" to License.Epl1::class,
            "the eclipse public license 1.0" to License.Epl1::class,
            "the eclipse public license, version 1.0" to License.Epl1::class,
            "the eclipse public license version 1.0" to License.Epl1::class,
            "eclipse public license (epl) 1.0" to License.Epl1::class,
            "eclipse public license" to License.Epl1::class,
            "the eclipse public license" to License.Epl1::class,
            "epl" to License.Epl1::class,
            "cddl-1.0" to License.Cddl1::class,
            "cddl 1.0" to License.Cddl1::class,
            "cddl, version 1.0" to License.Cddl1::class,
            "cddl version 1.0" to License.Cddl1::class,
            "common development and distribution license 1.0" to License.Cddl1::class,
            "common development and distribution license (cddl) 1.0" to License.Cddl1::class,
            "the common development and distribution license 1.0" to License.Cddl1::class,
            "cddl" to License.Cddl1::class,
            "common development and distribution license" to License.Cddl1::class,
            "common development and distribution license (cddl)" to License.Cddl1::class,
            "cddl-1.1" to License.Cddl1_1::class,
            "cddl 1.1" to License.Cddl1_1::class,
            "cddl, version 1.1" to License.Cddl1_1::class,
            "cddl version 1.1" to License.Cddl1_1::class,
            "common development and distribution license 1.1" to License.Cddl1_1::class,
            "common development and distribution license (cddl) 1.1" to License.Cddl1_1::class,
            "the common development and distribution license 1.1" to License.Cddl1_1::class,
            "cddl+gpl" to License.Cddl1_1::class,
            "cddl+gpl license" to License.Cddl1_1::class,
            "cddl/gplv2+ce" to License.Cddl1_1::class,
            "cddl/gplv2 ce" to License.Cddl1_1::class,
            "cddl 1.1 / gplv2+ce" to License.Cddl1_1::class,
            "cddl + gplv2 with classpath exception" to License.Cddl1_1::class,
            "cddl 1.1 or gpl-2.0-with-classpath-exception" to License.Cddl1_1::class,
            "cddl-1.1 or gpl-2.0-with-classpath-exception" to License.Cddl1_1::class,
            "dual license consisting of the cddl v1.1 and gpl v2" to License.Cddl1_1::class,
            "dual license consisting of the cddl v1.0 and gpl v2" to License.Cddl1::class,
            "cddl or gplv2+ce" to License.Cddl1_1::class,
            "cddl 1.1 / gpl 2.0 with classpath exception" to License.Cddl1_1::class,
            "gpl-2.0-with-classpath-exception" to License.Gpl2::class,
            "gpl2 w/ cpe" to License.Gpl2::class,
            "gpl2 with classpath exception" to License.Gpl2::class,
            "gnu general public license, version 2 with the classpath exception" to License.Gpl2::class,
            "gnu general public license, version 2 with classpath exception" to License.Gpl2::class,
            "unlicense" to License.Unlicense::class,
            "the unlicense" to License.Unlicense::class,
            "unlicense (unlicense)" to License.Unlicense::class,
            "odbl-1.0" to License.Odbl::class,
            "odbl" to License.Odbl::class,
            "open data commons open database license" to License.Odbl::class,
            "open data commons open database license (odbl)" to License.Odbl::class,
            "open data commons open database license v1.0" to License.Odbl::class,
        )

    private val urlTable: List<Pair<Regex, KClass<out License>>> =
        listOf(
            Regex("apache\\.org/licenses/LICENSE-2\\.0", RegexOption.IGNORE_CASE) to License.Apache2::class,
            Regex("opensource\\.org/licenses/MIT-0", RegexOption.IGNORE_CASE) to License.Mit0::class,
            Regex("spdx\\.org/licenses/MIT-0", RegexOption.IGNORE_CASE) to License.Mit0::class,
            Regex("opensource\\.org/licenses/MIT", RegexOption.IGNORE_CASE) to License.MIT::class,
            Regex("opensource\\.org/licenses/BSD-2-Clause", RegexOption.IGNORE_CASE) to License.Bsd2Clause::class,
            Regex("opensource\\.org/licenses/BSD-3-Clause", RegexOption.IGNORE_CASE) to License.Bsd3Clause::class,
            Regex("opensource\\.org/licenses/ISC", RegexOption.IGNORE_CASE) to License.Isc::class,
            Regex("gnu\\.org/licenses/old-licenses/gpl-2\\.0", RegexOption.IGNORE_CASE) to License.Gpl2::class,
            Regex("gnu\\.org/licenses/gpl-3\\.0", RegexOption.IGNORE_CASE) to License.Gpl3::class,
            Regex("gnu\\.org/licenses/agpl-3\\.0", RegexOption.IGNORE_CASE) to License.Agpl3::class,
            Regex("opensource\\.org/licenses/AGPL-3\\.0", RegexOption.IGNORE_CASE) to License.Agpl3::class,
            Regex("spdx\\.org/licenses/AGPL-3\\.0", RegexOption.IGNORE_CASE) to License.Agpl3::class,
            Regex("gnu\\.org/licenses/old-licenses/lgpl-2\\.1", RegexOption.IGNORE_CASE) to License.Lgpl2_1::class,
            Regex("gnu\\.org/licenses/lgpl-3\\.0", RegexOption.IGNORE_CASE) to License.Lgpl3::class,
            Regex("mozilla\\.org.*MPL/2\\.0", RegexOption.IGNORE_CASE) to License.Mpl2::class,
            Regex("eclipse\\.org/legal/epl-v10", RegexOption.IGNORE_CASE) to License.Epl1::class,
            Regex("eclipse\\.org/org/documents/epl-v10", RegexOption.IGNORE_CASE) to License.Epl1::class,
            Regex("opensource\\.org/licenses/EPL-1\\.0", RegexOption.IGNORE_CASE) to License.Epl1::class,
            Regex("spdx\\.org/licenses/EPL-1\\.0", RegexOption.IGNORE_CASE) to License.Epl1::class,
            Regex("spdx\\.org/licenses/CDDL-1\\.1", RegexOption.IGNORE_CASE) to License.Cddl1_1::class,
            Regex("glassfish.*CDDL.*GPL", RegexOption.IGNORE_CASE) to License.Cddl1_1::class,
            Regex("oracle.*CDDL.*GPL", RegexOption.IGNORE_CASE) to License.Cddl1_1::class,
            Regex("opensource\\.org/licenses/CDDL-1\\.0", RegexOption.IGNORE_CASE) to License.Cddl1::class,
            Regex("spdx\\.org/licenses/CDDL-1\\.0", RegexOption.IGNORE_CASE) to License.Cddl1::class,
            Regex("sun\\.com/cddl", RegexOption.IGNORE_CASE) to License.Cddl1::class,
            Regex("unlicense\\.org", RegexOption.IGNORE_CASE) to License.Unlicense::class,
            Regex("opendatacommons\\.org/licenses/odbl", RegexOption.IGNORE_CASE) to License.Odbl::class,
        )

    fun match(
        name: String?,
        url: String? = null,
    ): KClass<out License>? {
        if (!name.isNullOrBlank()) {
            val normalized = normalize(name)
            nameTable[normalized]?.let { return it }

            // Handle disjunctive expressions like "Apache-2.0 or MIT", "CDDL 1.1 / GPL-2.0"
            if (" or " in normalized || " / " in normalized || " | " in normalized) {
                val parts = normalized.split(Regex("\\s+(?:or|\\/|\\|)\\s+"))
                if (parts.size > 1) {
                    val matchedParts = parts.mapNotNull { nameTable[it] }
                    if (matchedParts.isNotEmpty()) {
                        // Pick the least restrictive license (NONE < WEAK < STRONG)
                        return matchedParts.minByOrNull { BuiltInLicenses.copyleftStrength(it).ordinal }
                    }
                }
            }
        }

        if (!url.isNullOrBlank()) {
            for ((regex, kClass) in urlTable) {
                if (regex.containsMatchIn(url)) {
                    return kClass
                }
            }
        }

        return null
    }
}
