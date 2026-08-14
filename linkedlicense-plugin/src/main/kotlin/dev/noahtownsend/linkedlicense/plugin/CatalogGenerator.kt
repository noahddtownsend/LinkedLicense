package dev.noahtownsend.linkedlicense.plugin

/** A coordinate that failed the `[license-policy]` allow/block check (README §3.6). */
data class PolicyOffender(
    val coordinate: Coordinate,
    val licenseTypeId: String,
)

/** The outcome of resolving one coordinate's license against overrides/matcher/copyleft guard. */
data class CatalogResult(
    val entries: List<Pair<Coordinate, CatalogEntry>>,
    val unresolved: List<Coordinate>,
    val copyleftOffenders: List<Coordinate>,
    val policyOffenders: List<PolicyOffender>,
)

/**
 * The pure resolution core of `generateLicenseCatalog` (README §2.1 / §3.3 / §3.5 / §3.6) —
 * dedup already happened upstream ([collectResolvedComponents]); this decides, per
 * coordinate, whether it's ignored, overridden, auto-matched, unresolved, a copyleft
 * offender, or a `[license-policy]` offender.
 */
object CatalogGenerator {
    fun resolve(
        coordinates: List<Coordinate>,
        pomInfoOf: (Coordinate) -> PomInfo,
        overrides: OverridesConfig,
        failOnCopyleft: Boolean,
        failOnUnknown: Boolean,
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
                    val matched =
                        pomInfo.licenses.firstNotNullOfOrNull { LicenseMatcher.match(it.name, it.url) }

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

        return CatalogResult(entries, unresolved, copyleftOffenders, policyOffenders)
    }
}
