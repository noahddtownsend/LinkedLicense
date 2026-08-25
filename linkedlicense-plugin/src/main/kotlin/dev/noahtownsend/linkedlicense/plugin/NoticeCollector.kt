package dev.noahtownsend.linkedlicense.plugin

import java.io.File
import java.util.zip.ZipFile

/** Reads a `NOTICE`/`NOTICE.txt` entry (root, or under `META-INF/`) out of a jar or aar, if present. */
fun readNoticeFromJar(jarFile: File): String? {
    if (!jarFile.exists()) {
        return null
    }

    val name = jarFile.name.lowercase()
    if (!name.endsWith(".jar") && !name.endsWith(".aar")) {
        return null
    }

    val candidateNames = setOf("NOTICE", "NOTICE.txt", "META-INF/NOTICE", "META-INF/NOTICE.txt")

    return runCatching {
        ZipFile(jarFile).use { zip ->
            val entry = candidateNames.firstNotNullOfOrNull { zip.getEntry(it) }
            if (entry != null) {
                return@use zip.getInputStream(entry).bufferedReader().readText()
            }
            if (name.endsWith(".aar")) {
                val classesJarEntry = zip.getEntry("classes.jar")
                if (classesJarEntry != null) {
                    val tempFile = File.createTempFile("classes", ".jar")
                    try {
                        zip.getInputStream(classesJarEntry).use { input ->
                            tempFile.outputStream().use { out -> input.copyTo(out) }
                        }
                        readNoticeFromJar(tempFile)
                    } finally {
                        tempFile.delete()
                    }
                } else null
            } else null
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
