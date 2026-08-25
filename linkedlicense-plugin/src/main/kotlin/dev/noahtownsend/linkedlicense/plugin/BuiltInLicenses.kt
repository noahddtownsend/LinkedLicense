package dev.noahtownsend.linkedlicense.plugin

import dev.noahtownsend.linkedlicense.License
import kotlin.reflect.KClass

/**
 * Registry of built-in [License] subtypes addressable by their simple name (as used in
 * `linkedlicense.toml`'s `license = "Apache2"` overrides), plus copyleft classification and
 * codegen support.
 */
object BuiltInLicenses {
    private val bySimpleName: Map<String, KClass<out License>> =
        mapOf(
            "MIT" to License.MIT::class,
            "Mit0" to License.Mit0::class,
            "Apache1_1" to License.Apache1_1::class,
            "Apache2" to License.Apache2::class,
            "Bsd2Clause" to License.Bsd2Clause::class,
            "Bsd3Clause" to License.Bsd3Clause::class,
            "Isc" to License.Isc::class,
            "Gpl2" to License.Gpl2::class,
            "Gpl3" to License.Gpl3::class,
            "Agpl3" to License.Agpl3::class,
            "AGPL3" to License.Agpl3::class,
            "Lgpl2_1" to License.Lgpl2_1::class,
            "Lgpl3" to License.Lgpl3::class,
            "Mpl2" to License.Mpl2::class,
            "Epl1" to License.Epl1::class,
            "Cddl1" to License.Cddl1::class,
            "Cddl1_0" to License.Cddl1::class,
            "CDDL1" to License.Cddl1::class,
            "Cddl1_1" to License.Cddl1_1::class,
            "CDDL1_1" to License.Cddl1_1::class,
            "Unlicense" to License.Unlicense::class,
            "Cc0" to License.Cc0::class,
            "Ofl" to License.Ofl::class,
            "PublicDomain" to License.PublicDomain::class,
            "UsGovernmentPublicDomain" to License.UsGovernmentPublicDomain::class,
            "CopyrightExpired" to License.CopyrightExpired::class,
            "Odbl" to License.Odbl::class,
            "Custom" to License.Custom::class,
        )

    private val copyleftStrength: Map<KClass<out License>, License.CopyleftStrength> =
        mapOf(
            License.Gpl2::class to License.CopyleftStrength.STRONG,
            License.Gpl3::class to License.CopyleftStrength.STRONG,
            License.Agpl3::class to License.CopyleftStrength.STRONG,
            License.Lgpl2_1::class to License.CopyleftStrength.WEAK,
            License.Lgpl3::class to License.CopyleftStrength.WEAK,
            License.Mpl2::class to License.CopyleftStrength.WEAK,
            License.Epl1::class to License.CopyleftStrength.WEAK,
            License.Cddl1::class to License.CopyleftStrength.WEAK,
            License.Cddl1_1::class to License.CopyleftStrength.WEAK,
        )

    fun bySimpleName(name: String): KClass<out License>? = bySimpleName[name]

    fun copyleftStrength(kClass: KClass<out License>): License.CopyleftStrength =
        copyleftStrength[kClass] ?: License.CopyleftStrength.NONE

    fun isCopyleft(kClass: KClass<out License>): Boolean = copyleftStrength(kClass) != License.CopyleftStrength.NONE

    fun simpleNameOf(kClass: KClass<out License>): String =
        bySimpleName.entries.first { it.value == kClass }.key

    /** The `[license-policy]` identifier for a built-in type, e.g. `"Gpl3"`. */
    fun policyId(kClass: KClass<out License>): String = simpleNameOf(kClass)

    /** Built-in [License] subtypes whose constructor requires a `year: String` parameter. */
    private val requiresYear: Set<KClass<out License>> =
        setOf(License.MIT::class, License.Mit0::class, License.Bsd2Clause::class, License.Bsd3Clause::class, License.Isc::class)

    fun requiresYear(kClass: KClass<out License>): Boolean = kClass in requiresYear
}
