package dev.noahtownsend.linkedlicense.plugin

import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilation
import org.jetbrains.kotlin.gradle.plugin.KotlinPlatformType

/**
 * Registers one `generate<SourceSet>LicenseCatalog` task per real platform target's `main`
 * compilation (README §2.1: `generateJvmMainLicenseCatalog`, `generateAndroidMainLicenseCatalog`,
 * `generateIosMainLicenseCatalog`, etc.), plus `generateCommonMainLicenseCatalog` as the union of
 * every platform target's resolved coordinates (README §2).
 *
 * Deliberately a plain object, not part of [LinkedLicensePlugin] itself — see
 * [CatalogTaskExecution]'s class doc for why referencing Kotlin-Gradle-Plugin (KGP) types has to
 * stay out of the `Plugin<Project>` implementation's own declared methods. Referencing them here
 * is otherwise safe: `linkedlicense-plugin/build.gradle.kts` declares a `compileOnly` dependency
 * on `kotlin-gradle-plugin`, so this plugin's jar never bundles its own copy of those classes -
 * at runtime it binds against whichever KGP classes the *consuming* project's own
 * `plugins { id("org.jetbrains.kotlin.multiplatform") }` provides, on Gradle's shared plugin
 * classloader for co-applied plugins.
 */
internal object MultiplatformCatalogTasks {
    fun register(
        project: Project,
        extension: LinkedLicenseExtension,
    ) {
        val kotlinExtension = project.extensions.getByType(KotlinMultiplatformExtension::class.java)
        val targetConfigurations = mutableListOf<Configuration>()

        // The synthetic "metadata" target (platformType == common) is skipped - it isn't one
        // real compilation/classpath, it's what the commonMain union below stands in for.
        kotlinExtension.targets.matching { it.platformType != KotlinPlatformType.common }.all { target ->
            val compilation = target.compilations.findByName(KotlinCompilation.MAIN_COMPILATION_NAME) ?: return@all
            val sourceSetName = compilation.defaultSourceSet.name

            // Native targets (e.g. iosX64) have no separate runtime classpath - only
            // compileDependencyConfigurationName resolves for them. JVM-like targets have both;
            // prefer the runtime one, matching the plain-JVM path's use of "runtimeClasspath".
            val configurationName =
                compilation.runtimeDependencyConfigurationName ?: compilation.compileDependencyConfigurationName
            val configuration = project.configurations.getByName(configurationName)

            targetConfigurations += configuration
            registerSourceSetTask(project, extension, kotlinExtension, sourceSetName, configuration)
        }

        registerCommonMainTask(project, extension, kotlinExtension, targetConfigurations)
    }

    private fun registerSourceSetTask(
        project: Project,
        extension: LinkedLicenseExtension,
        kotlinExtension: KotlinMultiplatformExtension,
        sourceSetName: String,
        configuration: Configuration,
    ) {
        val outputDir = project.layout.buildDirectory.dir("generated/linkedlicense/$sourceSetName").get().asFile
        val taskName = "generate${sourceSetName.replaceFirstChar { it.uppercase() }}LicenseCatalog"

        val task =
            project.tasks.register(taskName) { task ->
                task.group = "linkedlicense"
                task.description =
                    "Scans the resolved dependency graph for the $sourceSetName source set and generates its license catalog."
                task.outputs.dir(outputDir)

                // [assets] entries (README §3.7) aren't tied to any one target - they belong to
                // commonMain only, not repeated in every platform target's own catalog.
                task.doLast {
                    CatalogTaskExecution.generateCatalog(project, extension, configuration, outputDir, includeAssets = false)
                }
            }

        kotlinExtension.sourceSets.getByName(sourceSetName).kotlin.srcDir(task)
    }

    private fun registerCommonMainTask(
        project: Project,
        extension: LinkedLicenseExtension,
        kotlinExtension: KotlinMultiplatformExtension,
        targetConfigurations: List<Configuration>,
    ) {
        val outputDir = project.layout.buildDirectory.dir("generated/linkedlicense/commonMain").get().asFile

        val task =
            project.tasks.register("generateCommonMainLicenseCatalog") { task ->
                task.group = "linkedlicense"
                task.description =
                    "Generates the license catalog for commonMain - the union of every platform target's " +
                        "resolved coordinates, per README §2."
                task.outputs.dir(outputDir)

                task.doLast {
                    CatalogTaskExecution.generateUnionCatalog(project, extension, targetConfigurations, outputDir)
                }
            }

        kotlinExtension.sourceSets.getByName("commonMain").kotlin.srcDir(task)
    }
}
