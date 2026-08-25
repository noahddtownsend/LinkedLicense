package dev.noahtownsend.linkedlicense.plugin

import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/** A single `<license>` entry from a POM's `<licenses>` block. */
data class PomLicense(
    val name: String?,
    val url: String?,
)

/** A `<parent>` reference from a POM. */
data class ParentPomRef(
    val groupId: String,
    val artifactId: String,
    val version: String,
)

/** A `<developer>` entry from a POM's `<developers>` block. */
data class PomDeveloper(
    val name: String?,
)

/**
 * Carries parsed POM metadata used for license resolution, author attribution, display names,
 * and parent POM inheritance.
 */
data class PomInfo(
    val licenses: List<PomLicense> = emptyList(),
    val organizationName: String? = null,
    val scmUrl: String? = null,
    val name: String? = null,
    val developers: List<PomDeveloper> = emptyList(),
    val parentRef: ParentPomRef? = null,
    val properties: Map<String, String> = emptyMap(),
    val groupId: String? = null,
    val artifactId: String? = null,
    val version: String? = null,
) {
    /**
     * Resolves the author in priority order (Bug 3):
     * 1. `<organization><name>`
     * 2. First non-blank `<developers><developer><name>`
     * 3. Fallback provided by caller (e.g. Maven groupId)
     */
    fun resolveAuthor(fallback: String): String {
        organizationName?.takeIf { it.isNotBlank() && !it.contains("\${") }?.let { return it }
        developers.firstNotNullOfOrNull { it.name?.takeIf { name -> name.isNotBlank() && !name.contains("\${") } }?.let { return it }
        return fallback
    }

    /**
     * Resolves elementLicensed in priority order (Bug 3):
     * 1. POM `<name>` (only if non-blank and containing no unresolved `${...}` placeholders)
     * 2. Fallback provided by caller (e.g. Maven artifactId)
     */
    fun resolveElementLicensed(fallback: String): String {
        name?.takeIf { it.isNotBlank() && !it.contains("\${") }?.let { return it }
        return fallback
    }

    /**
     * Merges inherited metadata from a parent POM (Bug 1):
     * - Inherits licenses if child has none
     * - Inherits organizationName if child has none
     * - Inherits developers if child has none
     * - Inherits scmUrl if child has none
     * - Inherits properties (child properties override parent properties)
     * - Passes along parent's parentRef to allow further chain traversal
     */
    fun withParent(parent: PomInfo): PomInfo =
        PomInfo(
            licenses = if (this.licenses.isNotEmpty()) this.licenses else parent.licenses,
            organizationName = this.organizationName ?: parent.organizationName,
            scmUrl = this.scmUrl ?: parent.scmUrl,
            name = this.name ?: parent.name,
            developers = if (this.developers.isNotEmpty()) this.developers else parent.developers,
            parentRef = parent.parentRef,
            properties = parent.properties + this.properties,
            groupId = this.groupId ?: parent.groupId,
            artifactId = this.artifactId ?: parent.artifactId,
            version = this.version ?: parent.version,
        )

    /**
     * Interpolates Maven built-in and custom property placeholders (`${project.groupId}`,
     * `${project.artifactId}`, `${project.version}`, `${pom.*}`, `${...}`) in all text fields (Bug 6).
     */
    fun interpolated(coordinate: Coordinate? = null): PomInfo {
        val resolvedProperties = mutableMapOf<String, String>()
        resolvedProperties.putAll(properties)

        val g = groupId ?: coordinate?.group
        val a = artifactId ?: coordinate?.artifact
        val v = version ?: coordinate?.version

        if (g != null) {
            resolvedProperties["project.groupId"] = g
            resolvedProperties["pom.groupId"] = g
            resolvedProperties["groupId"] = g
        }
        if (a != null) {
            resolvedProperties["project.artifactId"] = a
            resolvedProperties["pom.artifactId"] = a
            resolvedProperties["artifactId"] = a
        }
        if (v != null) {
            resolvedProperties["project.version"] = v
            resolvedProperties["pom.version"] = v
            resolvedProperties["version"] = v
        }
        if (name != null) {
            resolvedProperties["project.name"] = name
            resolvedProperties["pom.name"] = name
        }

        for (i in 0 until 10) {
            var changed = false
            for ((key, value) in resolvedProperties.toList()) {
                if (value.contains("\${")) {
                    val replaced = interpolateProperties(value, resolvedProperties)
                    if (replaced != null && replaced != value) {
                        resolvedProperties[key] = replaced
                        changed = true
                    }
                }
            }
            if (!changed) break
        }

        fun interp(s: String?): String? = interpolateProperties(s, resolvedProperties)

        return copy(
            name = interp(name),
            organizationName = interp(organizationName),
            scmUrl = interp(scmUrl),
            developers = developers.map { dev -> dev.copy(name = interp(dev.name)) },
            licenses = licenses.map { lic -> lic.copy(name = interp(lic.name), url = interp(lic.url)) },
            properties = resolvedProperties,
        )
    }
}

/** Interpolates `${placeholder}` tokens using values in [properties]. */
fun interpolateProperties(text: String?, properties: Map<String, String>): String? {
    if (text == null) return null
    if (!text.contains("\${")) return text

    val regex = Regex("""\$\{([^}]+)\}""")
    var current: String = text
    var previous: String
    var iterations = 0
    val maxIterations = 10

    do {
        previous = current
        current = regex.replace(current) { matchResult ->
            val key = matchResult.groupValues[1].trim()
            properties[key] ?: matchResult.value
        }
        iterations++
    } while (current != previous && iterations < maxIterations && current.contains("\${"))

    return current
}

private val EMPTY_POM_INFO = PomInfo(licenses = emptyList(), organizationName = null)

fun parsePomLicenses(pomFile: File): PomInfo {
    if (!pomFile.exists()) {
        return EMPTY_POM_INFO
    }

    val factory = DocumentBuilderFactory.newInstance()
    factory.isNamespaceAware = false
    factory.isValidating = false

    val document =
        factory.newDocumentBuilder().parse(pomFile).also {
            it.documentElement.normalize()
        }

    val root = document.documentElement

    val parentElement = root.directChild("parent")
    val parentRef =
        if (parentElement != null) {
            val pGroup = parentElement.directChild("groupId")?.textContent?.trim()?.takeIf { it.isNotEmpty() }
            val pArtifact = parentElement.directChild("artifactId")?.textContent?.trim()?.takeIf { it.isNotEmpty() }
            val pVersion = parentElement.directChild("version")?.textContent?.trim()?.takeIf { it.isNotEmpty() }
            if (pGroup != null && pArtifact != null && pVersion != null) {
                ParentPomRef(pGroup, pArtifact, pVersion)
            } else null
        } else null

    val groupId =
        root.directChild("groupId")?.textContent?.trim()?.takeIf { it.isNotEmpty() }
            ?: parentRef?.groupId

    val artifactId =
        root.directChild("artifactId")?.textContent?.trim()?.takeIf { it.isNotEmpty() }
            ?: parentRef?.artifactId

    val version =
        root.directChild("version")?.textContent?.trim()?.takeIf { it.isNotEmpty() }
            ?: parentRef?.version

    val propertiesElement = root.directChild("properties")
    val properties = mutableMapOf<String, String>()
    if (propertiesElement != null) {
        val childNodes = propertiesElement.childNodes
        for (i in 0 until childNodes.length) {
            val node = childNodes.item(i)
            if (node is Element) {
                val key = node.tagName.trim()
                val value = node.textContent.trim()
                if (key.isNotEmpty()) {
                    properties[key] = value
                }
            }
        }
    }

    val name =
        root
            .directChild("name")
            ?.textContent
            ?.trim()
            ?.takeIf { it.isNotEmpty() }

    val organizationName =
        root
            .directChild("organization")
            ?.directChild("name")
            ?.textContent
            ?.trim()
            ?.takeIf { it.isNotEmpty() }

    val developers =
        root
            .directChild("developers")
            ?.directChildren("developer")
            ?.map { dev ->
                PomDeveloper(
                    name = dev.directChild("name")?.textContent?.trim()?.takeIf { it.isNotEmpty() },
                )
            }
            .orEmpty()

    val scmUrl =
        root
            .directChild("scm")
            ?.directChild("url")
            ?.textContent
            ?.trim()
            ?.takeIf { it.isNotEmpty() }

    val licensesElement = root.directChild("licenses")
    val licenses =
        licensesElement
            ?.directChildren("license")
            ?.map { licenseElement ->
                PomLicense(
                    name = licenseElement.directChild("name")?.textContent?.trim()?.takeIf { it.isNotEmpty() },
                    url = licenseElement.directChild("url")?.textContent?.trim()?.takeIf { it.isNotEmpty() },
                )
            }
            .orEmpty()

    return PomInfo(
        licenses = licenses,
        organizationName = organizationName,
        scmUrl = scmUrl,
        name = name,
        developers = developers,
        parentRef = parentRef,
        properties = properties,
        groupId = groupId,
        artifactId = artifactId,
        version = version,
    )
}

private fun Element.directChild(tag: String): Element? = directChildren(tag).firstOrNull()

private fun Element.directChildren(tag: String): List<Element> {
    val result = mutableListOf<Element>()
    val nodes = childNodes

    for (i in 0 until nodes.length) {
        val node = nodes.item(i)

        if (node is Element && node.tagName == tag) {
            result += node
        }
    }

    return result
}
