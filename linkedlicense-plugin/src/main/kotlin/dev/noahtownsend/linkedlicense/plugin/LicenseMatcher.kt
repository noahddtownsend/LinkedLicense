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
            "unlicense" to License.Unlicense::class,
            "the unlicense" to License.Unlicense::class,
            "unlicense (unlicense)" to License.Unlicense::class,
        )

    private val urlTable: List<Pair<Regex, KClass<out License>>> =
        listOf(
            Regex("apache\\.org/licenses/LICENSE-2\\.0", RegexOption.IGNORE_CASE) to License.Apache2::class,
            Regex("opensource\\.org/licenses/MIT", RegexOption.IGNORE_CASE) to License.MIT::class,
            Regex("opensource\\.org/licenses/BSD-2-Clause", RegexOption.IGNORE_CASE) to License.Bsd2Clause::class,
            Regex("opensource\\.org/licenses/BSD-3-Clause", RegexOption.IGNORE_CASE) to License.Bsd3Clause::class,
            Regex("opensource\\.org/licenses/ISC", RegexOption.IGNORE_CASE) to License.Isc::class,
            Regex("gnu\\.org/licenses/old-licenses/gpl-2\\.0", RegexOption.IGNORE_CASE) to License.Gpl2::class,
            Regex("gnu\\.org/licenses/gpl-3\\.0", RegexOption.IGNORE_CASE) to License.Gpl3::class,
            Regex("gnu\\.org/licenses/old-licenses/lgpl-2\\.1", RegexOption.IGNORE_CASE) to License.Lgpl2_1::class,
            Regex("gnu\\.org/licenses/lgpl-3\\.0", RegexOption.IGNORE_CASE) to License.Lgpl3::class,
            Regex("mozilla\\.org.*MPL/2\\.0", RegexOption.IGNORE_CASE) to License.Mpl2::class,
            Regex("unlicense\\.org", RegexOption.IGNORE_CASE) to License.Unlicense::class,
        )

    fun match(
        name: String?,
        url: String? = null,
    ): KClass<out License>? {
        if (!name.isNullOrBlank()) {
            nameTable[normalize(name)]?.let { return it }
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
