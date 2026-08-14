package dev.noahtownsend.linkedlicense.plugin

import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PodspecLicensesTest {
    private val tempDir = Files.createTempDirectory("linkedlicense-podspec-test").toFile()

    @AfterTest
    fun cleanup() {
        tempDir.deleteRecursively()
    }

    @Test
    fun `parseJsonLicense() reads a plain string license field`() {
        val license = PodspecLicenses.parseJsonLicense("""{"name": "Alamofire", "license": "MIT"}""")

        assertEquals(PomLicense(name = "MIT", url = null), license)
    }

    @Test
    fun `parseJsonLicense() reads the type-file-text object form`() {
        val license =
            PodspecLicenses.parseJsonLicense(
                """{"name": "Foo", "license": {"type": "MIT", "file": "LICENSE"}}""",
            )

        assertEquals(PomLicense(name = "MIT", url = "LICENSE"), license)
    }

    @Test
    fun `parseJsonLicense() returns null when license is absent`() {
        assertNull(PodspecLicenses.parseJsonLicense("""{"name": "Foo"}"""))
    }

    @Test
    fun `parseRubyLicense() reads a bare string assignment`() {
        val podspec =
            """
            Pod::Spec.new do |s|
              s.name         = "Alamofire"
              s.license      = 'MIT'
              s.version      = "5.6.4"
            end
            """.trimIndent()

        assertEquals(PomLicense(name = "MIT", url = null), PodspecLicenses.parseRubyLicense(podspec))
    }

    @Test
    fun `parseRubyLicense() reads a hash-literal assignment with symbol arrow syntax`() {
        val podspec =
            """
            Pod::Spec.new do |s|
              s.license = { :type => 'MIT', :file => 'LICENSE' }
            end
            """.trimIndent()

        assertEquals(PomLicense(name = "MIT", url = null), PodspecLicenses.parseRubyLicense(podspec))
    }

    @Test
    fun `parseRubyLicense() reads a hash-literal assignment with modern symbol-colon syntax`() {
        val podspec = """s.license = { type: 'Apache-2.0', text: 'Copyright ...' }"""

        assertEquals(PomLicense(name = "Apache-2.0", url = null), PodspecLicenses.parseRubyLicense(podspec))
    }

    @Test
    fun `parseRubyLicense() returns null when there is no license assignment`() {
        assertNull(PodspecLicenses.parseRubyLicense("s.name = 'Foo'"))
    }

    @Test
    fun `locatePodspec() finds an unsharded spec-repo-cache-style podspec json`() {
        val specsRepo = tempDir.resolve("repos").resolve("trunk")
        val podDir = specsRepo.resolve("Alamofire").resolve("5.6.4")
        podDir.mkdirs()
        File(podDir, "Alamofire.podspec.json").writeText("""{"license": "MIT"}""")

        val located = PodspecLicenses.locatePodspec(ResolvedPod("Alamofire", "5.6.4"), specsRepo)

        assertEquals(File(podDir, "Alamofire.podspec.json"), located)
    }

    @Test
    fun `locatePodspec() finds a hash-sharded trunk-cdn-style podspec json`() {
        val specsRepo = tempDir.resolve("repos").resolve("trunk")
        val podDir = specsRepo.resolve("a1").resolve("b2").resolve("c3").resolve("Alamofire").resolve("5.6.4")
        podDir.mkdirs()
        File(podDir, "Alamofire.podspec.json").writeText("""{"license": "MIT"}""")

        val located = PodspecLicenses.locatePodspec(ResolvedPod("Alamofire", "5.6.4"), specsRepo)

        assertEquals(File(podDir, "Alamofire.podspec.json"), located)
    }

    @Test
    fun `locatePodspec() returns null when nothing matches`() {
        assertNull(PodspecLicenses.locatePodspec(ResolvedPod("Missing", "1.0.0"), tempDir))
    }

    @Test
    fun `parseLicense() dispatches to json or ruby parsing based on file extension`() {
        val jsonFile = File(tempDir, "Foo.podspec.json").apply { writeText("""{"license": "MIT"}""") }
        val rubyFile = File(tempDir, "Bar.podspec").apply { writeText("s.license = 'ISC'") }

        assertEquals(PomLicense(name = "MIT", url = null), PodspecLicenses.parseLicense(jsonFile))
        assertEquals(PomLicense(name = "ISC", url = null), PodspecLicenses.parseLicense(rubyFile))
    }
}
