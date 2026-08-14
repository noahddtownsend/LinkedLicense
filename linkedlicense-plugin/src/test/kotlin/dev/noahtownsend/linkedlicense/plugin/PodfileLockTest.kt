package dev.noahtownsend.linkedlicense.plugin

import kotlin.test.Test
import kotlin.test.assertEquals

class PodfileLockTest {
    private val fixture =
        """
        PODS:
          - Alamofire (5.6.4)
          - SomePod (1.2.3):
            - SomeDependency (~> 2.0)
          - SomeDependency (2.0.1)

        DEPENDENCIES:
          - Alamofire
          - SomePod

        SPEC REPOS:
          trunk:
            - Alamofire
            - SomeDependency
            - SomePod

        SPEC CHECKSUMS:
          Alamofire: abc123
          SomeDependency: def456
          SomePod: ghi789

        PODFILE CHECKSUM: 0123456789abcdef

        COCOAPODS: 1.11.3
        """.trimIndent()

    @Test
    fun `parse() reads every top-level pod name and version including transitive ones`() {
        val pods = PodfileLock.parse(fixture)

        assertEquals(
            listOf(
                ResolvedPod("Alamofire", "5.6.4"),
                ResolvedPod("SomePod", "1.2.3"),
                ResolvedPod("SomeDependency", "2.0.1"),
            ),
            pods,
        )
    }

    @Test
    fun `parse() ignores nested sub-dependency constraint lines`() {
        val pods = PodfileLock.parse(fixture)

        // "SomeDependency (~> 2.0)" nested under SomePod must not appear as its own entry -
        // only the resolved top-level "SomeDependency (2.0.1)" pod does.
        assertEquals(1, pods.count { it.name == "SomeDependency" })
        assertEquals("2.0.1", pods.single { it.name == "SomeDependency" }.version)
    }

    @Test
    fun `parse() returns empty list when there is no PODS section`() {
        assertEquals(emptyList(), PodfileLock.parse("DEPENDENCIES:\n  - Foo\n"))
    }

    @Test
    fun `parse() returns empty list for an empty PODS section`() {
        val pods = PodfileLock.parse("PODS:\n\nDEPENDENCIES:\n  - Foo\n")

        assertEquals(emptyList(), pods)
    }

    @Test
    fun `parse() handles a pod with a build variant suffix on its own line`() {
        val pods = PodfileLock.parse("PODS:\n  - GoogleUtilities/Environment (7.11.0)\n")

        assertEquals(listOf(ResolvedPod("GoogleUtilities/Environment", "7.11.0")), pods)
    }
}
