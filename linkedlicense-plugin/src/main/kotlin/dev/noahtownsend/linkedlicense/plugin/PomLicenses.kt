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
) {
    /**
     * Resolves the author in priority order (Bug 3):
     * 1. `<organization><name>`
     * 2. First non-blank `<developers><developer><name>`
     * 3. Fallback provided by caller (e.g. Maven groupId)
     */
    fun resolveAuthor(fallback: String): String {
        organizationName?.takeIf { it.isNotBlank() }?.let { return it }
        developers.firstNotNullOfOrNull { it.name?.takeIf { name -> name.isNotBlank() } }?.let { return it }
        return fallback
    }

    /**
     * Resolves elementLicensed in priority order (Bug 3):
     * 1. POM `<name>`
     * 2. Fallback provided by caller (e.g. Maven artifactId)
     */
    fun resolveElementLicensed(fallback: String): String {
        name?.takeIf { it.isNotBlank() }?.let { return it }
        return fallback
    }

    /**
     * Merges inherited metadata from a parent POM (Bug 1):
     * - Inherits licenses if child has none
     * - Inherits organizationName if child has none
     * - Inherits developers if child has none
     * - Inherits scmUrl if child has none
     * - Passes along parent's parentRef to allow further chain traversal
     */
    fun withParent(parent: PomInfo): PomInfo =
        PomInfo(
            licenses = if (this.licenses.isNotEmpty()) this.licenses else parent.licenses,
            organizationName = this.organizationName ?: parent.organizationName,
            scmUrl = this.scmUrl ?: parent.scmUrl,
            name = this.name,
            developers = if (this.developers.isNotEmpty()) this.developers else parent.developers,
            parentRef = parent.parentRef,
        )
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
