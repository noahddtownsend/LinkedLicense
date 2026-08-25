package dev.noahtownsend.linkedlicense.plugin

import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project

/**
 * Registers `generateLicenseCatalog` for a Kotlin/JVM project's `main` source set (delegated to
 * [JvmCatalogTasks]), `generate<SourceSet>LicenseCatalog` per source set (README §2/§2.1) for a
 * Kotlin Multiplatform project (delegated to [MultiplatformCatalogTasks]), and
 * `generate<Variant>LicenseCatalog` per variant for an Android project (delegated to
 * [AndroidCatalogTasks]).
 *
 * This class - and only this class - must never reference any Kotlin-Gradle-Plugin (KGP) or
 * AGP type in any of its own declared methods, public or private. Gradle decorates every `Plugin`
 * implementation via ASM regardless of which plugin id triggered application, which requires
 * every type referenced in this class's method signatures to be loadable - including for a
 * plain-JVM consumer that never applies `org.jetbrains.kotlin.multiplatform` or
 * `org.jetbrains.kotlin.android` and so never has KGP's multiplatform classes or AGP classes on
 * its classpath at all. Referencing KGP/AGP types from a *different* plain (non-Gradle-decorated)
 * object, like [MultiplatformCatalogTasks], [AndroidCatalogTasks], [JvmCatalogTasks], or
 * [CatalogTaskExecution], is fine - see their class docs for the full runtime-classloader
 * reasoning (why `compileOnly` on `kotlin-gradle-plugin` and `com.android.tools.build:gradle` in
 * `build.gradle.kts` matters there).
 */
class LinkedLicensePlugin : Plugin<Project> {
    override fun apply(project: Project) {
        val extension = project.extensions.create("linkedLicense", LinkedLicenseExtension::class.java, project)

        var kotlinPluginApplied = false

        project.pluginManager.withPlugin("org.jetbrains.kotlin.jvm") {
            kotlinPluginApplied = true
            JvmCatalogTasks.register(project, extension)
        }

        project.pluginManager.withPlugin("org.jetbrains.kotlin.multiplatform") {
            kotlinPluginApplied = true
            MultiplatformCatalogTasks.register(project, extension)
        }

        project.pluginManager.withPlugin("org.jetbrains.kotlin.android") {
            kotlinPluginApplied = true
            AndroidCatalogTasks.register(project, extension)
        }

        project.afterEvaluate {
            if (!kotlinPluginApplied) {
                throw GradleException(
                    "LinkedLicensePlugin requires one of the following Kotlin plugins to be applied: " +
                        "'org.jetbrains.kotlin.jvm', 'org.jetbrains.kotlin.multiplatform', or 'org.jetbrains.kotlin.android'.",
                )
            }
        }
    }
}
