package dev.noahtownsend.linkedlicense.plugin

import dev.noahtownsend.linkedlicense.License
import kotlin.reflect.KClass

/** What a single `[overrides]` entry in `linkedlicense.toml` resolves to. */
sealed class OverrideSpec {
    /** A built-in [License] subtype, e.g. `{ license = "Apache2" }`. */
    data class BuiltIn(
        val kClass: KClass<out License>,
        val elementLicensed: String?,
        val author: String?,
        val url: String?,
        val text: String?,
    ) : OverrideSpec()

    /** A `custom:fully.qualified.Symbol` reference — codegen emits an import + reference. */
    data class Custom(
        val fullyQualifiedName: String,
    ) : OverrideSpec()
}

/**
 * `[license-policy]` (README §3.6) — a license-*type*-level allow/block list, independent of
 * the coordinate-level tables. Entries use the same identifier space as `[overrides]`'s
 * `license = "..."` field: a built-in simple name (e.g. `"MIT"`) or a `custom:fqcn` reference.
 */
data class LicensePolicy(
    val allow: Set<String> = emptySet(),
    val block: Set<String> = emptySet(),
) {
    fun isAllowed(licenseTypeId: String): Boolean = if (allow.isNotEmpty()) licenseTypeId in allow else licenseTypeId !in block

    companion object {
        val EMPTY = LicensePolicy()
    }
}

/** Parsed contents of `linkedlicense.toml`, keyed by resolved `group:artifact`. */
data class OverridesConfig(
    val overrides: Map<String, OverrideSpec> = emptyMap(),
    val ignored: Map<String, String> = emptyMap(),
    val copyleftAllowed: Map<String, String> = emptyMap(),
    val licensePolicy: LicensePolicy = LicensePolicy.EMPTY,
    /**
     * `[assets]` (README §3.7) — non-dependency assets (fonts, datasets, images) keyed by an
     * arbitrary asset identifier rather than a Gradle coordinate. Same value shape as
     * `[overrides]`. Every entry is included unconditionally, tagged
     * `kind = License.Kind.ASSET` — there's no matching/fail-on-unknown step for these.
     */
    val assets: Map<String, OverrideSpec> = emptyMap(),
) {
    companion object {
        val EMPTY = OverridesConfig()
    }
}
