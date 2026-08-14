package dev.noahtownsend.linkedlicense.plugin

import java.io.File

/** One npm package's own `package.json`, as far as this plugin cares (README §2.3). */
data class NpmPackageInfo(
    val name: String?,
    val version: String?,
    /** The `license` field, normalized to the same shape as a Maven POM `<license>` entry. */
    val license: PomLicense?,
    /** Names of this package's own direct `dependencies` (for transitive graph walking). */
    val dependencyNames: Set<String>,
    /** `repository` field, normalized to a plain URL — feeds the best-effort fallback (§2.3). */
    val repositoryUrl: String?,
)

/**
 * Parses one npm package's `package.json`. The `license` field comes in two shapes still found
 * in the wild:
 * - a plain SPDX string: `"license": "MIT"`
 * - the older `{ type, url }` object form: `"license": { "type": "MIT", "url": "..." }`
 *
 * A legacy `licenses` array (`[{ type, url }, ...]`, pre-npm-1.5) is also read as a fallback,
 * using its first entry, when `license` is absent.
 */
fun parseNpmPackageJson(file: File): NpmPackageInfo? {
    if (!file.exists()) {
        return null
    }

    return parseNpmPackageJson(file.readText())
}

fun parseNpmPackageJson(jsonText: String): NpmPackageInfo? {
    val root = MiniJson.parse(jsonText) as? JsonValue.JsonObject ?: return null

    val license = extractLicense(root)
    val dependencyNames = (root.obj("dependencies")?.entries?.keys).orEmpty()
    val repositoryUrl = extractRepositoryUrl(root)

    return NpmPackageInfo(
        name = root.string("name"),
        version = root.string("version"),
        license = license,
        dependencyNames = dependencyNames,
        repositoryUrl = repositoryUrl,
    )
}

private fun extractLicense(root: JsonValue.JsonObject): PomLicense? {
    when (val licenseValue = root["license"]) {
        is JsonValue.JsonString -> return PomLicense(name = licenseValue.value, url = null)
        is JsonValue.JsonObject -> {
            return PomLicense(name = licenseValue.string("type"), url = licenseValue.string("url"))
        }
        else -> Unit
    }

    val legacy = (root["licenses"] as? JsonValue.JsonArray)?.items?.firstOrNull() as? JsonValue.JsonObject

    if (legacy != null) {
        return PomLicense(name = legacy.string("type"), url = legacy.string("url"))
    }

    return null
}

private fun extractRepositoryUrl(root: JsonValue.JsonObject): String? {
    val raw =
        when (val repository = root["repository"]) {
            is JsonValue.JsonString -> repository.value
            is JsonValue.JsonObject -> repository.string("url")
            else -> null
        } ?: return null

    return raw
        .removePrefix("git+")
        .removeSuffix(".git")
        .replace(Regex("^git://"), "https://")
}

/**
 * [PomInfo] form of this package's parsed license/repository info, so it can flow through
 * [CatalogGenerator]'s matching/fallback pipeline unchanged. `organizationName` has no npm
 * equivalent (codegen falls back to `coordinate.group`, the literal `"npm"` ecosystem tag).
 */
fun NpmPackageInfo.toPomInfo(): PomInfo =
    PomInfo(
        licenses = listOfNotNull(license),
        organizationName = null,
        scmUrl = repositoryUrl,
    )

/**
 * Walks an installed `node_modules` tree and returns one [Coordinate] per unique
 * `(name, version)` pair found — direct and transitive alike (README §2.3). Coordinates use
 * `group = "npm"` so [Coordinate.moduleId] naturally comes out as `"npm:<package-name>"`, the
 * override-table key format §2.3 specifies, and the rest of the resolution pipeline
 * ([CatalogGenerator]) needs no npm-specific branching.
 *
 * Every `package.json` reachable under [nodeModulesDir] is read, including nested
 * `node_modules` directories (npm/Yarn nest a package locally instead of hoisting it when two
 * dependents need incompatible versions) — walking exhaustively finds both cases without having
 * to separately know which packages are "direct" vs "transitive", matching how tools like
 * `npm ls`/license-checker approach this.
 */
fun scanNpmDependencyGraph(nodeModulesDir: File): List<Coordinate> = scanNpmPackageInfo(nodeModulesDir).keys.toList()

/**
 * Same walk as [scanNpmDependencyGraph], but keeping each coordinate's parsed
 * [NpmPackageInfo] (license field, repository URL) alongside it, so callers can feed both the
 * coordinate *and* its license info into the shared resolution pipeline without re-reading
 * every `package.json` a second time.
 */
fun scanNpmPackageInfo(nodeModulesDir: File): Map<Coordinate, NpmPackageInfo> {
    if (!nodeModulesDir.isDirectory) {
        return emptyMap()
    }

    val seen = linkedMapOf<Coordinate, NpmPackageInfo>()
    val seenKeys = mutableSetOf<String>()

    fun visit(dir: File) {
        val packageDirs = dir.listFiles { candidate -> candidate.isDirectory } ?: return

        for (packageDir in packageDirs.sortedBy { it.name }) {
            if (packageDir.name.startsWith("@")) {
                // Scoped packages (@scope/name) are one directory level deeper.
                val scopedDirs = packageDir.listFiles { candidate -> candidate.isDirectory } ?: continue

                for (scopedDir in scopedDirs.sortedBy { it.name }) {
                    visitPackageDir(scopedDir, seen, seenKeys)
                    visit(File(scopedDir, "node_modules"))
                }

                continue
            }

            visitPackageDir(packageDir, seen, seenKeys)
            visit(File(packageDir, "node_modules"))
        }
    }

    visit(nodeModulesDir)
    return seen
}

private fun visitPackageDir(
    packageDir: File,
    seen: MutableMap<Coordinate, NpmPackageInfo>,
    seenKeys: MutableSet<String>,
) {
    val info = parseNpmPackageJson(File(packageDir, "package.json")) ?: return
    val name = info.name ?: return
    val version = info.version ?: "0.0.0"

    if (seenKeys.add("$name@$version")) {
        seen[Coordinate(group = "npm", artifact = name, version = version)] = info
    }
}
