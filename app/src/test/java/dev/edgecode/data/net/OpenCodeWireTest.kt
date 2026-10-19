package dev.edgecode.data.net

import dev.edgecode.core.model.ChatMessage
import dev.edgecode.core.model.Part
import dev.edgecode.core.model.PermissionKind
import dev.edgecode.core.model.ServerEvent
import dev.edgecode.core.model.SessionStatus
import dev.edgecode.core.model.ToolStatus
import dev.edgecode.core.util.JsonValue
import dev.edgecode.core.util.arr
import dev.edgecode.core.util.encode
import dev.edgecode.core.util.get
import dev.edgecode.core.util.obj
import dev.edgecode.core.util.parseJson
import dev.edgecode.core.util.string
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Mapping tests.
 *
 * The fixtures under `src/test/resources/fixtures` are unmodified responses
 * captured from a running v1.19.3 server, so these assertions hold the mapper
 * against the server's real output rather than against my reading of its spec.
 */
class OpenCodeWireTest {

    private fun fixture(name: String): JsonValue {
        val stream = javaClass.getResourceAsStream("/fixtures/$name")
            ?: error("missing fixture $name")
        return parseJson(stream.bufferedReader().readText())
    }

    private fun json(text: String): JsonValue = parseJson(text)

    // ---- sessions -------------------------------------------------------

    @Test
    fun sessionMapsEveryFieldFromRealServerOutput() {
        val sessions = fixture("sessions.json")
        val summary = OpenCodeWire.session(sessions[0]!!)

        assertEquals("ses_eaf6cc321ffedK7WlgM5fS7tOH", summary.id)
        assertEquals("Greeting", summary.title)
        assertEquals("/data/data/com.termux/files/home/edgecode", summary.directory)
        assertEquals(1792353189087L, summary.createdAt)
        assertEquals(1792353195384L, summary.updatedAt)
        assertEquals(0.001196826, summary.cost, 1e-9)
        assertEquals(7899, summary.tokens.input)
        assertEquals(11, summary.tokens.output)
        assertEquals(1792, summary.tokens.cacheRead)
        assertEquals("commandcode/deepseek/deepseek-v4.1-flash", summary.modelLabel)
    }

    @Test
    fun epochMillisSurviveTheRoundTrip() {
        // Regression guard: a Double-backed number type loses epoch millis, and
        // formatRelative would then render every session as 1970.
        val summary = OpenCodeWire.session(fixture("sessions.json")[0]!!)
        assertTrue(summary.createdAt > 1_700_000_000_000L)
        assertEquals(1792353189087L, summary.createdAt)
    }

    @Test
    fun aSessionWithNoCompactionStampIsIdle() {
        val summary = OpenCodeWire.session(fixture("sessions.json")[0]!!)
        assertEquals(SessionStatus.Idle, summary.status)
    }

    @Test
    fun aCompactingSessionIsWorking() {
        val summary = OpenCodeWire.session(
            json("""{"id":"s","title":"t","directory":"/x","time":{"created":1,"updated":2,"compacting":5}}"""),
        )
        assertEquals(SessionStatus.Working, summary.status)
    }

    @Test
    fun missingFieldsDegradeInsteadOfThrowing() {
        val summary = OpenCodeWire.session(json("""{"id":"s"}"""))
        assertEquals("s", summary.id)
        assertEquals("Untitled", summary.title)
        assertEquals(0L, summary.createdAt)
        assertEquals(0, summary.tokens.input)
        assertNull(summary.modelLabel)
    }

    // ---- messages -------------------------------------------------------

    @Test
    fun userMessageFromRealServerOutput() {
        val message = OpenCodeWire.message(fixture("messages.json")[0]!!)
        assertTrue(message is ChatMessage.User)

        val user = message as ChatMessage.User
        assertEquals("msg_150933d32001a99NXtb5GZHav3", user.id)
        assertEquals("hi", user.text)
        assertEquals(1792353189170L, user.createdAt)
    }

    @Test
    fun assistantMessageFromRealServerOutput() {
        val message = OpenCodeWire.message(fixture("messages.json")[1]!!)
        assertTrue(message is ChatMessage.Assistant)

        val assistant = message as ChatMessage.Assistant
        assertEquals("msg_150933db6002rdGVsMojguxD98", assistant.id)
        assertEquals(1792353195340L, assistant.completedAt)
        assertFalse("a completed turn is not streaming", assistant.streaming)
        assertNull(assistant.error)

        val text = assistant.parts.filterIsInstance<Part.Text>().single()
        assertTrue(text.text.startsWith("Hi!"))
        assertFalse("a finished part is not streaming", text.streaming)
    }

    @Test
    fun assistantModelComesFromTheFlatProviderAndModelIdFields() {
        // An assistant message carries flat `providerID`/`modelID`; a user message
        // carries a nested `model` object. Reading only one shape loses the other.
        val assistant = OpenCodeWire.message(fixture("messages.json")[1]!!)
            as ChatMessage.Assistant
        assertNotNull(assistant.model)
        assertEquals("commandcode", assistant.model!!.providerId)
        assertEquals("deepseek/deepseek-v4.1-flash", assistant.model!!.modelId)
    }

    @Test
    fun assistantAgentIsCarried() {
        val assistant = OpenCodeWire.message(fixture("messages.json")[1]!!)
            as ChatMessage.Assistant
        assertEquals("plan", assistant.agent)
    }

    @Test
    fun aSingleStepTurnDropsItsStepBoundaries() {
        // The real fixture has exactly one step. Rendering its boundaries put a
        // rule above and below an ordinary reply.
        val assistant = OpenCodeWire.message(fixture("messages.json")[1]!!)
            as ChatMessage.Assistant
        assertEquals(0, assistant.parts.count { it is Part.Step })
    }

    @Test
    fun aMultiStepTurnKeepsItsStepBoundaries() {
        val parts = (1..2).joinToString(",") { index ->
            """{"id":"p$index","type":"step-start","snapshot":"s"}"""
        }
        val message = OpenCodeWire.message(
            json(
                """
                {"info":{"id":"m","role":"assistant","time":{"created":1},
                 "modelID":"m1","providerID":"p1"},
                 "parts":[$parts,{"id":"t","type":"text","text":"hello","time":{"start":1,"end":2}}]}
                """.trimIndent(),
            ),
        ) as ChatMessage.Assistant

        assertEquals(2, message.parts.count { it is Part.Step })
        assertEquals(0, message.parts.filterIsInstance<Part.Step>()[0].index)
        assertEquals(1, message.parts.filterIsInstance<Part.Step>()[1].index)
    }

    @Test
    fun anAssistantTurnWithoutACompletedStampIsStillStreaming() {
        val message = OpenCodeWire.message(
            json("""{"info":{"id":"m","role":"assistant","time":{"created":1}},"parts":[]}"""),
        ) as ChatMessage.Assistant
        assertTrue(message.streaming)
        assertNull(message.completedAt)
    }

    @Test
    fun aUserMessageWithFilesProducesAttachments() {
        val message = OpenCodeWire.message(
            json(
                """
                {"info":{"id":"m","role":"user","time":{"created":1}},
                 "parts":[{"id":"p","type":"file","mime":"image/png","filename":"shot.png",
                           "url":"data:image/png;base64,AAAA"}]}
                """.trimIndent(),
            ),
        ) as ChatMessage.User

        assertEquals(1, message.attachments.size)
        assertEquals("shot.png", message.attachments[0].name)
        assertEquals("data:image/png;base64,AAAA", message.attachments[0].uri)
    }

    @Test
    fun aMessageWithNoIdIsRejected() {
        assertNull(OpenCodeWire.message(json("""{"info":{"role":"user"}}""")))
    }

    // ---- parts ----------------------------------------------------------

    @Test
    fun everyToolStateMaps() {
        val pending = OpenCodeWire.toolState(json("""{"status":"pending"}"""))
        assertEquals(ToolStatus.Pending, pending.status)

        val running = OpenCodeWire.toolState(
            json("""{"status":"running","title":"Reading","input":{"file":"a.kt"},"time":{"start":100}}"""),
        )
        assertEquals(ToolStatus.Running, running.status)
        assertEquals("Reading", running.title)
        assertNotNull(running.input)
        assertNull(running.durationMs)

        val completed = OpenCodeWire.toolState(
            json(
                """{"status":"completed","title":"Read","output":"ok","input":{},"time":{"start":100,"end":350}}""",
            ),
        )
        assertEquals(ToolStatus.Completed, completed.status)
        assertEquals("ok", completed.output)
        assertEquals(250L, completed.durationMs)

        val failed = OpenCodeWire.toolState(
            json("""{"status":"error","error":"boom","input":{},"time":{"start":100,"end":200}}"""),
        )
        assertEquals(ToolStatus.Failed, failed.status)
        assertEquals("boom", failed.output)
        assertEquals(100L, failed.durationMs)
    }

    @Test
    fun anUnknownToolStatusDoesNotThrow() {
        assertEquals(ToolStatus.Pending, OpenCodeWire.toolState(json("""{"status":"invented"}""")).status)
        assertEquals(ToolStatus.Pending, OpenCodeWire.toolState(null).status)
    }

    @Test
    fun reasoningRecordsItsDurationFromTheTimePair() {
        val part = OpenCodeWire.part(
            json("""{"id":"p","type":"reasoning","text":"hmm","time":{"start":1000,"end":4000}}"""),
        )
        assertTrue(part is Part.Reasoning)

        val reasoning = part as Part.Reasoning
        assertEquals("hmm", reasoning.text)
        assertEquals(3, reasoning.seconds)
        assertFalse(reasoning.streaming)
    }

    @Test
    fun aTextPartWithoutAnEndTimeIsStreaming() {
        val part = OpenCodeWire.part(json("""{"id":"p","type":"text","text":"partial"}"""))
        assertTrue((part as Part.Text).streaming)
    }

    @Test
    fun anUnknownPartTypeBecomesANoteRatherThanVanishing() {
        // The server adds union variants between minor versions; a client that
        // silently drops them shows an unexplained gap.
        val part = OpenCodeWire.part(json("""{"id":"p","type":"invented-in-future"}"""))
        assertTrue(part is Part.Note)
        assertTrue((part as Part.Note).text.contains("invented-in-future"))
    }

    @Test
    fun compactionAndRetryBecomeReadableNotes() {
        val compaction = OpenCodeWire.part(json("""{"id":"p","type":"compaction","auto":true}"""))
        assertTrue((compaction as Part.Note).text.contains("automatically"))

        val retry = OpenCodeWire.part(
            json("""{"id":"p","type":"retry","error":{"name":"APIError","data":{"message":"overloaded"}}}"""),
        )
        assertTrue((retry as Part.Note).text.contains("overloaded"))
    }

    @Test
    fun aToolPartCarriesItsCallIdAndName() {
        val part = OpenCodeWire.part(
            json("""{"id":"p","type":"tool","callID":"call_1","tool":"read","state":{"status":"pending"}}"""),
        )
        assertTrue(part is Part.Tool)
        assertEquals("call_1", (part as Part.Tool).toolId)
        assertEquals("read", part.name)
    }

    // ---- errors ---------------------------------------------------------

    @Test
    fun errorTextComesFromTheUnionDataField() {
        assertEquals(
            "rate limited",
            OpenCodeWire.errorMessage(
                json("""{"name":"APIError","data":{"message":"rate limited"}}"""),
            ),
        )
        assertNull(OpenCodeWire.errorMessage(null))
        assertEquals(
            "ProviderAuthError",
            OpenCodeWire.errorMessage(json("""{"name":"ProviderAuthError"}""")),
        )
    }

    // ---- catalogue ------------------------------------------------------

    @Test
    fun providersRespectTheConnectedList() {
        val providers = OpenCodeWire.providers(fixture("providers.json"))
        assertTrue(providers.isNotEmpty())
        assertTrue(providers.all { it.id.isNotEmpty() })
        assertTrue(providers.all { it.name.isNotEmpty() })
    }

    @Test
    fun modelsCarryCostLimitsCapabilitiesAndVariants() {
        val models = OpenCodeWire.models(fixture("providers.json"))
        assertTrue(models.isNotEmpty())

        val model = models.first()
        assertTrue(model.id.isNotEmpty())
        assertTrue(model.providerId.isNotEmpty())
        assertTrue(model.providerName.isNotEmpty())
        assertTrue(model.contextLimit > 0)
        assertTrue(model.name.isNotEmpty())
        assertTrue(model.inputCostPerM >= 0)
    }

    @Test
    fun theDefaultModelIsFlagged() {
        val models = OpenCodeWire.models(
            json(
                """
                {"all":[{"id":"p","name":"P","models":{
                  "m1":{"id":"m1","capabilities":{},"limit":{},"cost":{}},
                  "m2":{"id":"m2","capabilities":{},"limit":{},"cost":{}}}}]}
                """.trimIndent(),
            ),
            mapOf("p" to "m2"),
        )
        assertEquals(2, models.size)
        assertFalse(models.first { it.id == "m1" }.isDefault)
        assertTrue(models.first { it.id == "m2" }.isDefault)
    }

    @Test
    fun aDeprecatedModelIsMarked() {
        val models = OpenCodeWire.models(
            json("""{"all":[{"id":"p","models":{"m":{"id":"m","status":"deprecated"}}}]}"""),
        )
        assertTrue(models.single().deprecated)
    }

    @Test
    fun agentsHideSubagentsFromThePrimaryList() {
        val agents = OpenCodeWire.agents(fixture("agents.json"))
        assertTrue(agents.isNotEmpty())
        assertTrue("build should be present", agents.any { it.id == "build" })
        assertTrue("hidden agents must be excluded", agents.none { it.id == "__hidden__" })
    }

    @Test
    fun commandsCarryTheirSource() {
        val commands = OpenCodeWire.commands(fixture("commands.json"))
        assertTrue(commands.isNotEmpty())
        assertTrue(commands.all { it.name.isNotEmpty() })
    }

    // ---- events ---------------------------------------------------------

    @Test
    fun theRealServerConnectedFrameMaps() {
        val event = OpenCodeWire.event(
            json("""{"id":"evt_1","type":"server.connected","properties":{}}"""),
        )
        assertTrue(event is ServerEvent.Connected)
    }

    @Test
    fun payloadLivesUnderPropertiesNotData() {
        val event = OpenCodeWire.event(
            json("""{"id":"e","type":"session.idle","properties":{"sessionID":"ses_1"}}"""),
        )
        assertEquals(ServerEvent.SessionIdle("ses_1"), event)

        assertNull(
            "a payload at `data` must not be read as if it were `properties`",
            OpenCodeWire.event(json("""{"id":"e","type":"session.idle","data":{"sessionID":"x"}}""")),
        )
    }

    @Test
    fun aDeletedSessionRemovesIt() {
        assertEquals(
            ServerEvent.SessionRemoved("ses_1"),
            OpenCodeWire.event(json("""{"type":"session.deleted","properties":{"sessionID":"ses_1"}}""")),
        )
    }

    @Test
    fun aPartUpdateCarriesItsMessageId() {
        val event = OpenCodeWire.event(
            json(
                """
                {"type":"message.part.updated","properties":{"sessionID":"ses_1",
                 "part":{"id":"p","messageID":"msg_1","type":"text","text":"hi"}}}
                """.trimIndent(),
            ),
        )
        assertTrue(event is ServerEvent.PartUpserted)
        assertEquals("ses_1", (event as ServerEvent.PartUpserted).sessionId)
        assertEquals("msg_1", event.messageId)
    }

    @Test
    fun aPermissionRequestMapsItsKindAndResources() {
        val event = OpenCodeWire.event(
            json(
                """
                {"type":"permission.asked","properties":{"id":"per_1","sessionID":"ses_1",
                 "permission":"edit","patterns":["src/Main.kt"],"metadata":{}}}
                """.trimIndent(),
            ),
        )
        assertTrue(event is ServerEvent.PermissionRequested)
        val request = (event as ServerEvent.PermissionRequested).request
        assertEquals("per_1", request.id)
        assertEquals("ses_1", request.sessionId)
        assertEquals(PermissionKind.Edit, request.kind)
        assertEquals(listOf("src/Main.kt"), request.resources)
    }

    @Test
    fun aSessionErrorSurfacesItsMessage() {
        val event = OpenCodeWire.event(
            json(
                """
                {"type":"session.error","properties":{"sessionID":"ses_1",
                 "error":{"name":"APIError","data":{"message":"overloaded"}}}}
                """.trimIndent(),
            ),
        )
        assertEquals(ServerEvent.SessionFailed("ses_1", "overloaded"), event)
    }

    @Test
    fun anUnmappedEventTypeIsDroppedRatherThanThrowing() {
        // The stream carries ~89 types and is documented as lossy.
        assertNull(OpenCodeWire.event(json("""{"type":"lsp.updated","properties":{}}""")))
        assertNull(OpenCodeWire.event(json("""{"type":"pty.exited","properties":{}}""")))
    }

    @Test
    fun anEventWithoutPropertiesIsDropped() {
        assertNull(OpenCodeWire.event(json("""{"type":"session.idle"}""")))
    }

    // ---- request bodies -------------------------------------------------

    @Test
    fun promptBodyCarriesTextModelAgentAndMessageId() {
        val body = parseJson(
            OpenCodeWire.promptBody(
                text = "hello",
                messageId = "msg_local",
                model = dev.edgecode.core.model.ModelRef("anthropic", "claude"),
                agent = "build",
                attachments = emptyList(),
            ),
        )

        assertEquals("msg_local", body.string("messageID"))
        assertEquals("build", body.string("agent"))
        assertEquals("anthropic", body.obj("model")?.string("providerID"))
        assertEquals("claude", body.obj("model")?.string("modelID"))

        val part = body.arr("parts").single()
        assertEquals("text", part.string("type"))
        assertEquals("hello", part.string("text"))
    }

    @Test
    fun promptBodyEncodesAttachmentsAsFilePartsWithDataUris() {
        val body = parseJson(
            OpenCodeWire.promptBody(
                text = "",
                messageId = null,
                model = null,
                agent = null,
                attachments = listOf(
                    dev.edgecode.core.model.Attachment(
                        id = "a1",
                        name = "shot.png",
                        mime = "image/png",
                        sizeBytes = 12,
                        kind = dev.edgecode.core.model.AttachmentKind.Image,
                        uri = "data:image/png;base64,AAAA",
                    ),
                ),
            ),
        )

        assertEquals(1, body.arr("parts").size)
        val file = body.arr("parts").single()
        assertEquals("file", file.string("type"))
        assertEquals("image/png", file.string("mime"))
        assertEquals("data:image/png;base64,AAAA", file.string("url"))
    }

    @Test
    fun anAttachmentWithoutAUriIsSkippedRatherThanSentBroken() {
        val body = parseJson(
            OpenCodeWire.promptBody(
                text = "hi",
                messageId = null,
                model = null,
                agent = null,
                attachments = listOf(
                    dev.edgecode.core.model.Attachment(
                        id = "a1",
                        name = "x",
                        mime = "image/png",
                        sizeBytes = 1,
                        kind = dev.edgecode.core.model.AttachmentKind.Image,
                        uri = null,
                    ),
                ),
            ),
        )
        assertEquals(1, body.arr("parts").size)
    }

    @Test
    fun replyBodyUsesTheReplyFieldTheServerExpects() {
        assertEquals("""{"reply":"once"}""", OpenCodeWire.replyBody("once"))
        assertEquals("""{"reply":"always"}""", OpenCodeWire.replyBody("always"))
        assertEquals("""{"reply":"reject"}""", OpenCodeWire.replyBody("reject"))
    }

    @Test
    fun bodiesEncodeAsValidJsonAgain() {
        // Every body is built from JsonValue, so it must survive a reparse.
        val body = OpenCodeWire.promptBody("quote \" and \\ backslash", null, null, null, emptyList())
        assertEquals(
            "quote \" and \\ backslash",
            parseJson(body).arr("parts").single().string("text"),
        )
        assertEquals(body, parseJson(body).encode())
    }
}
