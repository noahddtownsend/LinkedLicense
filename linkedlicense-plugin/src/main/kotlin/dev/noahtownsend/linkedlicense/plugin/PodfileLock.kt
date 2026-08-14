package dev.noahtownsend.linkedlicense.plugin

import java.io.File

/** One resolved pod from a `Podfile.lock`'s `PODS` section. */
data class ResolvedPod(
    val name: String,
    val version: String,
)

/**
 * Parses `Podfile.lock` (README §2.3) — specifically its `PODS:` section, which is the resolved
 * pod graph (name + version), for every pod whether pulled in directly or transitively.
 *
 * `Podfile.lock` is YAML, but a narrow, highly regular subset of it — CocoaPods only ever
 * emits this one shape. A small hand-rolled line-based parser handles it reliably without
 * pulling in a general YAML library for one file format (same reasoning as [MiniJson] for
 * `package.json`/`Package.resolved`/`.podspec.json`).
 *
 * Example shape:
 * ```
 * PODS:
 *   - Alamofire (5.6.4)
 *   - SomePod (1.2.3):
 *     - SomeDependency (~> 2.0)
 *   - SomeDependency (2.0.1)
 *
 * DEPENDENCIES:
 *   - Alamofire
 *   - SomePod
 * ```
 *
 * Only the top-level `- Name (version)` list items are pods; a nested, deeper-indented
 * `- OtherName (constraint)` line under a pod is that pod's *own* dependency requirement
 * (a version constraint, not a resolved version) — it's ignored here since the dependency it
 * names is already present as its own top-level `PODS` entry with its actual resolved version.
 */
object PodfileLock {
    private val podEntryRegex = Regex("""^-\s+(\S+)\s+\(([^)]+)\)\s*:?\s*$""")

    fun parse(file: File): List<ResolvedPod> = parse(file.readText())

    fun parse(text: String): List<ResolvedPod> {
        val lines = text.lines()
        val podsHeaderIndex = lines.indexOfFirst { it.trimEnd() == "PODS:" }

        if (podsHeaderIndex == -1) {
            return emptyList()
        }

        val result = mutableListOf<ResolvedPod>()
        var topLevelIndent: Int? = null

        for (line in lines.drop(podsHeaderIndex + 1)) {
            if (line.isBlank()) {
                continue
            }

            // A non-indented, non-list line ends the PODS section (e.g. "DEPENDENCIES:").
            if (!line.startsWith(" ")) {
                break
            }

            val indent = line.takeWhile { it == ' ' }.length
            val trimmed = line.trim()

            if (topLevelIndent == null) {
                topLevelIndent = indent
            }

            if (indent != topLevelIndent) {
                // A nested sub-dependency line (constraint, not a resolved pod) - skip.
                continue
            }

            val match = podEntryRegex.matchEntire(trimmed) ?: continue
            result += ResolvedPod(name = match.groupValues[1], version = match.groupValues[2])
        }

        return result
    }
}
