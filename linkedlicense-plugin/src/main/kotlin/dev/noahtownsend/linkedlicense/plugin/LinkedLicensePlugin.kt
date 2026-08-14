package dev.noahtownsend.linkedlicense.plugin

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.SourceTask

/**
 * Registers `generateLicenseCatalog` for a Kotlin/JVM project's `main` source set, and
 * `generate<SourceSet>LicenseCatalog` per source set (README §2/§2.1) for a Kotlin
 * Multiplatform project (delegated to [MultiplatformCatalogTasks]).
 *
 * This class - and only this class - must never reference any Kotlin-Gradle-Plugin (KGP) type
 * in any of its own declared methods, public or private. Gradle decorates every `Plugin`
 * implementation via ASM regardless of which plugin id triggered application, which requires
 * every type referenced in this class's method signatures to be loadable - including for a
 * plain-JVM consumer that never applies `org.jetbrains.kotlin.multiplatform` and so never has
 * KGP's multiplatform classes on its classpath at all. Referencing KGP types from a *different*
 * plain (non-Gradle-decorated) object, like [MultiplatformCatalogTasks] or
 * [CatalogTaskExecution], is fine - see their class docs for the full runtime-classloader
 * reasoning (why `compileOnly` on `kotlin-gradle-plugin` in `build.gradle.kts` matters there).
 */
class LinkedLicensePlugin : Plugin<Project> {
    override fun apply(project: Project) {
        val extension = project.extensions.create("linkedLicense", LinkedLicenseExtension::class.java, project)

        project.plugins.withId("org.jetbrains.kotlin.jvm") {
            registerJvmTask(project, extension)
        }

        project.plugins.withId("org.jetbrains.kotlin.multiplatform") {
            MultiplatformCatalogTasks.register(project, extension)
        }
    }

    private fun registerJvmTask(
        project: Project,
        extension: LinkedLicenseExtension,
    ) {
        val outputDir = project.layout.buildDirectory.dir("generated/linkedlicense/main").get().asFile

        val task =
            project.tasks.register("generateLicenseCatalog") { task ->
                task.group = "linkedlicense"
                task.description = "Scans the resolved dependency graph and generates the license catalog."
                task.outputs.dir(outputDir)

                task.doLast {
                    val configuration = project.configurations.getByName("runtimeClasspath")
                    CatalogTaskExecution.generateCatalog(project, extension, configuration, outputDir, includeAssets = true)
                }
            }

        // SourceTask is core Gradle API and shared across all plugin classloaders, so it's safe
        // to cast to even without the compileOnly fix - unlike KGP types, it doesn't need it.
        project.tasks.named("compileKotlin") { compileTask ->
            compileTask.dependsOn(task)
            (compileTask as SourceTask).source(outputDir)
        }
    }
}
