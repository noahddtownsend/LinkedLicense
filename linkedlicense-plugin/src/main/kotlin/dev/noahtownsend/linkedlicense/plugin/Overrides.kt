package dev.noahtownsend.linkedlicense.plugin

import dev.noahtownsend.linkedlicense.License
import kotlin.reflect.KClass

/** What a single `[overrides]` entry in `linkedlicense.toml` resolves to. */
sealed class OverrideSpec {
    /** A built-in [License] subtype (or field-level override with auto-matched license), e.g. `{ license = "Apache2" }` or `{ author = "Google" }`. */
    data class BuiltIn(
        val kClass: KClass<out License>? = null,
        val elementLicensed: String? = null,
        val author: String? = null,
        val url: String? = null,
        val text: String? = null,
        /** Optional [License.Custom.licenseName] override — ignored for any other built-in type. */
        val licenseName: String? = null,
        /** Explicit auto-populate toggle; when null, defers to the global extension setting. */
        val autoPopulate: Boolean? = null,
        val notice: String? = null,
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
    /**
     * `[suppress-best-guess-warnings]` (README §2.3) — silences the per-build warning the
     * best-effort fallback would otherwise emit for a coordinate, once its guess has been
     * manually verified. A reason string is required, same audit-trail pattern as `[ignored]`/
     * `[copyleft-allowed]`. Suppressing the warning never suppresses the guess itself.
     */
    val suppressBestGuessWarnings: Map<String, String> = emptyMap(),
) {
    companion object {
        val EMPTY = OverridesConfig()
    }
}
