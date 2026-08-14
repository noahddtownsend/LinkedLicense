package dev.noahtownsend.linkedlicense.plugin

import kotlin.test.Test
import kotlin.test.assertEquals

class SpmPackageResolvedTest {
    @Test
    fun `parse() reads the v2 flattened pins format`() {
        val json =
            """
            {
              "pins": [
                {
                  "identity": "swift-log",
                  "kind": "remoteSourceControl",
                  "location": "https://github.com/apple/swift-log.git",
                  "state": {
                    "revision": "532d8b529501fb73a2455b179e0bbb6d49b652ed",
                    "version": "1.5.3"
                  }
                }
              ],
              "version": 2
            }
            """.trimIndent()

        val pins = SpmPackageResolved.parse(json)

        assertEquals(1, pins.size)
        assertEquals("swift-log", pins[0].identity)
        assertEquals("https://github.com/apple/swift-log.git", pins[0].repositoryUrl)
        assertEquals("532d8b529501fb73a2455b179e0bbb6d49b652ed", pins[0].revision)
    }

    @Test
    fun `parse() reads the legacy object-wrapped v1 pins format`() {
        val json =
            """
            {
              "object": {
                "pins": [
                  {
                    "package": "swift-log",
                    "repositoryURL": "https://github.com/apple/swift-log.git",
                    "state": {
                      "branch": null,
                      "revision": "532d8b529501fb73a2455b179e0bbb6d49b652ed",
                      "version": "1.5.3"
                    }
                  }
                ]
              },
              "version": 1
            }
            """.trimIndent()

        val pins = SpmPackageResolved.parse(json)

        assertEquals(1, pins.size)
        assertEquals("swift-log", pins[0].identity)
        assertEquals("https://github.com/apple/swift-log.git", pins[0].repositoryUrl)
    }

    @Test
    fun `parse() returns empty list when there are no pins`() {
        assertEquals(emptyList(), SpmPackageResolved.parse("""{"pins": [], "version": 2}"""))
    }

    @Test
    fun `parse() skips a pin with no revision`() {
        val json =
            """
            {"pins": [{"identity": "foo", "location": "https://github.com/example/foo.git", "state": {}}]}
            """.trimIndent()

        assertEquals(emptyList(), SpmPackageResolved.parse(json))
    }

    @Test
    fun `toCoordinate() produces the spm-colon-url moduleId format with the git suffix stripped`() {
        val pin =
            ResolvedSpmPackage(
                identity = "swift-log",
                repositoryUrl = "https://github.com/apple/swift-log.git",
                revision = "532d8b5",
            )

        val coordinate = pin.toCoordinate()

        assertEquals("spm:https://github.com/apple/swift-log", coordinate.moduleId)
        assertEquals("532d8b5", coordinate.version)
    }
}
