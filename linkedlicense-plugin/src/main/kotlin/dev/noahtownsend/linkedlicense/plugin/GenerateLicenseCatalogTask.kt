package dev.noahtownsend.linkedlicense.plugin

import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import java.io.File

/**
 * A typed, configuration-cache-compatible Gradle task that generates the license catalog
 * for a source set or union of source sets.
 *
 * Inputs (dependencies, POM info, JAR files, and configuration properties) are captured
 * via Gradle [Property], [ListProperty], [MapProperty], and [ConfigurableFileCollection],
 * completely avoiding execution-time access to [org.gradle.api.Project] or
 * [org.gradle.api.artifacts.Configuration].
 */
abstract class GenerateLicenseCatalogTask : DefaultTask() {

    @get:Input
    abstract val sourceSetName: Property<String>

    @get:Input
    abstract val coordinates: ListProperty<Coordinate>

    @get:Input
    abstract val pomInfos: MapProperty<Coordinate, PomInfo>

    @get:Input
    abstract val jarsByCoordinate: MapProperty<Coordinate, File>

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.NAME_ONLY)
    abstract val jarFiles: ConfigurableFileCollection

    @get:Input
    abstract val versionCatalogAliases: MapProperty<String, String>

    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val overridesFile: RegularFileProperty

    @get:Input
    abstract val failOnCopyleft: Property<Boolean>

    @get:Optional
    @get:Input
    abstract val failOnSoftCopyleft: Property<Boolean>

    @get:Input
    abstract val failOnUnknown: Property<Boolean>

    @get:Input
    abstract val includeAssets: Property<Boolean>

    @get:Input
    abstract val bestEffortLicenseFetch: Property<Boolean>

    @get:Input
    abstract val autoPopulate: Property<Boolean>

    @get:Input
    abstract val copyRequiredNotices: Property<Boolean>

    @get:Optional
    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val npmNodeModulesDir: DirectoryProperty

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @get:Optional
    @get:OutputFile
    abstract val noticesOutputFile: RegularFileProperty

    @TaskAction
    fun generate() {
        CatalogTaskExecution.generateCatalogFromTask(
            logger = logger,
            sourceSetName = sourceSetName.get(),
            coordinates = coordinates.get(),
            pomInfos = pomInfos.get(),
            jarsByCoordinate = jarsByCoordinate.get(),
            versionCatalogAliases = versionCatalogAliases.get(),
            overridesFile = overridesFile.get().asFile,
            failOnCopyleft = failOnCopyleft.get(),
            failOnSoftCopyleft = failOnSoftCopyleft.orNull,
            failOnUnknown = failOnUnknown.get(),
            includeAssets = includeAssets.get(),
            bestEffortLicenseFetch = bestEffortLicenseFetch.get(),
            autoPopulate = autoPopulate.get(),
            copyRequiredNotices = copyRequiredNotices.get(),
            npmNodeModulesDir = npmNodeModulesDir.orNull?.asFile,
            outputDir = outputDir.get().asFile,
            noticesOutputFile = noticesOutputFile.orNull?.asFile,
        )
    }
}
