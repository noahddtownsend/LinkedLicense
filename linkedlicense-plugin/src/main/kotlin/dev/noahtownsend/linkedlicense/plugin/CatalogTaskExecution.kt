package dev.noahtownsend.linkedlicense.plugin

import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.artifacts.component.ProjectComponentIdentifier
import org.gradle.api.artifacts.result.ResolvedArtifactResult
import org.gradle.api.artifacts.type.ArtifactTypeDefinition
import org.gradle.maven.MavenModule
import org.gradle.maven.MavenPomArtifact
import dev.noahtownsend.linkedlicense.License
import kotlin.reflect.KClass
import java.io.File
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

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
        /**
         * npm scanning (README §2.3): the `node_modules` directory KGP installed this source
         * set's declared `npm(...)` dependencies into, if this is a `jsMain`/`wasmJsMain`
         * source set with any. `null`/non-existent means "nothing to scan" - not an error.
         */
        npmNodeModulesDir: File? = null,
        /**
         * The Kotlin source set this catalog is generated for (e.g. `"main"`, `"jvmMain"`).
         * Lowercased and appended to the generated file's package (README §2.1 step 7), so that
         * `commonMain`'s generated file — merged by KGP's default hierarchy template into every
         * platform target's own compilation — never redeclares the same `object
         * GeneratedLicenses` as that target's own generated file.
         */
        sourceSetName: String,
    ) {
        OverridesFileScaffold.ensureExists(extension.overridesFile)

        val componentIds = collectResolvedComponents(configuration.incoming.resolutionResult.root)
        val npmPackageInfo = npmNodeModulesDir?.let { scanNpmPackageInfo(it) }.orEmpty()

        generateCatalogFromComponents(
            project = project,
            extension = extension,
            componentIds = componentIds,
            extraCoordinates = npmPackageInfo.keys.toList(),
            extraPomInfo = npmPackageInfo.mapValues { (_, info) -> info.toPomInfo() },
            configurations = listOf(configuration),
            outputDir = outputDir,
            includeAssets = includeAssets,
            sourceSetName = sourceSetName,
        )
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
        OverridesFileScaffold.ensureExists(extension.overridesFile)

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
            extraCoordinates = emptyList(),
            extraPomInfo = emptyMap(),
            configurations = configurations,
            outputDir = outputDir,
            includeAssets = true,
            sourceSetName = "commonMain",
        )
    }

    private fun generateCatalogFromComponents(
        project: Project,
        extension: LinkedLicenseExtension,
        componentIds: List<ModuleComponentIdentifier>,
        /** Non-Maven coordinates (npm/CocoaPods/SPM, README §2.3) folded into the same pipeline. */
        extraCoordinates: List<Coordinate>,
        /** [PomInfo]-shaped license/repo info for each of [extraCoordinates]. */
        extraPomInfo: Map<Coordinate, PomInfo>,
        configurations: List<Configuration>,
        outputDir: File,
        includeAssets: Boolean,
        /** README §2.1 step 7 — disambiguates the generated package per source set. */
        sourceSetName: String,
    ) {
        val coordinates = componentIds.map { it.toCoordinate() } + extraCoordinates

        val overrides =
            TomlOverridesParser.parse(extension.overridesFile) { alias -> resolveVersionCatalogAlias(project, alias) }

        val pomInfoCache = resolvePomInfo(project, componentIds) + extraPomInfo

        val bestEffortFetch: ((String, String) -> KClass<out License>?)? =
            if (extension.bestEffortLicenseFetch) {
                { repoUrl, ref -> BestEffortLicenseFetch.guessLicense(repoUrl, ref, ::fetchUrlBody) }
            } else {
                null
            }

        val result =
            CatalogGenerator.resolve(
                coordinates = coordinates,
                pomInfoOf = { pomInfoCache[it] ?: PomInfo(emptyList(), null) },
                overrides = overrides,
                failOnCopyleft = extension.failOnCopyleft,
                failOnSoftCopyleft = extension.failOnSoftCopyleft,
                failOnUnknown = extension.failOnUnknown,
                includeAssets = includeAssets,
                bestEffortFetch = bestEffortFetch,
                onBestGuess = { coordinate, kClass ->
                    if (!overrides.suppressBestGuessWarnings.containsKey(coordinate.moduleId)) {
                        project.logger.warn(
                            "linkedlicense: best-effort guess for ${coordinate.moduleId} -> " +
                                "${BuiltInLicenses.simpleNameOf(kClass)} (unverified - fetched from its repository " +
                                "and pattern-matched, not read from a structured license field). Silence this " +
                                "warning with a [suppress-best-guess-warnings] entry in ${extension.overridesFile.path} " +
                                "once verified.",
                        )
                    }
                },
                autoPopulate = extension.autoPopulate,
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

        val sourceSetPackageSegment = sourceSetName.lowercase()
        outputDir.mkdirs()
        val generatedFile =
            File(outputDir, "dev/noahtownsend/linkedlicense/generated/$sourceSetPackageSegment/GeneratedLicenses.kt")
        generatedFile.parentFile.mkdirs()
        generatedFile.writeText(
            renderGeneratedLicensesFile(
                packageName = "dev.noahtownsend.linkedlicense.generated.$sourceSetPackageSegment",
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

        val parsedPoms = mutableMapOf<Coordinate, PomInfo>()

        result.resolvedComponents.forEach { component ->
            val id = component.id as? ModuleComponentIdentifier ?: return@forEach
            val coordinate = Coordinate(id.group, id.module, id.version)
            val pomFile =
                component
                    .getArtifacts(MavenPomArtifact::class.java)
                    .filterIsInstance<ResolvedArtifactResult>()
                    .firstOrNull()
                    ?.file

            parsedPoms[coordinate] = if (pomFile != null) parsePomLicenses(pomFile) else PomInfo(licenses = emptyList(), organizationName = null)
        }

        val parentPomCache = mutableMapOf<Coordinate, PomInfo>()

        fun fetchParentPom(ref: ParentPomRef): PomInfo? {
            val parentCoord = Coordinate(ref.groupId, ref.artifactId, ref.version)
            parentPomCache[parentCoord]?.let { return it }

            return runCatching {
                val compId =
                    org.gradle.internal.component.external.model.DefaultModuleComponentIdentifier.newId(
                        org.gradle.api.internal.artifacts.DefaultModuleIdentifier.newId(ref.groupId, ref.artifactId),
                        ref.version,
                    )
                val queryResult =
                    project.dependencies
                        .createArtifactResolutionQuery()
                        .forComponents(compId)
                        .withArtifacts(MavenModule::class.java, MavenPomArtifact::class.java)
                        .execute()
                val parentPomFile =
                    queryResult.resolvedComponents.firstOrNull()
                        ?.getArtifacts(MavenPomArtifact::class.java)
                        ?.filterIsInstance<ResolvedArtifactResult>()
                        ?.firstOrNull()
                        ?.file

                if (parentPomFile != null) {
                    val info = parsePomLicenses(parentPomFile)
                    parentPomCache[parentCoord] = info
                    info
                } else {
                    null
                }
            }.getOrNull()
        }

        val finalPoms = mutableMapOf<Coordinate, PomInfo>()

        parsedPoms.forEach { (coord, initialInfo) ->
            var currentInfo = initialInfo
            val visited = mutableSetOf(coord.toString())
            var depth = 0
            val maxDepth = 10

            while (depth < maxDepth) {
                val parentRef = currentInfo.parentRef ?: break
                val parentKey = "${parentRef.groupId}:${parentRef.artifactId}:${parentRef.version}"
                if (!visited.add(parentKey)) {
                    break
                }
                val parentInfo = fetchParentPom(parentRef) ?: break
                currentInfo = currentInfo.withParent(parentInfo)
                depth++
            }

            finalPoms[coord] = currentInfo
        }

        return finalPoms
    }

    private fun writeThirdPartyNotices(
        project: Project,
        configurations: List<Configuration>,
        coordinates: List<Coordinate>,
        overrides: OverridesConfig,
    ) {
        val jarsByCoordinate = mutableMapOf<Coordinate, File>()
        val artifactTypes = listOf(ArtifactTypeDefinition.JAR_TYPE, "aar")

        configurations.forEach { configuration ->
            for (artifactType in artifactTypes) {
                val view =
                    configuration.incoming.artifactView { viewConfig ->
                        viewConfig.lenient(true)
                        viewConfig.attributes { container ->
                            container.attribute(
                                ArtifactTypeDefinition.ARTIFACT_TYPE_ATTRIBUTE,
                                artifactType,
                            )
                        }
                    }

                view.artifacts.artifacts.forEach { artifact ->
                    val id = artifact.id.componentIdentifier
                    if (id is ProjectComponentIdentifier) {
                        return@forEach
                    }
                    if (id is ModuleComponentIdentifier) {
                        val coordinate = Coordinate(id.group, id.module, id.version)
                        jarsByCoordinate.putIfAbsent(coordinate, artifact.file)
                    }
                }
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

    /** A real HTTPS GET, used only when `bestEffortLicenseFetch = true` (README §2.3). */
    private fun fetchUrlBody(url: String): String? =
        runCatching {
            val client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build()
            val request = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(10)).GET().build()
            val response = client.send(request, HttpResponse.BodyHandlers.ofString())
            if (response.statusCode() in 200..299) response.body() else null
        }.getOrNull()
}
