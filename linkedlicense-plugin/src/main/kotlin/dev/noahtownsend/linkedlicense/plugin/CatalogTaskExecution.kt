package dev.noahtownsend.linkedlicense.plugin

import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.artifacts.result.ResolvedArtifactResult
import org.gradle.maven.MavenModule
import org.gradle.maven.MavenPomArtifact
import java.io.File

/**
 * The `generateLicenseCatalog`/`generate<SourceSet>LicenseCatalog` task bodies (resolution,
 * fail-fast checks, notice copying, codegen), shared by both the plain-`kotlin.jvm` path
 * ([LinkedLicensePlugin]) and the Kotlin Multiplatform path ([MultiplatformCatalogTasks]).
 *
 * Deliberately a plain object with no Gradle-managed supertype (not a `Plugin`, `Task`, or
 * extension): Gradle decorates every declared `Plugin` implementation's methods (including
 * private ones) via ASM, which requires all referenced types - including Kotlin-Gradle-Plugin
 * ones - to be loadable even for a plain-JVM consumer that never applies
 * `org.jetbrains.kotlin.multiplatform` and so never has those classes on its classpath at all.
 * Keeping KGP-type-referencing code (see [MultiplatformCatalogTasks]) out of [LinkedLicensePlugin]
 * itself, and confined to plain helper objects like this one, avoids that decoration failure.
 */
internal object CatalogTaskExecution {
    fun generateCatalog(
        project: Project,
        extension: LinkedLicenseExtension,
        configuration: Configuration,
        outputDir: File,
        includeAssets: Boolean,
    ) {
        val componentIds = collectResolvedComponents(configuration.incoming.resolutionResult.root)
        generateCatalogFromComponents(project, extension, componentIds, listOf(configuration), outputDir, includeAssets)
    }

    /**
     * `commonMain`'s catalog (README §2): every platform target's classpath is resolved and
     * dedup'd independently, then the resulting coordinate sets are unioned by resolved
     * coordinate before running the pure resolution core once - so a dependency that exists on
     * only one target still shows up, and one shared by all targets appears exactly once.
     */
    fun generateUnionCatalog(
        project: Project,
        extension: LinkedLicenseExtension,
        configurations: List<Configuration>,
        outputDir: File,
    ) {
        val componentsByCoordinate = linkedMapOf<Coordinate, ModuleComponentIdentifier>()

        configurations.forEach { configuration ->
            collectResolvedComponents(configuration.incoming.resolutionResult.root).forEach { id ->
                componentsByCoordinate.putIfAbsent(id.toCoordinate(), id)
            }
        }

        generateCatalogFromComponents(
            project = project,
            extension = extension,
            componentIds = componentsByCoordinate.values.toList(),
            configurations = configurations,
            outputDir = outputDir,
            includeAssets = true,
        )
    }

    private fun generateCatalogFromComponents(
        project: Project,
        extension: LinkedLicenseExtension,
        componentIds: List<ModuleComponentIdentifier>,
        configurations: List<Configuration>,
        outputDir: File,
        includeAssets: Boolean,
    ) {
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
                includeAssets = includeAssets,
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
            writeThirdPartyNotices(project, configurations, coordinates, overrides)
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
        configurations: List<Configuration>,
        coordinates: List<Coordinate>,
        overrides: OverridesConfig,
    ) {
        val jarsByCoordinate = mutableMapOf<Coordinate, File>()

        configurations.forEach { configuration ->
            configuration.resolvedConfiguration.resolvedArtifacts.forEach { artifact ->
                val id = artifact.moduleVersion.id
                jarsByCoordinate.putIfAbsent(Coordinate(id.group, id.name, id.version), artifact.file)
            }
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

    fun resolveVersionCatalogAlias(
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
