package dev.edgecode.core.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class JsonTest {

    private fun malformed(text: String) {
        try {
            parseJson(text)
            fail("expected JsonException for <$text>")
        } catch (expected: JsonException) {
            assertTrue(
                "message should carry an offset, was <${expected.message}>",
                expected.message.orEmpty().contains("offset"),
            )
        }
    }

    @Test
    fun nestedDocumentRoundTrips() {
        val text = """
            {
              "id": "sess-1",
              "count": 3,
              "ratio": 1.5,
              "ok": true,
              "nothing": null,
              "tags": ["a", "b"],
              "nested": {"deep": {"value": 42}, "list": [1, 2, {"x": false}]}
            }
        """.trimIndent()

        val doc = parseJson(text)

        assertEquals("sess-1", doc.string("id"))
        assertEquals(3, doc.int("count"))
        assertEquals(1.5, doc.double("ratio")!!, 0.0)
        assertEquals(true, doc.bool("ok"))
        assertEquals(JsonValue.Null, doc["nothing"])
        assertEquals(listOf("a", "b"), doc.strings("tags"))
        assertEquals(42, doc.obj("nested")?.obj("deep")?.int("value"))
        assertEquals(false, doc.obj("nested")?.arr("list")?.get(2)?.bool("x"))

        val reparsed = parseJson(doc.encode())
        assertEquals(doc, reparsed)
        assertEquals(text.filterNot { it.isWhitespace() }, doc.encode())
    }

    @Test
    fun stringEscapesRoundTrip() {
        val original = "quote:\" slash:\\ newline:\n tab:\t back:\b form:\u000C"
        val encoded = JsonValue.Str(original).encode()

        assertTrue(encoded.contains("\\n"))
        assertTrue(encoded.contains("\\t"))
        assertTrue(encoded.contains("\\\""))
        assertTrue(encoded.contains("\\\\"))
        assertEquals(original, parseJson(encoded).asString)
    }

    @Test
    fun unicodeEscapesDecode() {
        assertEquals("café", parseJson("\"caf\\u00e9\"").asString)
    }

    @Test
    fun surrogatePairsDecode() {
        val emoji = parseJson("\"\\uD83D\\uDE00\"").asString
        assertEquals("\uD83D\uDE00", emoji)
        assertEquals(2, emoji!!.length)
        assertEquals("😀", parseJson("\"😀\"").asString)
        assertEquals("a😀b", parseJson("\"a\\uD83D\\uDE00b\"").asString)
    }

    @Test
    fun unicodeInSourceSurvives() {
        val doc = parseJson("""{"greeting":"café ☕"}""")
        assertEquals("café ☕", doc.string("greeting"))
        assertEquals("café ☕", parseJson(doc.encode()).string("greeting"))
    }

    @Test
    fun controlCharactersAreEscaped() {
        val encoded = JsonValue.Str("\u0001\u001F").encode()
        assertEquals("\"\\u0001\\u001f\"", encoded)
        assertEquals("\u0001\u001F", parseJson(encoded).asString)
    }

    @Test
    fun epochMillisRecoverExactly() {
        val doc = parseJson("""{"ts":1792353189087}""")

        assertEquals(1792353189087L, doc.long("ts"))
        assertEquals("""{"ts":1792353189087}""", doc.encode())
        assertFalse(doc.encode().contains("E12"))
        assertEquals(1792353189087L, parseJson(doc.encode()).long("ts"))
    }

    @Test
    fun numberFormsParse() {
        val doc = parseJson("""{"int":5,"neg":-1,"dec":3.25,"exp":1.5e3,"negexp":-2E-2,"zero":0}""")

        assertEquals(5, doc.int("int"))
        assertEquals(-1L, doc.long("neg"))
        assertEquals(-1, doc.int("neg"))
        assertEquals(3.25, doc.double("dec")!!, 0.0)
        assertEquals(1500.0, doc.double("exp")!!, 0.0)
        assertEquals(-0.02, doc.double("negexp")!!, 1e-12)
        assertEquals(0.0, doc.double("zero")!!, 0.0)

        assertEquals("-1", JsonValue.Num(-1.0).encode())
        assertEquals("0", JsonValue.Num(0.0).encode())
        assertEquals("3.25", JsonValue.Num(3.25).encode())
    }

    @Test
    fun nullAppearsEverywhere() {
        assertEquals(JsonValue.Null, parseJson("null"))

        val arr = parseJson("[null,1]").asArray!!
        assertEquals(JsonValue.Null, arr[0])
        assertEquals(JsonValue.Num(1.0), arr[1])

        val obj = parseJson("""{"a":null}""")
        assertEquals(JsonValue.Null, obj["a"])
        assertNull(obj.string("a"))
        assertNull(obj.obj("a"))
    }

    @Test
    fun getOperatorBehavesByContainer() {
        val obj = parseJson("""{"a":{"b":1}}""")
        assertEquals(1, obj["a"]?.int("b"))
        assertNull(obj["missing"])
        assertNull(parseJson("[]")["a"])

        val arr = parseJson("[10,20,30]")
        assertEquals(JsonValue.Num(20.0), arr[1])
        assertNull(arr[9])
        assertNull(obj[0])
    }

    @Test
    fun readHelpersReturnDefaultsForMissingKeys() {
        val doc = parseJson("""{"s":"text","n":7,"b":true,"o":{"k":1},"a":[1,"two"]}""")

        assertNull(doc.string("missing"))
        assertNull(doc.int("missing"))
        assertNull(doc.long("missing"))
        assertNull(doc.double("missing"))
        assertNull(doc.bool("missing"))
        assertNull(doc.obj("missing"))
        assertEquals("fallback", doc.stringOr("missing", "fallback"))
        assertEquals(9, doc.intOr("missing", 9))
        assertEquals(9L, doc.longOr("missing", 9L))
        assertEquals(9.5, doc.doubleOr("missing", 9.5), 0.0)
        assertEquals(true, doc.boolOr("missing", true))
        assertEquals(emptyList<JsonValue>(), doc.arr("missing"))
        assertEquals(emptyList<String>(), doc.strings("missing"))
    }

    @Test
    fun readHelpersReturnDefaultsForWrongTypes() {
        val doc = parseJson("""{"s":"text","n":7,"b":true,"o":{"k":1},"a":[1,"two"]}""")

        assertNull(doc.int("s"))
        assertNull(doc.long("s"))
        assertNull(doc.double("s"))
        assertNull(doc.bool("s"))
        assertNull(doc.obj("s"))
        assertNull(doc.string("n"))
        assertNull(doc.bool("n"))
        assertEquals("fallback", doc.stringOr("n", "fallback"))
        assertEquals(0, doc.intOr("s", 0))
        assertEquals(false, doc.boolOr("n", false))
        assertEquals(3.5, doc.doubleOr("s", 3.5), 0.0)
        assertEquals(emptyList<JsonValue>(), doc.arr("s"))
        assertEquals(emptyList<String>(), doc.strings("n"))
        assertEquals(emptyList<String>(), doc.strings("b"))
    }

    @Test
    fun bareAccessorsAndStringList() {
        assertEquals("text", JsonValue.Str("text").asString)
        assertNull(JsonValue.Num(1.0).asString)
        assertEquals(mapOf("k" to JsonValue.Num(1.0)), JsonValue.Obj(mapOf("k" to JsonValue.Num(1.0))).asObject)
        assertNull(parseJson("[]").asObject)
        assertEquals(2, parseJson("[1,2]").asArray?.size)
        assertNull(parseJson("{}").asArray)

        val strings = parseJson("""["a",1,"b",null]""").asStringList
        assertEquals(listOf("a", "b"), strings)
        assertEquals(emptyList<String>(), parseJson("""{"x":1}""").asStringList)
    }

    @Test
    fun duplicateKeysLastValueWins() {
        val doc = parseJson("""{"a":1,"a":2,"a":3}""")

        assertEquals(3, doc.int("a"))
        assertEquals(1, doc.asObject!!.size)
    }

    @Test
    fun whitespaceIsSkippedEverywhere() {
        val doc = parseJson("  \n\t {  \"a\" \t : \n [ 1 , 2 ] }  \r\n ")
        assertEquals(listOf(JsonValue.Num(1.0), JsonValue.Num(2.0)), doc.arr("a"))
    }

    @Test
    fun malformedInputsThrow() {
        malformed("")
        malformed("   ")
        malformed("\"abc")
        malformed("""{"a":}""")
        malformed("[1,2,]")
        malformed("""{"a":1,}""")
        malformed("{")
        malformed("[1,2")
        malformed("""{"a":1} x""")
        malformed("undefined")
        malformed("nul")
        malformed("tru")
        malformed("[1,,2]")
        malformed("""{"a" 1}""")
        malformed("01")
        malformed("1.")
        malformed("1e")
        malformed("\"\\q\"")
        malformed("{\"a\":")
    }

    @Test
    fun nestingDepthLimitIsEnforced() {
        val shallow = "[".repeat(30) + "]".repeat(30)
        assertTrue(parseJson(shallow) is JsonValue.Arr)

        val tooDeep = "[".repeat(100) + "]".repeat(100)
        malformed(tooDeep)
    }

    @Test
    fun largeDocumentParses() {
        val builder = StringBuilder("[")
        repeat(2000) { index ->
            if (index > 0) builder.append(',')
            builder.append("""{"i":$index,"name":"row-$index","ok":${index % 2 == 0}}""")
        }
        builder.append(']')

        val doc = parseJson(builder.toString())
        val items = doc.asArray!!
        assertEquals(2000, items.size)
        assertEquals(1999, items[1999].int("i"))
        assertEquals("row-0", items[0].string("name"))
        assertEquals(true, items[0].bool("ok"))
        assertEquals(doc, parseJson(doc.encode()))
    }
}
