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

    /**
     * Opt-in best-guess fallback (README §2.3): when a dependency's own license field is
     * missing/unmatched but a repository URL is known, fetch that repo's `LICENSE` file at the
     * resolved revision and pattern-match its content against known license texts. Applies
     * uniformly across Maven, npm, CocoaPods, and SPM. Every dependency resolved this way emits
     * a build warning naming the coordinate and the guessed license, unless silenced via
     * `[suppress-best-guess-warnings]` in `linkedlicense.toml`. Default: `false`.
     */
    var bestEffortLicenseFetch: Boolean = false
}
