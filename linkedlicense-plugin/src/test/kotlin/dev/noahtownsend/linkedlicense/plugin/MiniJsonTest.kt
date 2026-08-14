package dev.noahtownsend.linkedlicense.plugin

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class MiniJsonTest {
    @Test
    fun `parse() reads a flat object of strings`() {
        val result = MiniJson.parse("""{"name": "left-pad", "version": "1.3.0"}""") as JsonValue.JsonObject

        assertEquals("left-pad", result.string("name"))
        assertEquals("1.3.0", result.string("version"))
    }

    @Test
    fun `parse() reads nested objects and arrays`() {
        val result =
            MiniJson.parse(
                """{"license": {"type": "MIT", "url": "https://example.com"}, "keywords": ["a", "b"]}""",
            ) as JsonValue.JsonObject

        assertEquals("MIT", result.obj("license")?.string("type"))
        assertEquals(2, result.array("keywords")?.items?.size)
    }

    @Test
    fun `parse() reads booleans null and numbers`() {
        val result = MiniJson.parse("""{"a": true, "b": false, "c": null, "d": 42, "e": -1.5e2}""") as JsonValue.JsonObject

        assertEquals(true, (result["a"] as JsonValue.JsonBoolean).value)
        assertEquals(false, (result["b"] as JsonValue.JsonBoolean).value)
        assertEquals(JsonValue.JsonNull, result["c"])
        assertEquals(42.0, (result["d"] as JsonValue.JsonNumber).value)
        assertEquals(-150.0, (result["e"] as JsonValue.JsonNumber).value)
    }

    @Test
    fun `parse() handles escaped characters in strings`() {
        val result = MiniJson.parse(""""line1\nline2\t\"quoted\""""") as JsonValue.JsonString

        assertEquals("line1\nline2\t\"quoted\"", result.value)
    }

    @Test
    fun `parse() throws on malformed input`() {
        assertFailsWith<JsonParseException> { MiniJson.parse("{\"a\": }") }
        assertFailsWith<JsonParseException> { MiniJson.parse("{\"a\": 1") }
    }

    @Test
    fun `JsonObject accessors return null for missing or wrong-typed keys`() {
        val result = MiniJson.parse("""{"a": "x"}""") as JsonValue.JsonObject

        assertNull(result.string("missing"))
        assertNull(result.obj("a"))
    }
}
