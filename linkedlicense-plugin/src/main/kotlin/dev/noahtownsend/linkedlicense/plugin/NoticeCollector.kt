package dev.noahtownsend.linkedlicense.plugin

import java.io.File
import java.util.zip.ZipFile

/** Reads a `NOTICE`/`NOTICE.txt` entry (root, or under `META-INF/`) out of a jar, if present. */
fun readNoticeFromJar(jarFile: File): String? {
    if (!jarFile.exists() || !jarFile.name.endsWith(".jar")) {
        return null
    }

    val candidateNames = setOf("NOTICE", "NOTICE.txt", "META-INF/NOTICE", "META-INF/NOTICE.txt")

    return runCatching {
        ZipFile(jarFile).use { zip ->
            val entry = candidateNames.firstNotNullOfOrNull { zip.getEntry(it) } ?: return@use null
            zip.getInputStream(entry).bufferedReader().readText()
        }
    }.getOrNull()
}

/** Renders the `THIRD-PARTY-NOTICES` file contents (README §3.4). */
fun renderThirdPartyNotices(noticesByCoordinate: List<Pair<Coordinate, String>>): String =
    buildString {
        appendLine("THIRD-PARTY NOTICES")
        appendLine("This file contains required notices for third-party dependencies bundled with this project.")

        noticesByCoordinate.forEach { (coordinate, notice) ->
            appendLine()
            appendLine("=".repeat(80))
            appendLine(coordinate.toString())
            appendLine("=".repeat(80))
            appendLine(notice.trim())
        }
    }
