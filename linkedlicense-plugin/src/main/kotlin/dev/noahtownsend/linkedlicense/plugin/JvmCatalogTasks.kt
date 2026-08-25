package dev.noahtownsend.linkedlicense.plugin

import org.gradle.api.Project
import org.jetbrains.kotlin.gradle.dsl.KotlinProjectExtension

/**
 * Registers `generateLicenseCatalog` for a Kotlin/JVM project's `main` source set and wires the
 * output directory into Kotlin's `main` source set.
 *
 * Like [MultiplatformCatalogTasks] and [AndroidCatalogTasks], this is deliberately a plain internal
 * object to keep KGP type references out of [LinkedLicensePlugin]'s declared method signatures.
 */
internal object JvmCatalogTasks {
    fun register(
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
                    CatalogTaskExecution.generateCatalog(
                        project,
                        extension,
                        configuration,
                        outputDir,
                        includeAssets = true,
                        sourceSetName = "main",
                    )
                }
            }

        try {
            val kotlinExtension = project.extensions.getByType(KotlinProjectExtension::class.java)
            kotlinExtension.sourceSets.getByName("main").kotlin.srcDir(task)
        } catch (_: Throwable) {
            registerSourceDirReflectively(project, "main", task)
        }
    }

    internal fun registerSourceDirReflectively(project: Project, sourceSetName: String, taskProvider: Any) {
        val kotlinExtension = project.extensions.getByName("kotlin")
        val sourceSets = kotlinExtension.javaClass.getMethod("getSourceSets").invoke(kotlinExtension)
        val getByName = sourceSets.javaClass.getMethod("getByName", String::class.java)
        val sourceSet = getByName.invoke(sourceSets, sourceSetName)
        val getKotlin = sourceSet.javaClass.getMethod("getKotlin").invoke(sourceSet)
        val srcDirMethod = getKotlin.javaClass.methods.first { it.name == "srcDir" && it.parameterCount == 1 }
        srcDirMethod.invoke(getKotlin, taskProvider)
    }
}
