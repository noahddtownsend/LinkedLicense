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
        /**
         * Explicit override for weak-copyleft dependencies (README §3.5): `null` (the default)
         * defers to [failOnCopyleft]; non-`null` takes precedence over it for weak copyleft
         * only. Strong copyleft is governed by [failOnCopyleft] alone.
         */
        failOnSoftCopyleft: Boolean? = null,
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
        /** Default for auto-populating non-overridden fields from POM/best-effort metadata. */
        autoPopulate: Boolean = true,
        /** Invoked when an unresolvable ${...} placeholder is found in POM metadata and fell back (Bug 6). */
        onUnresolvedPlaceholder: (Coordinate, String) -> Unit = { _, _ -> },
        /** Provides extracted notice text for a dependency coordinate, if available. */
        noticeOf: (Coordinate) -> String? = { null },
    ): CatalogResult {
        val entries = mutableListOf<Pair<Coordinate, CatalogEntry>>()
        val unresolved = mutableListOf<Coordinate>()
        val copyleftOffenders = mutableListOf<Coordinate>()
        val policyOffenders = mutableListOf<PolicyOffender>()

        fun resolveElement(pomInfo: PomInfo, coordinate: Coordinate, explicit: String?, autoPopulate: Boolean): String {
            if (explicit != null) return explicit
            if (!autoPopulate) return coordinate.artifact
            val rawName = pomInfo.name
            val resolved = pomInfo.resolveElementLicensed(coordinate.artifact)
            if (rawName != null && rawName.contains("\${") && resolved == coordinate.artifact) {
                onUnresolvedPlaceholder(coordinate, rawName)
            }
            return resolved
        }

        /**
         * Whether a dependency with this [kClass]'s copyleft strength fails the guard, per
         * README §3.5: `failOnCopyleft` alone decides strong copyleft; weak copyleft is decided
         * by `failOnSoftCopyleft ?: failOnCopyleft`.
         */
        fun failsCopyleftGuard(kClass: KClass<out License>): Boolean =
            when (BuiltInLicenses.copyleftStrength(kClass)) {
                License.CopyleftStrength.STRONG -> failOnCopyleft
                License.CopyleftStrength.WEAK -> failOnSoftCopyleft ?: failOnCopyleft
                License.CopyleftStrength.NONE -> false
            }

        fun policyId(spec: OverrideSpec?, matched: kotlin.reflect.KClass<out dev.noahtownsend.linkedlicense.License>?): String =
            when {
                spec is OverrideSpec.Custom -> "custom:${spec.fullyQualifiedName}"
                spec is OverrideSpec.BuiltIn && spec.kClass != null -> BuiltInLicenses.policyId(spec.kClass)
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
                    val pomInfo = pomInfoOf(coordinate)
                    val effectiveAutoPopulate = overrideSpec.autoPopulate ?: autoPopulate

                    val resolvedKClass =
                        overrideSpec.kClass ?: run {
                            val fieldMatched =
                                pomInfo.licenses.firstNotNullOfOrNull { LicenseMatcher.match(it.name, it.url) }

                            fieldMatched
                                ?: pomInfo.scmUrl?.let { repoUrl -> bestEffortFetch?.invoke(repoUrl, coordinate.version) }
                                    ?.also { onBestGuess(coordinate, it) }
                        }

                    if (resolvedKClass == null) {
                        if (failOnUnknown) {
                            unresolved += coordinate
                        }
                        continue
                    }

                    val typeId = policyId(overrideSpec, resolvedKClass)

                    if (!overrides.licensePolicy.isAllowed(typeId)) {
                        policyOffenders += PolicyOffender(coordinate, typeId)
                        continue
                    }

                    if (failsCopyleftGuard(resolvedKClass) &&
                        !overrides.copyleftAllowed.containsKey(coordinate.moduleId)
                    ) {
                        copyleftOffenders += coordinate
                        continue
                    }

                    val resolvedElementLicensed =
                        resolveElement(
                            pomInfo = pomInfo,
                            coordinate = coordinate,
                            explicit = overrideSpec.elementLicensed,
                            autoPopulate = effectiveAutoPopulate,
                        )

                    val resolvedAuthor =
                        overrideSpec.author
                            ?: if (effectiveAutoPopulate) pomInfo.resolveAuthor(coordinate.group) else coordinate.group

                    val resolvedUrl =
                        overrideSpec.url
                            ?: if (effectiveAutoPopulate) pomInfo.licenses.firstOrNull { it.url != null }?.url ?: pomInfo.scmUrl else null

                    val resolvedNotice = overrideSpec.notice ?: noticeOf(coordinate)

                    entries +=
                        coordinate to
                        CatalogEntry.BuiltIn(
                            buildInExpression(
                                kClass = resolvedKClass,
                                elementLicensed = resolvedElementLicensed,
                                author = resolvedAuthor,
                                url = resolvedUrl,
                                text = overrideSpec.text,
                                licenseName = overrideSpec.licenseName,
                                notice = resolvedNotice,
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

                    if (failsCopyleftGuard(matched) &&
                        !overrides.copyleftAllowed.containsKey(coordinate.moduleId)
                    ) {
                        copyleftOffenders += coordinate
                        continue
                    }

                    val resolvedElementLicensed =
                        resolveElement(
                            pomInfo = pomInfo,
                            coordinate = coordinate,
                            explicit = null,
                            autoPopulate = autoPopulate,
                        )

                    val resolvedAuthor =
                        if (autoPopulate) pomInfo.resolveAuthor(coordinate.group) else coordinate.group

                    val resolvedUrl =
                        if (autoPopulate) pomInfo.licenses.firstOrNull { it.url != null }?.url ?: pomInfo.scmUrl else null

                    val resolvedNotice = noticeOf(coordinate)

                    entries +=
                        coordinate to
                        CatalogEntry.BuiltIn(
                            buildInExpression(
                                kClass = matched,
                                elementLicensed = resolvedElementLicensed,
                                author = resolvedAuthor,
                                url = resolvedUrl,
                                text = null,
                                notice = resolvedNotice,
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
                    val kClass =
                        spec.kClass
                            ?: throw org.gradle.api.GradleException("[assets] entry '$assetKey' must specify a 'license' key.")
                    val typeId = BuiltInLicenses.policyId(kClass)

                    if (!overrides.licensePolicy.isAllowed(typeId)) {
                        assetPolicyOffenders += AssetPolicyOffender(assetKey, typeId)
                        continue
                    }

                    if (failsCopyleftGuard(kClass) &&
                        !overrides.copyleftAllowed.containsKey(assetKey)
                    ) {
                        assetCopyleftOffenders += assetKey
                        continue
                    }

                    assetEntries +=
                        assetKey to
                        CatalogEntry.BuiltIn(
                            buildInExpression(
                                kClass = kClass,
                                elementLicensed = spec.elementLicensed ?: assetKey,
                                author = spec.author ?: assetKey,
                                url = spec.url,
                                text = spec.text,
                                isAsset = true,
                                licenseName = spec.licenseName,
                                notice = spec.notice,
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
