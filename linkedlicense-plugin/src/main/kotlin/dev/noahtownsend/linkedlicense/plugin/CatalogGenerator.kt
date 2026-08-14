package dev.noahtownsend.linkedlicense.plugin

import dev.noahtownsend.linkedlicense.License
import kotlin.reflect.KClass

/** A coordinate that failed the `[license-policy]` allow/block check (README §3.6). */
data class PolicyOffender(
    val coordinate: Coordinate,
    val licenseTypeId: String,
)

/** An `[assets]` entry (README §3.7) that failed the `[license-policy]` allow/block check. */
data class AssetPolicyOffender(
    val assetKey: String,
    val licenseTypeId: String,
)

/** The outcome of resolving one coordinate's license against overrides/matcher/copyleft guard. */
data class CatalogResult(
    val entries: List<Pair<Coordinate, CatalogEntry>>,
    val unresolved: List<Coordinate>,
    val copyleftOffenders: List<Coordinate>,
    val policyOffenders: List<PolicyOffender>,
    /** `[assets]` entries (README §3.7) — no matching/fail-on-unknown step, always resolved. */
    val assetEntries: List<Pair<String, CatalogEntry>> = emptyList(),
    val assetCopyleftOffenders: List<String> = emptyList(),
    val assetPolicyOffenders: List<AssetPolicyOffender> = emptyList(),
)

/**
 * The pure resolution core of `generateLicenseCatalog` (README §2.1 / §3.3 / §3.5 / §3.6 / §3.7)
 * — dedup already happened upstream ([collectResolvedComponents]); this decides, per
 * coordinate, whether it's ignored, overridden, auto-matched, unresolved, a copyleft
 * offender, or a `[license-policy]` offender. `[assets]` entries (§3.7) go through the same
 * copyleft/policy checks but skip matching entirely, since their license is supplied directly.
 */
object CatalogGenerator {
    fun resolve(
        coordinates: List<Coordinate>,
        pomInfoOf: (Coordinate) -> PomInfo,
        overrides: OverridesConfig,
        failOnCopyleft: Boolean,
        failOnUnknown: Boolean,
        /**
         * Whether to process `overrides.assets` (README §3.7) into the result. `[assets]`
         * entries aren't tied to any one Kotlin source set/target, so a multiplatform project
         * only wants them folded into the `commonMain` catalog - not repeated once per
         * platform target's own catalog too. Single-source-set (plain JVM) projects and the
         * `commonMain` union both pass `true` (the default).
         */
        includeAssets: Boolean = true,
        /**
         * README §2.3 best-guess fallback: invoked with a coordinate's known repository URL
         * and version (used as the fetch ref) when no primary field matched. Returns the
         * guessed license type, or `null` if nothing could be fetched/matched. `null` overall
         * (the default) disables the fallback entirely - matches `bestEffortLicenseFetch = false`.
         */
        bestEffortFetch: ((repoUrl: String, ref: String) -> KClass<out License>?)? = null,
        /** Invoked once per coordinate resolved via [bestEffortFetch] - the warning trigger point. */
        onBestGuess: (Coordinate, KClass<out License>) -> Unit = { _, _ -> },
    ): CatalogResult {
        val entries = mutableListOf<Pair<Coordinate, CatalogEntry>>()
        val unresolved = mutableListOf<Coordinate>()
        val copyleftOffenders = mutableListOf<Coordinate>()
        val policyOffenders = mutableListOf<PolicyOffender>()

        fun policyId(spec: OverrideSpec?, matched: kotlin.reflect.KClass<out dev.noahtownsend.linkedlicense.License>?): String =
            when {
                spec is OverrideSpec.Custom -> "custom:${spec.fullyQualifiedName}"
                spec is OverrideSpec.BuiltIn -> BuiltInLicenses.policyId(spec.kClass)
                matched != null -> BuiltInLicenses.policyId(matched)
                else -> error("policyId requested for an unresolved coordinate")
            }

        for (coordinate in coordinates) {
            if (overrides.ignored.containsKey(coordinate.moduleId)) {
                continue
            }

            val overrideSpec = overrides.overrides[coordinate.moduleId]

            when (overrideSpec) {
                is OverrideSpec.Custom -> {
                    val typeId = policyId(overrideSpec, null)

                    if (!overrides.licensePolicy.isAllowed(typeId)) {
                        policyOffenders += PolicyOffender(coordinate, typeId)
                        continue
                    }

                    entries += coordinate to CatalogEntry.CustomRef(overrideSpec.fullyQualifiedName)
                    // Copyleft status of a custom code-defined license isn't knowable at build
                    // time from the override file, so custom overrides bypass the copyleft guard.
                }

                is OverrideSpec.BuiltIn -> {
                    val typeId = policyId(overrideSpec, null)

                    if (!overrides.licensePolicy.isAllowed(typeId)) {
                        policyOffenders += PolicyOffender(coordinate, typeId)
                        continue
                    }

                    if (BuiltInLicenses.isCopyleft(overrideSpec.kClass) &&
                        failOnCopyleft &&
                        !overrides.copyleftAllowed.containsKey(coordinate.moduleId)
                    ) {
                        copyleftOffenders += coordinate
                        continue
                    }

                    entries +=
                        coordinate to
                        CatalogEntry.BuiltIn(
                            buildInExpression(
                                kClass = overrideSpec.kClass,
                                elementLicensed = overrideSpec.elementLicensed ?: coordinate.artifact,
                                author = overrideSpec.author ?: coordinate.group,
                                url = overrideSpec.url,
                                text = overrideSpec.text,
                            ),
                        )
                }

                null -> {
                    val pomInfo = pomInfoOf(coordinate)
                    val fieldMatched =
                        pomInfo.licenses.firstNotNullOfOrNull { LicenseMatcher.match(it.name, it.url) }

                    val matched =
                        fieldMatched
                            ?: pomInfo.scmUrl?.let { repoUrl -> bestEffortFetch?.invoke(repoUrl, coordinate.version) }
                                ?.also { onBestGuess(coordinate, it) }

                    if (matched == null) {
                        if (failOnUnknown) {
                            unresolved += coordinate
                        }
                        continue
                    }

                    val typeId = policyId(null, matched)

                    if (!overrides.licensePolicy.isAllowed(typeId)) {
                        policyOffenders += PolicyOffender(coordinate, typeId)
                        continue
                    }

                    if (BuiltInLicenses.isCopyleft(matched) &&
                        failOnCopyleft &&
                        !overrides.copyleftAllowed.containsKey(coordinate.moduleId)
                    ) {
                        copyleftOffenders += coordinate
                        continue
                    }

                    entries +=
                        coordinate to
                        CatalogEntry.BuiltIn(
                            buildInExpression(
                                kClass = matched,
                                elementLicensed = coordinate.artifact,
                                author = pomInfo.organizationName ?: coordinate.group,
                                url = pomInfo.licenses.firstOrNull { it.url != null }?.url,
                                text = null,
                            ),
                        )
                }
            }
        }

        val assetEntries = mutableListOf<Pair<String, CatalogEntry>>()
        val assetCopyleftOffenders = mutableListOf<String>()
        val assetPolicyOffenders = mutableListOf<AssetPolicyOffender>()

        for ((assetKey, spec) in if (includeAssets) overrides.assets else emptyMap()) {
            when (spec) {
                is OverrideSpec.Custom -> {
                    val typeId = "custom:${spec.fullyQualifiedName}"

                    if (!overrides.licensePolicy.isAllowed(typeId)) {
                        assetPolicyOffenders += AssetPolicyOffender(assetKey, typeId)
                        continue
                    }

                    assetEntries += assetKey to CatalogEntry.CustomRef(spec.fullyQualifiedName)
                }

                is OverrideSpec.BuiltIn -> {
                    val typeId = BuiltInLicenses.policyId(spec.kClass)

                    if (!overrides.licensePolicy.isAllowed(typeId)) {
                        assetPolicyOffenders += AssetPolicyOffender(assetKey, typeId)
                        continue
                    }

                    if (BuiltInLicenses.isCopyleft(spec.kClass) &&
                        failOnCopyleft &&
                        !overrides.copyleftAllowed.containsKey(assetKey)
                    ) {
                        assetCopyleftOffenders += assetKey
                        continue
                    }

                    assetEntries +=
                        assetKey to
                        CatalogEntry.BuiltIn(
                            buildInExpression(
                                kClass = spec.kClass,
                                elementLicensed = spec.elementLicensed ?: assetKey,
                                author = spec.author ?: assetKey,
                                url = spec.url,
                                text = spec.text,
                                isAsset = true,
                            ),
                        )
                }
            }
        }

        return CatalogResult(
            entries = entries,
            unresolved = unresolved,
            copyleftOffenders = copyleftOffenders,
            policyOffenders = policyOffenders,
            assetEntries = assetEntries,
            assetCopyleftOffenders = assetCopyleftOffenders,
            assetPolicyOffenders = assetPolicyOffenders,
        )
    }
}
