package dev.noahtownsend.linkedlicense.plugin

import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.artifacts.result.ResolvedComponentResult
import org.gradle.api.artifacts.result.ResolvedDependencyResult

fun ModuleComponentIdentifier.toCoordinate(): Coordinate = Coordinate(group, module, version)

/**
 * Walks a resolved dependency graph starting at root's direct dependencies, and returns one
 * ModuleComponentIdentifier per unique resolved group:artifact:version - deduped so a
 * coordinate reached by multiple paths in the graph (diamond dependencies, a transitive dep
 * that's also declared directly, shared transitive deps) is processed exactly once.
 */
fun collectResolvedComponents(root: ResolvedComponentResult): List<ModuleComponentIdentifier> {
    val visited = mutableSetOf<String>()
    val result = mutableListOf<ModuleComponentIdentifier>()
    val queue = ArrayDeque<ResolvedComponentResult>()

    root.dependencies.filterIsInstance<ResolvedDependencyResult>().forEach { queue.addLast(it.selected) }

    while (queue.isNotEmpty()) {
        val current = queue.removeFirst()
        val id = current.id as? ModuleComponentIdentifier ?: continue

        if (!visited.add(id.toCoordinate().toString())) {
            continue
        }

        result += id
        current.dependencies.filterIsInstance<ResolvedDependencyResult>().forEach { queue.addLast(it.selected) }
    }

    return result
}
