package dev.noahtownsend.linkedlicense.plugin

import java.io.File

/**
 * Locates and parses a pod's `.podspec`/`.podspec.json` `license` field (README §2.3).
 *
 * CocoaPods caches specs locally, typically under `~/.cocoapods/repos/<repo-name>/` for specs
 * published to a spec repo (the `trunk` CDN mirror or a private spec repo), sharded as
 * `<PodName>/<version>/<PodName>.podspec.json` (modern trunk layout further shards by a hash
 * prefix of the pod name above that, which [locatePodspec] also searches for). A git-based pod
 * (`pod 'Foo', :git => '...'`) instead has its podspec sitting directly in the pod's own
 * checkout - not handled here, since there's no single well-known cache path for it; only the
 * spec-repo cache lookup is implemented.
 *
 * The environment this plugin's own tests run in has no CocoaPods installation and no real
 * populated spec-repo cache, so this lookup path is unit-tested against a hand-built fixture
 * directory tree standing in for `~/.cocoapods/repos/`, not against a real CocoaPods cache.
 */
object PodspecLicenses {
    /**
     * Searches [specsReposDir] (stand-in for `~/.cocoapods/repos/`) for `pod`'s podspec,
     * trying the unsharded layout (`<repo>/<PodName>/<version>/<PodName>.podspec(.json)`) and
     * the hash-sharded trunk CDN layout (`<repo>/**/<PodName>/<version>/<PodName>.podspec.json`)
     * up to a shallow search depth. Returns `null` if no matching spec file is found anywhere
     * under [specsReposDir].
     */
    fun locatePodspec(
        pod: ResolvedPod,
        specsReposDir: File,
    ): File? {
        if (!specsReposDir.isDirectory) {
            return null
        }

        val candidateFileNames = listOf("${pod.name}.podspec.json", "${pod.name}.podspec")

        return specsReposDir
            .walkTopDown()
            .maxDepth(8)
            .firstOrNull { candidate ->
                candidate.isFile &&
                    candidate.name in candidateFileNames &&
                    candidate.parentFile?.name == pod.version &&
                    candidate.parentFile?.parentFile?.name == pod.name
            }
    }

    fun parseLicense(specFile: File): PomLicense? =
        if (specFile.name.endsWith(".json")) {
            parseJsonLicense(specFile.readText())
        } else {
            parseRubyLicense(specFile.readText())
        }

    /**
     * `.podspec.json` shape: `"license": "MIT"` or
     * `"license": { "type": "MIT", "file": "LICENSE", "text": "..." }`.
     */
    fun parseJsonLicense(jsonText: String): PomLicense? {
        val root = MiniJson.parse(jsonText) as? JsonValue.JsonObject ?: return null

        return when (val license = root["license"]) {
            is JsonValue.JsonString -> PomLicense(name = license.value, url = null)
            is JsonValue.JsonObject -> PomLicense(name = license.string("type"), url = license.string("file"))
            else -> null
        }
    }

    /**
     * Heuristic regex extraction for the Ruby-DSL `.podspec` form, e.g.:
     * ```ruby
     * s.license = 'MIT'
     * s.license = { :type => 'MIT', :file => 'LICENSE' }
     * s.license = { type: 'MIT', text: 'Copyright ...' }
     * ```
     * This is deliberately narrow - it doesn't evaluate Ruby - and only handles the field being
     * a simple string or a `type`/`:type` key inside a `{ ... }` literal on one or a few lines.
     */
    fun parseRubyLicense(podspecText: String): PomLicense? {
        val assignment =
            Regex(""".\.license\s*=\s*(\{[^}]*\}|'[^']*'|"[^"]*")""", RegexOption.DOT_MATCHES_ALL)
                .find(podspecText)
                ?.groupValues
                ?.get(1) ?: return null

        if (assignment.startsWith("{")) {
            val type =
                Regex("""(?::type\s*=>|type:)\s*['"]([^'"]*)['"]""").find(assignment)?.groupValues?.get(1)
            return type?.let { PomLicense(name = it, url = null) }
        }

        val bare = assignment.trim('\'', '"')
        return PomLicense(name = bare, url = null)
    }
}
