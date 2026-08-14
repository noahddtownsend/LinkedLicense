package dev.noahtownsend.linkedlicense.plugin

import org.gradle.api.Project
import java.io.File

/**
 * Locates the `node_modules` directory KGP installed a `jsMain`/`wasmJsMain` source set's
 * declared `npm(...)` dependencies into (README §2.3).
 *
 * KGP's JS/Wasm npm dependency resolution (`NodeJsRootPlugin`/`YarnRootPlugin`) shares a single
 * npm/Yarn install across every JS-like target in the whole build, rooted under the *root*
 * project's build directory - historically `build/js/node_modules` for both Kotlin/JS and
 * Kotlin/Wasm-JS targets (Wasm reuses the same JS npm tooling infrastructure). This is a
 * best-effort, version-pinned-convention lookup rather than something read out of KGP's own
 * APIs: at plugin-apply time in [MultiplatformCatalogTasks], reaching into KGP's internal
 * `NodeJsRootExtension`/`YarnRootExtension` types to ask for the real configured directory would
 * add a much deeper, more version-fragile dependency on KGP internals than this plugin takes on
 * anywhere else. Every candidate is tried in order; the first one that exists on disk at task
 * execution time is used. If none exist (no npm dependencies were ever declared, or the JS/Wasm
 * toolchain was never installed), npm scanning is silently skipped - not an error, since not
 * every `jsMain`/`wasmJsMain` source set has npm dependencies at all.
 */
internal object NpmNodeModulesLocator {
    fun locate(
        project: Project,
        sourceSetName: String,
    ): File? {
        val rootBuildDir = project.rootProject.layout.buildDirectory.get().asFile
        val projectBuildDir = project.layout.buildDirectory.get().asFile

        val candidates =
            listOfNotNull(
                File(rootBuildDir, "js/node_modules"),
                if (sourceSetName.startsWith("wasmJs")) File(rootBuildDir, "wasm/node_modules") else null,
                File(projectBuildDir, "js/node_modules"),
                if (sourceSetName.startsWith("wasmJs")) File(projectBuildDir, "wasm/node_modules") else null,
            )

        return candidates.firstOrNull { it.isDirectory }
    }
}
