package dev.tabml.box

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AssistantStreamParsingTest {

    @Test
    fun isLineStreamingContentType_detectsStreamAndNdjson() {
        assertTrue(AssistantStreamParsing.isLineStreamingContentType("text/event-stream; charset=utf-8"))
        assertTrue(AssistantStreamParsing.isLineStreamingContentType("application/x-ndjson"))
        assertFalse(AssistantStreamParsing.isLineStreamingContentType("application/json"))
    }

    @Test
    fun extractStreamContentPiece_readsDelta() {
        val j = JSONObject("""{"choices":[{"delta":{"content":"Hi"}}]}""")
        assertEquals("Hi", AssistantStreamParsing.extractStreamContentPiece(j))
    }

    @Test
    fun readLineStreamingFromText_accumulatesSseChunks() {
        val text = """
            data: {"choices":[{"delta":{"content":"He"}}]}

            data: {"choices":[{"delta":{"content":"llo"}}]}

            data: [DONE]
        """.trimIndent()
        val partials = mutableListOf<String>()
        val final = AssistantStreamParsing.readLineStreamingFromText(text, { partials.add(it) }, { true })
        assertEquals("Hello", final)
        assertEquals("Hello", partials.last())
    }
}
