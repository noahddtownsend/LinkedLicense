package dev.noahtownsend.linkedlicense.plugin

import org.gradle.testkit.runner.GradleRunner
import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * De-risking sanity check for README §2/§2.1: applying the plugin alongside
 * `org.jetbrains.kotlin.multiplatform` must not throw at apply time. Earlier attempts to
 * reference KGP multiplatform extension classes threw NoClassDefFoundError/ClassCastException
 * because the plugin module never declared a `compileOnly` dependency on the Kotlin Gradle
 * Plugin API - see `linkedlicense-plugin/build.gradle.kts`.
 */
class MultiplatformApplyFunctionalTest {
    @TempDir
    lateinit var projectDir: File

    @Test
    fun `applying the plugin alongside kotlin multiplatform does not throw`() {
        File(projectDir, "settings.gradle.kts").writeText(
            """
            |rootProject.name = "fixture"
            |
            |pluginManagement {
            |    repositories {
            |        gradlePluginPortal()
            |        mavenCentral()
            |    }
            |}
            """.trimMargin(),
        )

        File(projectDir, "build.gradle.kts").writeText(
            """
            |plugins {
            |    kotlin("multiplatform") version "2.3.21"
            |    id("dev.noahtownsend.linkedlicense")
            |}
            |
            |repositories {
            |    mavenCentral()
            |}
            |
            |kotlin {
            |    jvm()
            |}
            """.trimMargin(),
        )

        val result =
            GradleRunner
                .create()
                .withProjectDir(projectDir)
                .withPluginClasspath()
                .withArguments("tasks", "--stacktrace")
                .forwardOutput()
                .build()

        assertTrue(result.output.contains("BUILD SUCCESSFUL"), result.output)
    }
}
