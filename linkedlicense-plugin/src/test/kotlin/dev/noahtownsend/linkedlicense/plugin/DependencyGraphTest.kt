package dev.noahtownsend.linkedlicense.plugin

import io.mockk.every
import io.mockk.mockk
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.artifacts.result.ResolvedComponentResult
import org.gradle.api.artifacts.result.ResolvedDependencyResult
import kotlin.test.Test
import kotlin.test.assertEquals

class DependencyGraphTest {
    private fun component(
        group: String,
        artifact: String,
        version: String,
        dependencies: () -> Set<ResolvedDependencyResult>,
    ): ResolvedComponentResult {
        val id =
            mockk<ModuleComponentIdentifier> {
                every { this@mockk.group } returns group
                every { module } returns artifact
                every { this@mockk.version } returns version
            }
        val comp = mockk<ResolvedComponentResult>()
        every { comp.id } returns id
        every { comp.dependencies } answers { dependencies() }
        return comp
    }

    private fun dependencyOn(target: ResolvedComponentResult): ResolvedDependencyResult =
        mockk<ResolvedDependencyResult> {
            every { selected } returns target
        }

    @Test
    fun `collectResolvedComponents() dedupes a diamond dependency to one entry`() {
        // root -> A -> C, root -> B -> C: C is reachable via two paths but must appear once.
        val c = component("com.example", "c", "1.0") { emptySet() }
        val a = component("com.example", "a", "1.0") { setOf(dependencyOn(c)) }
        val b = component("com.example", "b", "1.0") { setOf(dependencyOn(c)) }
        val root = component("root", "root", "unspecified") { setOf(dependencyOn(a), dependencyOn(b)) }

        val result = collectResolvedComponents(root)

        assertEquals(3, result.size)
        assertEquals(1, result.count { it.toCoordinate() == Coordinate("com.example", "c", "1.0") })
    }

    @Test
    fun `collectResolvedComponents() dedupes a transitive dep that's also declared directly`() {
        // root -> A -> C, root -> C directly: still one entry for C.
        val c = component("com.example", "c", "1.0") { emptySet() }
        val a = component("com.example", "a", "1.0") { setOf(dependencyOn(c)) }
        val root = component("root", "root", "unspecified") { setOf(dependencyOn(a), dependencyOn(c)) }

        val result = collectResolvedComponents(root)

        assertEquals(2, result.size)
        assertEquals(1, result.count { it.toCoordinate() == Coordinate("com.example", "c", "1.0") })
    }

    @Test
    fun `collectResolvedComponents() is safe against dependency cycles`() {
        val holder = arrayOfNulls<ResolvedComponentResult>(1)
        val a =
            mockk<ResolvedComponentResult> {
                every { id } returns
                    mockk<ModuleComponentIdentifier> {
                        every { group } returns "com.example"
                        every { module } returns "a"
                        every { version } returns "1.0"
                    }
            }
        val b =
            mockk<ResolvedComponentResult> {
                every { id } returns
                    mockk<ModuleComponentIdentifier> {
                        every { group } returns "com.example"
                        every { module } returns "b"
                        every { version } returns "1.0"
                    }
                every { dependencies } returns setOf(dependencyOn(a))
            }
        every { a.dependencies } returns setOf(dependencyOn(b))
        holder[0] = a

        val root = component("root", "root", "unspecified") { setOf(dependencyOn(a)) }

        val result = collectResolvedComponents(root)

        assertEquals(2, result.size)
    }
}
