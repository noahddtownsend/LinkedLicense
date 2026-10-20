package dev.noahtownsend.linkedlicense.plugin

import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.artifacts.component.ProjectComponentIdentifier
import org.gradle.api.artifacts.result.ResolvedArtifactResult
import org.gradle.api.artifacts.type.ArtifactTypeDefinition
import org.gradle.api.logging.Logger
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

    fun configureTask(
        task: GenerateLicenseCatalogTask,
        project: Project,
        extension: LinkedLicenseExtension,
        configurationsProvider: () -> List<Configuration>,
        sourceSetName: String,
        outputDir: File,
        includeAssets: Boolean,
        includeNotices: Boolean = true,
        npmNodeModulesDir: File? = null,
    ) {
        OverridesFileScaffold.ensureExists(extension.overridesFile)

        task.group = "linkedlicense"
        task.description = "Scans the resolved dependency graph for $sourceSetName and generates its license catalog."

        task.sourceSetName.set(sourceSetName)
        task.outputDir.set(outputDir)
        task.includeAssets.set(includeAssets)
        task.overridesFile.set(extension.overridesFile)
        task.failOnCopyleft.set(extension.failOnCopyleft)
        task.failOnSoftCopyleft.set(extension.failOnSoftCopyleft)
        task.failOnUnknown.set(extension.failOnUnknown)
        task.bestEffortLicenseFetch.set(extension.bestEffortLicenseFetch)
        task.autoPopulate.set(extension.autoPopulate)
        task.copyRequiredNotices.set(extension.copyRequiredNotices && includeNotices)
        if (extension.copyRequiredNotices && includeNotices) {
            task.noticesOutputFile.set(extension.noticesOutputFile)
        }
        if (npmNodeModulesDir != null && npmNodeModulesDir.isDirectory) {
            task.npmNodeModulesDir.set(npmNodeModulesDir)
        }

        task.versionCatalogAliases.set(project.provider {
            extractVersionCatalogAliases(project)
        })

        val resolvedInputs = project.provider {
            val configs = configurationsProvider()
            val componentsByCoordinate = linkedMapOf<Coordinate, ModuleComponentIdentifier>()
            configs.forEach { config ->
                collectResolvedComponents(config.incoming.resolutionResult.root).forEach { id ->
                    componentsByCoordinate.putIfAbsent(id.toCoordinate(), id)
                }
            }
            val componentIds = componentsByCoordinate.values.toList()
            val coordinates = componentIds.map { it.toCoordinate() }
            val pomInfos = resolvePomInfo(project, componentIds)
            val jars = collectJarsByCoordinate(configs)
            Triple(coordinates, pomInfos, jars)
        }

        task.coordinates.set(resolvedInputs.map { it.first })
        task.pomInfos.set(resolvedInputs.map { it.second })
        task.jarsByCoordinate.set(resolvedInputs.map { it.third })
        task.jarFiles.from(task.jarsByCoordinate.map { it.values })
    }

    fun generateCatalogFromTask(
        logger: Logger,
        sourceSetName: String,
        coordinates: List<Coordinate>,
        pomInfos: Map<Coordinate, PomInfo>,
        jarsByCoordinate: Map<Coordinate, File>,
        versionCatalogAliases: Map<String, String>,
        overridesFile: File,
        failOnCopyleft: Boolean,
        failOnSoftCopyleft: Boolean?,
        failOnUnknown: Boolean,
        includeAssets: Boolean,
        bestEffortLicenseFetch: Boolean,
        autoPopulate: Boolean,
        copyRequiredNotices: Boolean,
        npmNodeModulesDir: File?,
        outputDir: File,
        noticesOutputFile: File?,
    ) {
        OverridesFileScaffold.ensureExists(overridesFile)

        val npmPackageInfo = npmNodeModulesDir?.let { scanNpmPackageInfo(it) }.orEmpty()
        val allCoordinates = coordinates + npmPackageInfo.keys.toList()
        val extraPomInfo = npmPackageInfo.mapValues { (_, info) -> info.toPomInfo() }
        val pomInfoCache = pomInfos + extraPomInfo

        val overrides =
            TomlOverridesParser.parse(overridesFile) { alias ->
                resolveVersionCatalogAlias(versionCatalogAliases, alias)
            }

        val noticesByCoordinate = allCoordinates.associateWith { coordinate ->
            val fromOverride = (overrides.overrides[coordinate.moduleId] as? OverrideSpec.BuiltIn)?.notice
            val fromJar =
                jarsByCoordinate[coordinate]?.let { readNoticeFromJar(it) }?.let { stripLicenseBoilerplate(it) }
                    ?.takeIf { it.isNotBlank() }
            fromOverride ?: fromJar
        }

        val bestEffortFetch: ((String, String) -> KClass<out License>?)? =
            if (bestEffortLicenseFetch) {
                { repoUrl, ref -> BestEffortLicenseFetch.guessLicense(repoUrl, ref, ::fetchUrlBody) }
            } else {
                null
            }

        val result =
            CatalogGenerator.resolve(
                coordinates = allCoordinates,
                pomInfoOf = { pomInfoCache[it] ?: PomInfo(emptyList(), null) },
                overrides = overrides,
                failOnCopyleft = failOnCopyleft,
                failOnSoftCopyleft = failOnSoftCopyleft,
                failOnUnknown = failOnUnknown,
                includeAssets = includeAssets,
                bestEffortFetch = bestEffortFetch,
                onBestGuess = { coordinate, kClass ->
                    if (!overrides.suppressBestGuessWarnings.containsKey(coordinate.moduleId)) {
                        logger.warn(
                            "linkedlicense: best-effort guess for ${coordinate.moduleId} -> " +
                                "${BuiltInLicenses.simpleNameOf(kClass)} (unverified - fetched from its repository " +
                                "and pattern-matched, not read from a structured license field). Silence this " +
                                "warning with a [suppress-best-guess-warnings] entry in ${overridesFile.path} " +
                                "once verified.",
                        )
                    }
                },
                autoPopulate = autoPopulate,
                onUnresolvedPlaceholder = { coordinate, rawName ->
                    logger.warn(
                        "linkedlicense: unresolved property placeholder in POM <name> '$rawName' for ${coordinate.moduleId} — falling back to '${coordinate.artifact}'.",
                    )
                },
                noticeOf = { noticesByCoordinate[it] },
            )

        if (result.unresolved.isNotEmpty()) {
            throw GradleException(
                "generateLicenseCatalog found dependencies with no matched or overridden license:\n" +
                    result.unresolved.joinToString("\n") { "  - $it" } +
                    "\n\nAdd an [overrides] or [ignored] entry for each in ${overridesFile.path}, " +
                    "or set failOnUnknown = false to omit them instead.",
            )
        }

        if (result.copyleftOffenders.isNotEmpty()) {
            throw GradleException(
                "generateLicenseCatalog found copyleft-licensed dependencies not allow-listed:\n" +
                    result.copyleftOffenders.joinToString("\n") { "  - $it" } +
                    "\n\nAdd a [copyleft-allowed] entry in ${overridesFile.path}, " +
                    "or set failOnCopyleft = false.",
            )
        }

        if (result.policyOffenders.isNotEmpty()) {
            throw GradleException(
                "generateLicenseCatalog found dependencies that violate [license-policy]:\n" +
                    result.policyOffenders.joinToString("\n") { "  - ${it.coordinate} (${it.licenseTypeId})" } +
                    "\n\nAdjust the [license-policy] allow/block lists in ${overridesFile.path}.",
            )
        }

        if (result.assetCopyleftOffenders.isNotEmpty()) {
            throw GradleException(
                "generateLicenseCatalog found copyleft-licensed [assets] entries not allow-listed:\n" +
                    result.assetCopyleftOffenders.joinToString("\n") { "  - $it" } +
                    "\n\nAdd a [copyleft-allowed] entry in ${overridesFile.path}, " +
                    "or set failOnCopyleft = false.",
            )
        }

        if (result.assetPolicyOffenders.isNotEmpty()) {
            throw GradleException(
                "generateLicenseCatalog found [assets] entries that violate [license-policy]:\n" +
                    result.assetPolicyOffenders.joinToString("\n") { "  - ${it.assetKey} (${it.licenseTypeId})" } +
                    "\n\nAdjust the [license-policy] allow/block lists in ${overridesFile.path}.",
            )
        }

        if (copyRequiredNotices && noticesOutputFile != null) {
            writeThirdPartyNoticesFromJars(jarsByCoordinate, allCoordinates, overrides, noticesOutputFile)
        }

        val sourceSetPackageSegment = sourceSetName.lowercase()
        outputDir.mkdirs()
        val generatedFile =
            File(outputDir, "dev/noahtownsend/linkedlicense/generated/$sourceSetPackageSegment/GeneratedLicenses.kt")
        generatedFile.parentFile.mkdirs()
        generatedFile.writeText(
            renderGeneratedLicensesFile(
                packageName = "dev.noahtownsend.linkedlicense.generated.$sourceSetPackageSegment",
                entries = (result.entries.map { it.second } + result.assetEntries.map { it.second }).distinct(),
            ),
        )
    }

    fun generateCatalog(
        project: Project,
        extension: LinkedLicenseExtension,
        configuration: Configuration,
        outputDir: File,
        includeAssets: Boolean,
        npmNodeModulesDir: File? = null,
        sourceSetName: String,
    ) {
        val componentIds = collectResolvedComponents(configuration.incoming.resolutionResult.root)
        val coordinates = componentIds.map { it.toCoordinate() }
        val pomInfos = resolvePomInfo(project, componentIds)
        val jars = collectJarsByCoordinate(listOf(configuration))
        val aliases = extractVersionCatalogAliases(project)

        generateCatalogFromTask(
            logger = project.logger,
            sourceSetName = sourceSetName,
            coordinates = coordinates,
            pomInfos = pomInfos,
            jarsByCoordinate = jars,
            versionCatalogAliases = aliases,
            overridesFile = extension.overridesFile,
            failOnCopyleft = extension.failOnCopyleft,
            failOnSoftCopyleft = extension.failOnSoftCopyleft,
            failOnUnknown = extension.failOnUnknown,
            includeAssets = includeAssets,
            bestEffortLicenseFetch = extension.bestEffortLicenseFetch,
            autoPopulate = extension.autoPopulate,
            copyRequiredNotices = extension.copyRequiredNotices,
            npmNodeModulesDir = npmNodeModulesDir,
            outputDir = outputDir,
            noticesOutputFile = if (extension.copyRequiredNotices) extension.noticesOutputFile else null,
        )
    }

    fun generateUnionCatalog(
        project: Project,
        extension: LinkedLicenseExtension,
        configurations: List<Configuration>,
        outputDir: File,
    ) {
        val componentsByCoordinate = linkedMapOf<Coordinate, ModuleComponentIdentifier>()
        configurations.forEach { config ->
            collectResolvedComponents(config.incoming.resolutionResult.root).forEach { id ->
                componentsByCoordinate.putIfAbsent(id.toCoordinate(), id)
            }
        }
        val componentIds = componentsByCoordinate.values.toList()
        val coordinates = componentIds.map { it.toCoordinate() }
        val pomInfos = resolvePomInfo(project, componentIds)
        val jars = collectJarsByCoordinate(configurations)
        val aliases = extractVersionCatalogAliases(project)

        generateCatalogFromTask(
            logger = project.logger,
            sourceSetName = "commonMain",
            coordinates = coordinates,
            pomInfos = pomInfos,
            jarsByCoordinate = jars,
            versionCatalogAliases = aliases,
            overridesFile = extension.overridesFile,
            failOnCopyleft = extension.failOnCopyleft,
            failOnSoftCopyleft = extension.failOnSoftCopyleft,
            failOnUnknown = extension.failOnUnknown,
            includeAssets = true,
            bestEffortLicenseFetch = extension.bestEffortLicenseFetch,
            autoPopulate = extension.autoPopulate,
            copyRequiredNotices = extension.copyRequiredNotices,
            npmNodeModulesDir = null,
            outputDir = outputDir,
            noticesOutputFile = if (extension.copyRequiredNotices) extension.noticesOutputFile else null,
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

        fun fetchParentPom(initialRef: ParentPomRef): PomInfo? {
            var currentRef: ParentPomRef? = initialRef
            val visited = mutableSetOf<Coordinate>()
            var accumulatedInfo: PomInfo? = null

            while (currentRef != null) {
                val ref = currentRef
                val parentCoord = Coordinate(ref.groupId, ref.artifactId, ref.version)
                if (!visited.add(parentCoord)) break

                val parentInfo =
                    parentPomCache[parentCoord] ?: runCatching {
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
                    }.getOrNull() ?: break

                accumulatedInfo =
                    if (accumulatedInfo == null) {
                        parentInfo
                    } else {
                        accumulatedInfo.copy(
                            licenses = accumulatedInfo.licenses.ifEmpty { parentInfo.licenses },
                            organizationName = accumulatedInfo.organizationName ?: parentInfo.organizationName,
                            scmUrl = accumulatedInfo.scmUrl ?: parentInfo.scmUrl,
                            developers = accumulatedInfo.developers.ifEmpty { parentInfo.developers },
                            properties = parentInfo.properties + accumulatedInfo.properties,
                        )
                    }

                if (accumulatedInfo.licenses.isNotEmpty()) {
                    break
                }
                currentRef = parentInfo.parentRef
            }
            return accumulatedInfo
        }

        val finalPoms = mutableMapOf<Coordinate, PomInfo>()

        parsedPoms.forEach { (coord, info) ->
            val mergedInfo =
                if (info.licenses.isEmpty() && info.parentRef != null) {
                    val parentInfo = fetchParentPom(info.parentRef)
                    if (parentInfo != null) {
                        info.copy(
                            licenses = parentInfo.licenses,
                            organizationName = info.organizationName ?: parentInfo.organizationName,
                            scmUrl = info.scmUrl ?: parentInfo.scmUrl,
                            developers = info.developers.ifEmpty { parentInfo.developers },
                            properties = parentInfo.properties + info.properties,
                        )
                    } else {
                        info
                    }
                } else {
                    info
                }

            finalPoms[coord] = mergedInfo.interpolated(coord)
        }

        return finalPoms
    }

    private fun collectJarsByCoordinate(configurations: List<Configuration>): Map<Coordinate, File> {
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

        return jarsByCoordinate
    }

    private fun writeThirdPartyNoticesFromJars(
        jarsByCoordinate: Map<Coordinate, File>,
        coordinates: List<Coordinate>,
        overrides: OverridesConfig,
        outputFile: File,
    ) {
        val notices =
            coordinates
                .filterNot { overrides.ignored.containsKey(it.moduleId) }
                .mapNotNull { coordinate ->
                    val overrideNotice = (overrides.overrides[coordinate.moduleId] as? OverrideSpec.BuiltIn)?.notice
                    val jarNotice = jarsByCoordinate[coordinate]?.let { readNoticeFromJar(it) }
                    val notice = overrideNotice ?: jarNotice ?: return@mapNotNull null
                    coordinate to notice
                }

        if (notices.isEmpty()) {
            return
        }

        outputFile.parentFile?.mkdirs()
        outputFile.writeText(renderThirdPartyNotices(notices))
    }

    fun extractVersionCatalogAliases(project: Project): Map<String, String> {
        val catalogs = project.extensions.findByType(VersionCatalogsExtension::class.java) ?: return emptyMap()
        val aliases = mutableMapOf<String, String>()
        catalogs.catalogNames.forEach { catalogName ->
            catalogs.find(catalogName).ifPresent { catalog ->
                catalog.libraryAliases.forEach { alias ->
                    catalog.findLibrary(alias).ifPresent { lib ->
                        val module = lib.get().module
                        val coord = "${module.group}:${module.name}"
                        aliases["$catalogName.$alias"] = coord
                        aliases["$catalogName.${alias.replace('-', '.')}"] = coord
                    }
                }
            }
        }
        return aliases
    }

    fun resolveVersionCatalogAlias(
        aliases: Map<String, String>,
        rawKey: String,
    ): String {
        aliases[rawKey]?.let { return it }
        val parts = rawKey.split(".")
        if (parts.size >= 2) {
            val catalogName = parts[0]
            val aliasWithDashes = parts.drop(1).joinToString("-")
            aliases["$catalogName.$aliasWithDashes"]?.let { return it }
            val aliasWithDots = parts.drop(1).joinToString(".")
            aliases["$catalogName.$aliasWithDots"]?.let { return it }
        }
        throw GradleException("Unknown version catalog alias '$rawKey' (available: ${aliases.keys}).")
    }

    fun resolveVersionCatalogAlias(
        project: Project,
        rawKey: String,
    ): String = resolveVersionCatalogAlias(extractVersionCatalogAliases(project), rawKey)

    /** A real HTTPS GET, used only when `bestEffortLicenseFetch = true` (README §2.3). */
    private fun fetchUrlBody(url: String): String? =
        runCatching {
            val client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build()
            val request = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(10)).GET().build()
            val response = client.send(request, HttpResponse.BodyHandlers.ofString())
            if (response.statusCode() in 200..299) response.body() else null
        }.getOrNull()
}
