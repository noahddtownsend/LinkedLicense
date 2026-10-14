package dev.noahtownsend.linkedlicense.plugin

import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilation
import org.jetbrains.kotlin.gradle.plugin.KotlinPlatformType
import org.jetbrains.kotlin.gradle.plugin.KotlinTarget

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

        // The synthetic "metadata" target (platformType == common) is skipped - it isn't one
        // real compilation/classpath, it's what the commonMain union below stands in for.
        kotlinExtension.targets.matching { it.platformType != KotlinPlatformType.common }.all { target ->
            if (target.platformType == KotlinPlatformType.androidJvm) {
                registerAndroidTarget(project, extension, kotlinExtension, target)
                return@all
            }

            val compilation = target.compilations.findByName(KotlinCompilation.MAIN_COMPILATION_NAME)
            if (compilation == null) {
                project.logger.warn("linkedlicense: target '${target.name}' has no 'main' compilation and was skipped.")
                return@all
            }

            val sourceSetName = compilation.defaultSourceSet.name

            // Native targets (e.g. iosX64) have no separate runtime classpath - only
            // compileDependencyConfigurationName resolves for them. JVM-like targets have both;
            // prefer the runtime one, matching the plain-JVM path's use of "runtimeClasspath".
            registerSourceSetTask(project, extension, kotlinExtension, sourceSetName) {
                val configurationName =
                    compilation.runtimeDependencyConfigurationName ?: compilation.compileDependencyConfigurationName
                listOfNotNull(project.configurations.findByName(configurationName))
            }
        }

        registerCommonMainTask(project, extension, kotlinExtension)
    }

    private fun registerAndroidTarget(
        project: Project,
        extension: LinkedLicenseExtension,
        kotlinExtension: KotlinMultiplatformExtension,
        target: KotlinTarget,
    ) {
        val sourceSetName = "androidMain"
        val outputDir = project.layout.buildDirectory.dir("generated/linkedlicense/$sourceSetName").get().asFile
        val taskName = "generateAndroidMainLicenseCatalog"

        val task =
            project.tasks.register(taskName, GenerateLicenseCatalogTask::class.java) { task ->
                CatalogTaskExecution.configureTask(
                    task = task,
                    project = project,
                    extension = extension,
                    configurationsProvider = {
                        findAndroidConfigurations(project, target)
                    },
                    sourceSetName = sourceSetName,
                    outputDir = outputDir,
                    includeAssets = false,
                    includeNotices = false,
                    npmNodeModulesDir = null,
                )
            }

        kotlinExtension.sourceSets.matching { it.name == sourceSetName }.all { sourceSet ->
            sourceSet.kotlin.srcDir(task.flatMap { it.outputDir })
        }
    }

    private fun registerSourceSetTask(
        project: Project,
        extension: LinkedLicenseExtension,
        kotlinExtension: KotlinMultiplatformExtension,
        sourceSetName: String,
        configurationsProvider: () -> List<Configuration>,
    ) {
        val outputDir = project.layout.buildDirectory.dir("generated/linkedlicense/$sourceSetName").get().asFile
        val taskName = "generate${sourceSetName.replaceFirstChar { it.uppercase() }}LicenseCatalog"

        val task =
            project.tasks.register(taskName, GenerateLicenseCatalogTask::class.java) { task ->
                CatalogTaskExecution.configureTask(
                    task = task,
                    project = project,
                    extension = extension,
                    configurationsProvider = configurationsProvider,
                    sourceSetName = sourceSetName,
                    outputDir = outputDir,
                    includeAssets = false,
                    includeNotices = false,
                    npmNodeModulesDir = NpmNodeModulesLocator.locate(project, sourceSetName),
                )
            }

        kotlinExtension.sourceSets.matching { it.name == sourceSetName }.all { sourceSet ->
            sourceSet.kotlin.srcDir(task.flatMap { it.outputDir })
        }
    }

    private fun registerCommonMainTask(
        project: Project,
        extension: LinkedLicenseExtension,
        kotlinExtension: KotlinMultiplatformExtension,
    ) {
        val outputDir = project.layout.buildDirectory.dir("generated/linkedlicense/commonMain").get().asFile

        val task =
            project.tasks.register("generateCommonMainLicenseCatalog", GenerateLicenseCatalogTask::class.java) { task ->
                CatalogTaskExecution.configureTask(
                    task = task,
                    project = project,
                    extension = extension,
                    configurationsProvider = {
                        collectTargetConfigurations(project, kotlinExtension)
                    },
                    sourceSetName = "commonMain",
                    outputDir = outputDir,
                    includeAssets = true,
                    npmNodeModulesDir = null,
                )
            }

        kotlinExtension.sourceSets.matching { it.name == "commonMain" }.all { sourceSet ->
            sourceSet.kotlin.srcDir(task.flatMap { it.outputDir })
        }
    }

    private fun collectTargetConfigurations(
        project: Project,
        kotlinExtension: KotlinMultiplatformExtension,
    ): List<Configuration> {
        val configs = mutableListOf<Configuration>()
        kotlinExtension.targets.matching { it.platformType != KotlinPlatformType.common }.forEach { target ->
            if (target.platformType == KotlinPlatformType.androidJvm) {
                configs += findAndroidConfigurations(project, target)
            } else {
                val compilation = target.compilations.findByName(KotlinCompilation.MAIN_COMPILATION_NAME)
                if (compilation != null) {
                    val configName =
                        compilation.runtimeDependencyConfigurationName ?: compilation.compileDependencyConfigurationName
                    project.configurations.findByName(configName)?.let { configs += it }
                }
            }
        }
        return configs
    }

    private fun findAndroidConfigurations(
        project: Project,
        target: KotlinTarget,
    ): List<Configuration> {
        val nonTestCompilations = target.compilations.filter { compilation ->
            !compilation.name.contains("Test", ignoreCase = true)
        }
        val releaseCompilations = nonTestCompilations.filter { compilation ->
            compilation.name.contains("Release", ignoreCase = true)
        }
        val selected = releaseCompilations.ifEmpty { nonTestCompilations }
        return selected.mapNotNull { compilation ->
            val configName =
                compilation.runtimeDependencyConfigurationName ?: compilation.compileDependencyConfigurationName
            project.configurations.findByName(configName)
        }
    }
}
