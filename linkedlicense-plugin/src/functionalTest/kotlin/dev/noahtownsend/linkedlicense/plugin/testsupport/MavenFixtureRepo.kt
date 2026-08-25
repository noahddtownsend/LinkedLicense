package dev.noahtownsend.linkedlicense.plugin.testsupport

import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Builds a minimal, standard-layout Maven repository on disk at test time (no binary fixtures
 * committed to git) - just enough POM + jar structure for Gradle to resolve fixed-version
 * dependencies against it.
 */
class MavenFixtureRepo(
    val dir: File,
) {
    /** A dependency reference for [publish]'s `dependencies` parameter. */
    data class Dep(
        val group: String,
        val artifact: String,
        val version: String,
    )

    fun publish(
        group: String,
        artifact: String,
        version: String,
        projectName: String? = null,
        parentGroup: String? = null,
        parentArtifact: String? = null,
        parentVersion: String? = null,
        licenseName: String? = null,
        licenseUrl: String? = null,
        organizationName: String? = null,
        developerName: String? = null,
        dependencies: List<Dep> = emptyList(),
        noticeText: String? = null,
        packaging: String = "jar",
    ) {
        val moduleDir = File(dir, "${group.replace('.', '/')}/$artifact/$version").apply { mkdirs() }

        val parentXml =
            if (parentGroup != null && parentArtifact != null && parentVersion != null) {
                """
                |  <parent>
                |    <groupId>$parentGroup</groupId>
                |    <artifactId>$parentArtifact</artifactId>
                |    <version>$parentVersion</version>
                |  </parent>
                """.trimMargin()
            } else {
                ""
            }

        val nameXml = if (projectName != null) "  <name>$projectName</name>" else ""

        val licensesXml =
            if (licenseName != null) {
                """
                |  <licenses>
                |    <license>
                |      <name>$licenseName</name>
                |      ${if (licenseUrl != null) "<url>$licenseUrl</url>" else ""}
                |    </license>
                |  </licenses>
                """.trimMargin()
            } else {
                ""
            }

        val organizationXml =
            if (organizationName != null) {
                "  <organization><name>$organizationName</name></organization>"
            } else {
                ""
            }

        val developersXml =
            if (developerName != null) {
                """
                |  <developers>
                |    <developer>
                |      <name>$developerName</name>
                |    </developer>
                |  </developers>
                """.trimMargin()
            } else {
                ""
            }

        val dependenciesXml =
            if (dependencies.isNotEmpty()) {
                """
                |  <dependencies>
                ${dependencies.joinToString("\n") {
                    "|    <dependency><groupId>${it.group}</groupId><artifactId>${it.artifact}</artifactId>" +
                        "<version>${it.version}</version></dependency>"
                }}
                |  </dependencies>
                """.trimMargin()
            } else {
                ""
            }

        File(moduleDir, "$artifact-$version.pom").writeText(
            """
            |<?xml version="1.0" encoding="UTF-8"?>
            |<project xmlns="http://maven.apache.org/POM/4.0.0">
            |  <modelVersion>4.0.0</modelVersion>
            $parentXml
            |  <groupId>$group</groupId>
            |  <artifactId>$artifact</artifactId>
            |  <version>$version</version>
            |  <packaging>$packaging</packaging>
            $nameXml
            $organizationXml
            $developersXml
            $licensesXml
            $dependenciesXml
            |</project>
            """.trimMargin(),
        )

        if (packaging != "pom") {
            File(moduleDir, "$artifact-$version.jar").outputStream().use { fileOut ->
                ZipOutputStream(fileOut).use { zip ->
                    zip.putNextEntry(ZipEntry("META-INF/MANIFEST.MF"))
                    zip.write("Manifest-Version: 1.0\n".toByteArray())
                    zip.closeEntry()

                    if (noticeText != null) {
                        zip.putNextEntry(ZipEntry("NOTICE"))
                        zip.write(noticeText.toByteArray())
                        zip.closeEntry()
                    }
                }
            }
        }
    }
}
