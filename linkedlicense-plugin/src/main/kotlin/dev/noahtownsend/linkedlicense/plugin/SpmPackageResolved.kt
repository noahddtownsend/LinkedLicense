package dev.noahtownsend.linkedlicense.plugin

import java.io.File

/** One resolved SPM package: a git URL pinned to a revision (README §2.3). */
data class ResolvedSpmPackage(
    val identity: String?,
    val repositoryUrl: String,
    val revision: String,
)

/**
 * Parses `Package.resolved` (README §2.3) into the resolved package graph. Unlike Maven/npm/
 * CocoaPods, there's no license field anywhere in this file - only a git URL + pinned revision
 * - so this enumeration feeds only [Coordinate]s (`group = "spm"`, `moduleId` = `"spm:<url>"`)
 * with no matchable license; every one of them only ever gets resolved via `[overrides]` or the
 * best-effort fallback (§2.3), never auto-matching.
 *
 * Two on-disk shapes have existed:
 * - **v1/v2** (`{ "object": { "pins": [ { "package": ..., "repositoryURL": ..., "state": { "revision": ... } } ] } }`)
 * - **v2 (Swift 5.6+ flattened)** (`{ "pins": [ { "identity": ..., "location": ..., "state": { "revision": ... } } ] }`)
 *
 * Both are handled.
 */
object SpmPackageResolved {
    fun parse(file: File): List<ResolvedSpmPackage> = parse(file.readText())

    fun parse(jsonText: String): List<ResolvedSpmPackage> {
        val root = MiniJson.parse(jsonText) as? JsonValue.JsonObject ?: return emptyList()
        val pins = root.array("pins") ?: root.obj("object")?.array("pins") ?: return emptyList()

        return pins.items.filterIsInstance<JsonValue.JsonObject>().mapNotNull { pin ->
            val url = pin.string("repositoryURL") ?: pin.string("location") ?: return@mapNotNull null
            val revision = pin.obj("state")?.string("revision") ?: return@mapNotNull null
            val identity = pin.string("identity") ?: pin.string("package")

            ResolvedSpmPackage(identity = identity, repositoryUrl = url, revision = revision)
        }
    }
}

/** [Coordinate] form (`group = "spm"`) feeding the shared resolution pipeline. */
fun ResolvedSpmPackage.toCoordinate(): Coordinate = Coordinate(group = "spm", artifact = repositoryUrl.removeSuffix(".git"), version = revision)
