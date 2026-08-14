package dev.noahtownsend.linkedlicense.plugin

import org.gradle.api.Project
import java.io.File

/**
 * The `linkedLicense { ... }` project extension. See README §3.
 */
open class LinkedLicenseExtension(
    project: Project,
) {
    /**
     * The TOML file holding `[overrides]`/`[ignored]`/`[copyleft-allowed]` entries.
     * Defaults to `linkedlicense.toml` at the project root.
     */
    var overridesFile: File = project.file("linkedlicense.toml")

    /**
     * Copies `NOTICE`/`NOTICE.txt` files found in resolved dependency artifacts into a
     * generated `THIRD-PARTY-NOTICES` file at the project root. Default: `true`.
     */
    var copyRequiredNotices: Boolean = true

    /**
     * Fails `generateLicenseCatalog` when a resolved dependency's license has
     * `isCopyleft == true` and isn't covered by `[copyleft-allowed]`. Default: `true`.
     */
    var failOnCopyleft: Boolean = true

    /**
     * Fails `generateLicenseCatalog` when a dependency can't be auto-matched and has no
     * `[overrides]`/`[ignored]` entry. When `false`, unmatched dependencies are simply
     * omitted from `GeneratedLicenses.kt` instead of failing the build. Default: `true`.
     */
    var failOnUnknown: Boolean = true
}
