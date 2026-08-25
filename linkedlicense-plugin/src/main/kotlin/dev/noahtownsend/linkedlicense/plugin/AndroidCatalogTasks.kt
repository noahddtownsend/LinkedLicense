package dev.noahtownsend.linkedlicense.plugin

import com.android.build.api.variant.AndroidComponentsExtension
import org.gradle.api.Action
import org.gradle.api.Project
import org.jetbrains.kotlin.gradle.dsl.KotlinProjectExtension

/**
 * Registers `generate<Variant>LicenseCatalog` tasks for each Android variant enumerated via
 * AGP's [AndroidComponentsExtension], plus an aggregate `generateLicenseCatalog` task.
 *
 * Like [MultiplatformCatalogTasks] and [JvmCatalogTasks], this is deliberately a plain internal
 * object to keep KGP and AGP type references out of [LinkedLicensePlugin]'s declared method signatures.
 */
internal object AndroidCatalogTasks {
    fun register(
        project: Project,
        extension: LinkedLicenseExtension,
    ) {
        val rootTask =
            project.tasks.register("generateLicenseCatalog") { task ->
                task.group = "linkedlicense"
                task.description = "Generates license catalogs for all Android variants."
            }

        try {
            val androidComponents = project.extensions.findByType(AndroidComponentsExtension::class.java)
            if (androidComponents != null) {
                androidComponents.onVariants { variant ->
                    registerVariantTask(project, extension, variant.name, rootTask)
                }
                return
            }
        } catch (_: Throwable) {
            // Classloader fallback below
        }

        registerVariantsReflectively(project, extension, rootTask)
    }

    private fun registerVariantTask(
        project: Project,
        extension: LinkedLicenseExtension,
        variantName: String,
        rootTask: org.gradle.api.tasks.TaskProvider<*>,
    ) {
        val capitalizedVariantName = variantName.replaceFirstChar { it.uppercase() }
        val taskName = "generate${capitalizedVariantName}LicenseCatalog"
        val outputDir = project.layout.buildDirectory.dir("generated/linkedlicense/$variantName").get().asFile

        val variantTask =
            project.tasks.register(taskName) { task ->
                task.group = "linkedlicense"
                task.description =
                    "Scans the resolved dependency graph for the $variantName variant and generates its license catalog."
                task.outputs.dir(outputDir)

                task.doLast {
                    val configurationName = "${variantName}RuntimeClasspath"
                    val configuration =
                        project.configurations.findByName(configurationName)
                            ?: project.configurations.getByName("${variantName}CompileClasspath")

                    CatalogTaskExecution.generateCatalog(
                        project = project,
                        extension = extension,
                        configuration = configuration,
                        outputDir = outputDir,
                        includeAssets = true,
                        sourceSetName = variantName,
                    )
                }
            }

        rootTask.configure { it.dependsOn(variantTask) }

        wireKotlinSourceSet(project, variantName, variantTask)
    }

    private fun wireKotlinSourceSet(project: Project, variantName: String, taskProvider: Any) {
        try {
            val kotlinExtension = project.extensions.getByType(KotlinProjectExtension::class.java)
            val targetSourceSet =
                kotlinExtension.sourceSets.findByName(variantName)
                    ?: kotlinExtension.sourceSets.findByName("main")
            targetSourceSet?.kotlin?.srcDir(taskProvider)
        } catch (_: Throwable) {
            try {
                JvmCatalogTasks.registerSourceDirReflectively(project, variantName, taskProvider)
            } catch (_: Throwable) {
                try {
                    JvmCatalogTasks.registerSourceDirReflectively(project, "main", taskProvider)
                } catch (_: Throwable) {
                    // Ignore if source set not found
                }
            }
        }
    }

    private fun registerVariantsReflectively(
        project: Project,
        extension: LinkedLicenseExtension,
        rootTask: org.gradle.api.tasks.TaskProvider<*>,
    ) {
        val androidComponents = project.extensions.findByName("androidComponents") ?: return

        val action = Action<Any> { variant ->
            val getNameMethod = variant.javaClass.getMethod("getName")
            val variantName = getNameMethod.invoke(variant) as String
            registerVariantTask(project, extension, variantName, rootTask)
        }

        val onVariantsActionMethod = androidComponents.javaClass.methods.firstOrNull {
            it.name == "onVariants" && it.parameterTypes.size == 2 && Action::class.java.isAssignableFrom(it.parameterTypes[1])
        }

        if (onVariantsActionMethod != null) {
            val selectorMethod = androidComponents.javaClass.methods.firstOrNull {
                it.name == "selector" && it.parameterCount == 0
            }
            val selector = selectorMethod?.invoke(androidComponents)
            val allSelector = selector?.javaClass?.methods?.firstOrNull { it.name == "all" && it.parameterCount == 0 }?.invoke(selector) ?: selector
            onVariantsActionMethod.invoke(androidComponents, allSelector, action)
            return
        }

        val onVariants1Arg = androidComponents.javaClass.methods.firstOrNull {
            it.name == "onVariants" && it.parameterCount == 1
        }
        if (onVariants1Arg != null) {
            onVariants1Arg.invoke(androidComponents, action)
        }
    }
}
