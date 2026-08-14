package dev.noahtownsend.linkedlicense.plugin

import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/** A single `<license>` entry from a POM's `<licenses>` block. */
data class PomLicense(
    val name: String?,
    val url: String?,
)

/** Also carries the POM's `<organization><name>`, used as a codegen fallback for `author`. */
data class PomInfo(
    val licenses: List<PomLicense>,
    val organizationName: String?,
)

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

    val organizationName =
        root
            .directChild("organization")
            ?.directChild("name")
            ?.textContent
            ?.trim()
            ?.takeIf { it.isNotEmpty() }

    val licensesElement = root.directChild("licenses") ?: return PomInfo(emptyList(), organizationName)

    val licenses =
        licensesElement.directChildren("license").map { licenseElement ->
            PomLicense(
                name = licenseElement.directChild("name")?.textContent?.trim()?.takeIf { it.isNotEmpty() },
                url = licenseElement.directChild("url")?.textContent?.trim()?.takeIf { it.isNotEmpty() },
            )
        }

    return PomInfo(licenses, organizationName)
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
