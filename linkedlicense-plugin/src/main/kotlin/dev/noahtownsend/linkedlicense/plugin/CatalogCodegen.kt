package dev.noahtownsend.linkedlicense.plugin

/** One entry in the generated `GeneratedLicenses.all` list. */
sealed class CatalogEntry {
    /** A built-in [dev.noahtownsend.linkedlicense.License] instantiation expression. */
    data class BuiltIn(
        val expression: String,
    ) : CatalogEntry()

    /** A reference to a user-declared `custom:` symbol — codegen imports and references it directly. */
    data class CustomRef(
        val fullyQualifiedName: String,
    ) : CatalogEntry()
}

private fun String.kotlinStringLiteral(): String =
    "\"" +
        replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("$", "\\$") +
        "\""

/** Builds the constructor-call expression for an auto-matched or built-in-typed override. */
fun buildInExpression(
    kClass: kotlin.reflect.KClass<out dev.noahtownsend.linkedlicense.License>,
    elementLicensed: String,
    author: String,
    url: String?,
    text: String?,
    isAsset: Boolean = false,
): String {
    val simpleName = BuiltInLicenses.simpleNameOf(kClass)
    val args = mutableListOf("elementLicensed = ${elementLicensed.kotlinStringLiteral()}", "author = ${author.kotlinStringLiteral()}")

    if (BuiltInLicenses.requiresYear(kClass)) {
        args += "year = \"\""
    }

    if (simpleName == "Custom") {
        args += "text = ${(text ?: "").kotlinStringLiteral()}"
    }

    if (url != null) {
        args += "url = ${url.kotlinStringLiteral()}"
    }

    if (isAsset) {
        args += "kind = License.Kind.ASSET"
    }

    return "License.$simpleName(${args.joinToString(", ")})"
}

/** Renders `GeneratedLicenses.kt` (README §2.1 step 7). */
fun renderGeneratedLicensesFile(
    packageName: String,
    entries: List<CatalogEntry>,
): String {
    val imports =
        buildList {
            add("import dev.noahtownsend.linkedlicense.License")
            entries.filterIsInstance<CatalogEntry.CustomRef>().forEach { add("import ${it.fullyQualifiedName}") }
        }.distinct().sorted()

    val listItems =
        entries.joinToString(",\n") { entry ->
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
