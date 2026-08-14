package dev.noahtownsend.linkedlicense.plugin

import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.artifacts.result.ResolvedArtifactResult
import org.gradle.api.artifacts.result.ResolvedComponentResult
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.api.tasks.SourceTask
import org.gradle.maven.MavenModule
import org.gradle.maven.MavenPomArtifact
import java.io.File

/**
 * Registers `generateLicenseCatalog` for a Kotlin/JVM project's `main` source set.
 *
 * Full Kotlin Multiplatform source-set iteration (a task per source set, e.g.
 * `generateCommonMainLicenseCatalog`) is out of scope for this pass — see README §2.1.
 * Only `org.jetbrains.kotlin.jvm` projects (a single `main` source set) are supported today.
 */
class LinkedLicensePlugin : Plugin<Project> {
    override fun apply(project: Project) {
        val extension = project.extensions.create("linkedLicense", LinkedLicenseExtension::class.java, project)

        project.plugins.withId("org.jetbrains.kotlin.jvm") {
            registerTask(project, extension)
        }
    }

    private fun registerTask(
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
                    generateCatalog(project, extension, configuration, outputDir)
                }
            }

        // Deliberately avoids referencing Kotlin-Gradle-plugin extension classes
        // (e.g. KotlinJvmProjectExtension): those load on the *consuming* build's own Kotlin
        // plugin classloader, which is a different one from this plugin's, and touching them
        // here throws NoClassDefFoundError/ClassCastException at apply time. SourceTask is
        // core Gradle API and shared across all plugin classloaders, so it's safe to cast to.
        project.tasks.named("compileKotlin") { compileTask ->
            compileTask.dependsOn(task)
            (compileTask as SourceTask).source(outputDir)
        }
    }

    private fun generateCatalog(
        project: Project,
        extension: LinkedLicenseExtension,
        configuration: Configuration,
        outputDir: File,
    ) {
        val root: ResolvedComponentResult = configuration.incoming.resolutionResult.root
        val componentIds = collectResolvedComponents(root)
        val coordinates = componentIds.map { it.toCoordinate() }

        val overrides =
            TomlOverridesParser.parse(extension.overridesFile) { alias -> resolveVersionCatalogAlias(project, alias) }

        val pomInfoCache = resolvePomInfo(project, componentIds)

        val result =
            CatalogGenerator.resolve(
                coordinates = coordinates,
                pomInfoOf = { pomInfoCache[it] ?: PomInfo(emptyList(), null) },
                overrides = overrides,
                failOnCopyleft = extension.failOnCopyleft,
                failOnUnknown = extension.failOnUnknown,
            )

        if (result.unresolved.isNotEmpty()) {
            throw GradleException(
                "generateLicenseCatalog found dependencies with no matched or overridden license:\n" +
                    result.unresolved.joinToString("\n") { "  - $it" } +
                    "\n\nAdd an [overrides] or [ignored] entry for each in ${extension.overridesFile.path}, " +
                    "or set failOnUnknown = false to omit them instead.",
            )
        }

        if (result.copyleftOffenders.isNotEmpty()) {
            throw GradleException(
                "generateLicenseCatalog found copyleft-licensed dependencies not allow-listed:\n" +
                    result.copyleftOffenders.joinToString("\n") { "  - $it" } +
                    "\n\nAdd a [copyleft-allowed] entry in ${extension.overridesFile.path}, " +
                    "or set failOnCopyleft = false.",
            )
        }

        if (result.policyOffenders.isNotEmpty()) {
            throw GradleException(
                "generateLicenseCatalog found dependencies that violate [license-policy]:\n" +
                    result.policyOffenders.joinToString("\n") { "  - ${it.coordinate} (${it.licenseTypeId})" } +
                    "\n\nAdjust the [license-policy] allow/block lists in ${extension.overridesFile.path}.",
            )
        }

        if (result.assetCopyleftOffenders.isNotEmpty()) {
            throw GradleException(
                "generateLicenseCatalog found copyleft-licensed [assets] entries not allow-listed:\n" +
                    result.assetCopyleftOffenders.joinToString("\n") { "  - $it" } +
                    "\n\nAdd a [copyleft-allowed] entry in ${extension.overridesFile.path}, " +
                    "or set failOnCopyleft = false.",
            )
        }

        if (result.assetPolicyOffenders.isNotEmpty()) {
            throw GradleException(
                "generateLicenseCatalog found [assets] entries that violate [license-policy]:\n" +
                    result.assetPolicyOffenders.joinToString("\n") { "  - ${it.assetKey} (${it.licenseTypeId})" } +
                    "\n\nAdjust the [license-policy] allow/block lists in ${extension.overridesFile.path}.",
            )
        }

        if (extension.copyRequiredNotices) {
            writeThirdPartyNotices(project, configuration, coordinates, overrides)
        }

        outputDir.mkdirs()
        val generatedFile = File(outputDir, "dev/noahtownsend/linkedlicense/generated/GeneratedLicenses.kt")
        generatedFile.parentFile.mkdirs()
        generatedFile.writeText(
            renderGeneratedLicensesFile(
                packageName = "dev.noahtownsend.linkedlicense.generated",
                entries = result.entries.map { it.second } + result.assetEntries.map { it.second },
            ),
        )
    }

    private fun resolvePomInfo(
        project: Project,
        componentIds: List<ModuleComponentIdentifier>,
    ): Map<Coordinate, PomInfo> {
        if (componentIds.isEmpty()) {
            return emptyMap()
        }

        val result =
            project.dependencies
                .createArtifactResolutionQuery()
                .forComponents(componentIds)
                .withArtifacts(MavenModule::class.java, MavenPomArtifact::class.java)
                .execute()

        val byCoordinate = mutableMapOf<Coordinate, PomInfo>()

        result.resolvedComponents.forEach { component ->
            val id = component.id as? ModuleComponentIdentifier ?: return@forEach
            val coordinate = Coordinate(id.group, id.module, id.version)
            val pomFile =
                component
                    .getArtifacts(MavenPomArtifact::class.java)
                    .filterIsInstance<ResolvedArtifactResult>()
                    .firstOrNull()
                    ?.file

            byCoordinate[coordinate] = if (pomFile != null) parsePomLicenses(pomFile) else PomInfo(emptyList(), null)
        }

        return byCoordinate
    }

    private fun writeThirdPartyNotices(
        project: Project,
        configuration: Configuration,
        coordinates: List<Coordinate>,
        overrides: OverridesConfig,
    ) {
        val jarsByCoordinate =
            configuration.resolvedConfiguration.resolvedArtifacts.associate { artifact ->
                val id = artifact.moduleVersion.id
                Coordinate(id.group, id.name, id.version) to artifact.file
            }

        val notices =
            coordinates
                .filterNot { overrides.ignored.containsKey(it.moduleId) }
                .mapNotNull { coordinate ->
                    val jar = jarsByCoordinate[coordinate] ?: return@mapNotNull null
                    val notice = readNoticeFromJar(jar) ?: return@mapNotNull null
                    coordinate to notice
                }

        if (notices.isEmpty()) {
            return
        }

        File(project.projectDir, "THIRD-PARTY-NOTICES").writeText(renderThirdPartyNotices(notices))
    }

    private fun resolveVersionCatalogAlias(
        project: Project,
        rawKey: String,
    ): String {
        val parts = rawKey.split(".")

        if (parts.size < 2) {
            throw GradleException("Malformed version-catalog alias '$rawKey' — expected e.g. 'libs.okio'.")
        }

        val catalogName = parts[0]
        val alias = parts.drop(1).joinToString("-")

        val catalogs = project.extensions.getByType(VersionCatalogsExtension::class.java)
        val catalog =
            catalogs.find(catalogName).orElse(null)
                ?: throw GradleException("Unknown version catalog '$catalogName' referenced by '$rawKey'.")
        val library =
            catalog.findLibrary(alias).orElse(null)
                ?: throw GradleException("Unknown alias '$alias' in catalog '$catalogName' referenced by '$rawKey'.")
        val module = library.get().module

        return "${module.group}:${module.name}"
    }
}
