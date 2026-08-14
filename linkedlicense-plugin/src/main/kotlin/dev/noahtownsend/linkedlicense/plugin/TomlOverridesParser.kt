package dev.noahtownsend.linkedlicense.plugin

import org.gradle.api.GradleException
import org.tomlj.Toml
import org.tomlj.TomlTable
import java.io.File

/**
 * Parses `linkedlicense.toml` (§3.1). Coordinate keys may be a raw `group:artifact` string or
 * a `libs.*`-style version-catalog alias — [resolveAlias] resolves the latter (§3.2).
 */
object TomlOverridesParser {
    fun parse(
        file: File,
        resolveAlias: (String) -> String,
    ): OverridesConfig {
        if (!file.exists()) {
            return OverridesConfig.EMPTY
        }

        val result = Toml.parse(file.toPath())

        if (result.hasErrors()) {
            val message = result.errors().joinToString("\n") { it.toString() }
            throw GradleException("Failed to parse ${file.path}:\n$message")
        }

        fun resolveKey(rawKey: String): String = if (rawKey.startsWith("libs.")) resolveAlias(rawKey) else rawKey

        val overridesTable = result.getTable("overrides")
        val overrides =
            overridesTable
                ?.keySet()
                .orEmpty()
                .associate { rawKey ->
                    val entryTable =
                        overridesTable!!.getTable(listOf(rawKey))
                            ?: throw GradleException("[overrides] entry '$rawKey' in ${file.path} must be a table.")
                    resolveKey(rawKey) to parseOverrideEntry(rawKey, entryTable, file)
                }

        val ignoredTable = result.getTable("ignored")
        val ignored =
            ignoredTable
                ?.keySet()
                .orEmpty()
                .associate { rawKey ->
                    val reason =
                        ignoredTable!!.getString(listOf(rawKey))
                            ?: throw GradleException("[ignored] entry '$rawKey' in ${file.path} must be a reason string.")
                    resolveKey(rawKey) to reason
                }

        val copyleftAllowedTable = result.getTable("copyleft-allowed")
        val copyleftAllowed =
            copyleftAllowedTable
                ?.keySet()
                .orEmpty()
                .associate { rawKey ->
                    val reason =
                        copyleftAllowedTable!!.getString(listOf(rawKey))
                            ?: throw GradleException(
                                "[copyleft-allowed] entry '$rawKey' in ${file.path} must be a reason string.",
                            )
                    resolveKey(rawKey) to reason
                }

        val policyTable = result.getTable("license-policy")
        val licensePolicy =
            LicensePolicy(
                allow = policyTable?.getArrayOrEmpty("allow")?.toList()?.map { it.toString() }?.toSet().orEmpty(),
                block = policyTable?.getArrayOrEmpty("block")?.toList()?.map { it.toString() }?.toSet().orEmpty(),
            )

        return OverridesConfig(overrides, ignored, copyleftAllowed, licensePolicy)
    }

    private fun parseOverrideEntry(
        rawKey: String,
        table: TomlTable,
        file: File,
    ): OverrideSpec {
        val licenseRef =
            table.getString("license")
                ?: throw GradleException("[overrides] entry '$rawKey' in ${file.path} is missing a 'license' key.")

        if (licenseRef.startsWith("custom:")) {
            return OverrideSpec.Custom(licenseRef.removePrefix("custom:"))
        }

        val kClass =
            BuiltInLicenses.bySimpleName(licenseRef)
                ?: throw GradleException(
                    "[overrides] entry '$rawKey' in ${file.path} references unknown built-in license '$licenseRef'.",
                )

        return OverrideSpec.BuiltIn(
            kClass = kClass,
            elementLicensed = table.getString("elementLicensed"),
            author = table.getString("author"),
            url = table.getString("url"),
            text = table.getString("text"),
        )
    }
}
