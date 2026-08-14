package dev.noahtownsend.linkedlicense.plugin

/**
 * A minimal, dependency-free JSON value model and parser — used for `package.json`
 * (npm, §2.3), `Package.resolved` (SPM, §2.3), and `.podspec.json` (CocoaPods, §2.3). None of
 * these call sites need a full-featured JSON library; a small hand-rolled recursive-descent
 * parser keeps the plugin's own dependency footprint minimal, matching the same "don't pull in
 * a heavy library for one format" philosophy applied to the Podfile.lock YAML parser
 * ([PodfileLock]).
 */
sealed class JsonValue {
    data class JsonObject(
        val entries: Map<String, JsonValue>,
    ) : JsonValue() {
        operator fun get(key: String): JsonValue? = entries[key]

        fun string(key: String): String? = (entries[key] as? JsonString)?.value

        fun obj(key: String): JsonObject? = entries[key] as? JsonObject

        fun array(key: String): JsonArray? = entries[key] as? JsonArray
    }

    data class JsonArray(
        val items: List<JsonValue>,
    ) : JsonValue()

    data class JsonString(
        val value: String,
    ) : JsonValue()

    data class JsonNumber(
        val value: Double,
    ) : JsonValue()

    data class JsonBoolean(
        val value: Boolean,
    ) : JsonValue()

    object JsonNull : JsonValue()
}

class JsonParseException(
    message: String,
) : RuntimeException(message)

object MiniJson {
    fun parse(text: String): JsonValue {
        val parser = Parser(text)
        val value = parser.parseValue()
        parser.skipWhitespace()

        if (!parser.isAtEnd()) {
            throw JsonParseException("Unexpected trailing content at offset ${parser.position}")
        }

        return value
    }

    private class Parser(
        private val text: String,
    ) {
        var position = 0
            private set

        fun isAtEnd(): Boolean = position >= text.length

        fun skipWhitespace() {
            while (position < text.length && text[position].isWhitespace()) {
                ++position
            }
        }

        private fun peek(): Char {
            if (position >= text.length) {
                throw JsonParseException("Unexpected end of input")
            }

            return text[position]
        }

        private fun expect(char: Char) {
            if (isAtEnd() || text[position] != char) {
                throw JsonParseException("Expected '$char' at offset $position")
            }

            ++position
        }

        fun parseValue(): JsonValue {
            skipWhitespace()

            return when (peek()) {
                '{' -> parseObject()
                '[' -> parseArray()
                '"' -> JsonValue.JsonString(parseStringLiteral())
                't' -> parseLiteral("true", JsonValue.JsonBoolean(true))
                'f' -> parseLiteral("false", JsonValue.JsonBoolean(false))
                'n' -> parseLiteral("null", JsonValue.JsonNull)
                else -> parseNumber()
            }
        }

        private fun <T : JsonValue> parseLiteral(
            literal: String,
            value: T,
        ): T {
            if (position + literal.length > text.length || text.substring(position, position + literal.length) != literal) {
                throw JsonParseException("Expected '$literal' at offset $position")
            }

            position += literal.length
            return value
        }

        private fun parseObject(): JsonValue.JsonObject {
            expect('{')
            skipWhitespace()

            val entries = linkedMapOf<String, JsonValue>()

            if (!isAtEnd() && peek() == '}') {
                ++position
                return JsonValue.JsonObject(entries)
            }

            while (true) {
                skipWhitespace()
                val key = parseStringLiteral()
                skipWhitespace()
                expect(':')
                val value = parseValue()
                entries[key] = value
                skipWhitespace()

                when (peek()) {
                    ',' -> { ++position }
                    '}' -> { ++position; break }
                    else -> throw JsonParseException("Expected ',' or '}' at offset $position")
                }
            }

            return JsonValue.JsonObject(entries)
        }

        private fun parseArray(): JsonValue.JsonArray {
            expect('[')
            skipWhitespace()

            val items = mutableListOf<JsonValue>()

            if (!isAtEnd() && peek() == ']') {
                ++position
                return JsonValue.JsonArray(items)
            }

            while (true) {
                items += parseValue()
                skipWhitespace()

                when (peek()) {
                    ',' -> { ++position }
                    ']' -> { ++position; break }
                    else -> throw JsonParseException("Expected ',' or ']' at offset $position")
                }
            }

            return JsonValue.JsonArray(items)
        }

        private fun parseStringLiteral(): String {
            expect('"')
            val builder = StringBuilder()

            while (true) {
                val char = peek()

                when (char) {
                    '"' -> { ++position; break }
                    '\\' -> {
                        ++position
                        val escaped = peek()

                        when (escaped) {
                            '"' -> builder.append('"')
                            '\\' -> builder.append('\\')
                            '/' -> builder.append('/')
                            'b' -> builder.append('\b')
                            'f' -> builder.append('\u000C')
                            'n' -> builder.append('\n')
                            'r' -> builder.append('\r')
                            't' -> builder.append('\t')
                            'u' -> {
                                val hex = text.substring(position + 1, position + 5)
                                builder.append(hex.toInt(16).toChar())
                                position += 4
                            }
                            else -> throw JsonParseException("Invalid escape '\\$escaped' at offset $position")
                        }

                        ++position
                    }
                    else -> {
                        builder.append(char)
                        ++position
                    }
                }
            }

            return builder.toString()
        }

        private fun parseNumber(): JsonValue.JsonNumber {
            val start = position

            if (!isAtEnd() && peek() == '-') {
                ++position
            }

            while (!isAtEnd() && (peek().isDigit() || peek() in ".eE+-")) {
                ++position
            }

            val raw = text.substring(start, position)
            val parsed = raw.toDoubleOrNull() ?: throw JsonParseException("Invalid number '$raw' at offset $start")
            return JsonValue.JsonNumber(parsed)
        }
    }
}
