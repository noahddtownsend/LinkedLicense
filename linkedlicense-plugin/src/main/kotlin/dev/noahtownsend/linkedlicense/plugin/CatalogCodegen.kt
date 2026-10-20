package dev.noahtownsend.linkedlicense.plugin

/** One entry in the generated `GeneratedLicenses.all` list. */
sealed class CatalogEntry {
    /** A built-in [dev.noahtownsend.linkedlicense.License] instantiation expression. */
    data class BuiltIn(
        val expression: String,
        val elementLicensed: String? = null,
        val author: String? = null,
    ) : CatalogEntry()

    /** A reference to a user-declared `custom:` symbol — codegen imports and references it directly. */
    data class CustomRef(
        val fullyQualifiedName: String,
    ) : CatalogEntry()
}

private val elementLicensedRegex = Regex("""elementLicensed\s*=\s*"([^"]+)"""")
private val authorRegex = Regex("""author\s*=\s*"([^"]+)"""")

fun List<CatalogEntry>.distinctByNameAndAuthor(): List<CatalogEntry> =
    distinctBy { entry ->
        when (entry) {
            is CatalogEntry.BuiltIn -> {
                val elem = entry.elementLicensed
                    ?: elementLicensedRegex.find(entry.expression)?.groupValues?.get(1)
                val auth = entry.author
                    ?: authorRegex.find(entry.expression)?.groupValues?.get(1)

                if (elem != null && auth != null) {
                    "${elem.lowercase()}|${auth.lowercase()}"
                } else {
                    entry.expression
                }
            }
            is CatalogEntry.CustomRef -> entry.fullyQualifiedName
        }
    }

private fun String.kotlinStringLiteral(): String =
    "\"" +
        replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("$", "\\$") +
        "\""

fun buildInExpression(
    kClass: kotlin.reflect.KClass<out dev.noahtownsend.linkedlicense.License>,
    elementLicensed: String,
    author: String,
    url: String?,
    text: String?,
    isAsset: Boolean = false,
    licenseName: String? = null,
    notice: String? = null,
): String {
    val simpleName = BuiltInLicenses.simpleNameOf(kClass)
    val args = mutableListOf("elementLicensed = ${elementLicensed.kotlinStringLiteral()}", "author = ${author.kotlinStringLiteral()}")

    if (BuiltInLicenses.requiresYear(kClass)) {
        args += "year = \"\""
    }

    if (simpleName == "Custom") {
        args += "text = ${(text ?: "").kotlinStringLiteral()}"

        if (licenseName != null) {
            args += "licenseName = ${licenseName.kotlinStringLiteral()}"
        }
    }

    if (url != null) {
        args += "url = ${url.kotlinStringLiteral()}"
    }

    if (isAsset) {
        args += "kind = License.Kind.ASSET"
    }

    if (notice != null && notice.isNotBlank()) {
        args += "notice = ${notice.kotlinStringLiteral()}"
    }

    return "License.$simpleName(${args.joinToString(", ")})"
}

/** Renders `GeneratedLicenses.kt` (README §2.1 step 7). */
fun renderGeneratedLicensesFile(
    packageName: String,
    entries: List<CatalogEntry>,
): String {
    val deduplicatedEntries = entries.distinctByNameAndAuthor()

    val imports =
        buildList {
            add("import dev.noahtownsend.linkedlicense.License")
            deduplicatedEntries.filterIsInstance<CatalogEntry.CustomRef>().forEach { add("import ${it.fullyQualifiedName}") }
        }.distinct().sorted()

    val listItems =
        deduplicatedEntries.joinToString(",\n") { entry ->
            val expr =
                when (entry) {
                    is CatalogEntry.BuiltIn -> entry.expression
                    is CatalogEntry.CustomRef -> entry.fullyQualifiedName.substringAfterLast('.')
                }
            "        $expr"
        }

    return buildString {
        appendLine("package $packageName")
        appendLine()
        imports.forEach { appendLine(it) }
        appendLine()
        appendLine("object GeneratedLicenses {")
        appendLine("    val all: List<License> = listOf(")

        if (listItems.isNotEmpty()) {
            appendLine(listItems)
        }

        appendLine("    )")
        appendLine("}")
    }
}
