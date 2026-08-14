package dev.noahtownsend.linkedlicense.plugin

import dev.noahtownsend.linkedlicense.plugin.testsupport.FixtureProject
import dev.noahtownsend.linkedlicense.plugin.testsupport.MavenFixtureRepo
import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NoticeCopyingFunctionalTest {
    @TempDir
    lateinit var projectDir: File

    private fun publishNoticeLib(repo: MavenFixtureRepo) {
        repo.publish(
            group = "com.example",
            artifact = "notice-lib",
            version = "1.0",
            licenseName = "Apache-2.0",
            noticeText = "This product includes software from Notice Lib.",
        )
    }

    @Test
    fun `a dependency with a NOTICE file produces a THIRD-PARTY-NOTICES entry`() {
        val repo = MavenFixtureRepo(File(projectDir, "repo"))
        publishNoticeLib(repo)

        val fixture = FixtureProject(projectDir, repo)
        fixture.writeSettings()
        fixture.writeBuildFile(dependencyCoordinates = listOf("com.example:notice-lib:1.0"))

        val result = fixture.run("generateLicenseCatalog")

        assertTrue(result.output.contains("BUILD SUCCESSFUL"), result.output)
        val notices = fixture.thirdPartyNoticesFile()
        assertTrue(notices.exists())
        assertTrue(notices.readText().contains("This product includes software from Notice Lib."))
        assertTrue(notices.readText().contains("com.example:notice-lib:1.0"))
    }

    @Test
    fun `copyRequiredNotices = false skips generating THIRD-PARTY-NOTICES`() {
        val repo = MavenFixtureRepo(File(projectDir, "repo"))
        publishNoticeLib(repo)

        val fixture = FixtureProject(projectDir, repo)
        fixture.writeSettings()
        fixture.writeBuildFile(
            dependencyCoordinates = listOf("com.example:notice-lib:1.0"),
            linkedLicenseBlock = "linkedLicense { copyRequiredNotices = false }",
        )

        val result = fixture.run("generateLicenseCatalog")

        assertTrue(result.output.contains("BUILD SUCCESSFUL"), result.output)
        assertFalse(fixture.thirdPartyNoticesFile().exists())
    }
}
