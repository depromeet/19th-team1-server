package com.depromeet.team1.infra.external.llm.gemini

import com.depromeet.team1.core.common.error.AppException
import com.depromeet.team1.core.llm.LlmError
import com.depromeet.team1.core.llm.LlmRequest
import com.depromeet.team1.core.llm.LlmResponse
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import tools.jackson.databind.json.JsonMapper
import java.time.Duration
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GeminiLlmClientTest {
    private val server = MockWebServer()
    private lateinit var client: GeminiLlmClient
    private val request = LlmRequest(systemInstruction = "지시문", prompt = "프롬프트")

    @BeforeEach
    fun setUp() {
        server.start()
        // 응답 타임아웃은 본문을 다 읽을 때까지 걸린다. 첫 호출의 JSON 변환기 초기화가 느린 CI 에서도 넘지 않게 넉넉히 둔다.
        client = clientWith(apiKey = "test-api-key", readTimeout = Duration.ofSeconds(5))
    }

    @AfterEach
    fun tearDown() {
        server.close()
    }

    private fun clientWith(apiKey: String, readTimeout: Duration) = GeminiLlmClient(
        GeminiProperties(
            apiKey = apiKey,
            model = "gemini-test",
            baseUrl = server.url("/").toString().removeSuffix("/"),
            connectTimeout = Duration.ofMillis(500),
            readTimeout = readTimeout,
        ),
    )

    @Test
    fun `응답 텍스트와 토큰 수를 돌려준다`() {
        server.enqueue(json(200, SUCCESS_BODY))

        val response = client.generate(request)

        // 출력 토큰은 응답 토큰과 생각 토큰의 합이다. 둘 다 출력 요금으로 과금된다.
        assertEquals(
            LlmResponse(text = "요약입니다", model = "gemini-test-001", inputTokens = 120, outputTokens = 45),
            response,
        )
    }

    @Test
    fun `모델 경로로 보내고 키는 헤더에 싣는다`() {
        server.enqueue(json(200, SUCCESS_BODY))

        client.generate(request)

        val recorded = server.takeRequest()
        assertEquals("POST", recorded.method)
        assertEquals("/v1beta/models/gemini-test:generateContent", recorded.url.encodedPath)
        assertEquals("test-api-key", recorded.headers["x-goog-api-key"])
        assertEquals(null, recorded.url.queryParameter("key"))
    }

    @Test
    fun `지시문과 프롬프트를 요청 본문에 담는다`() {
        server.enqueue(json(200, SUCCESS_BODY))

        client.generate(request)

        val body = JsonMapper.builder().build().readTree(server.takeRequest().body!!.utf8())
        assertEquals("지시문", body.at("/systemInstruction/parts/0/text").asString())
        assertEquals("user", body.at("/contents/0/role").asString())
        assertEquals("프롬프트", body.at("/contents/0/parts/0/text").asString())
    }

    @Test
    fun `지시문이 없으면 보내지 않는다`() {
        server.enqueue(json(200, SUCCESS_BODY))

        client.generate(LlmRequest(systemInstruction = null, prompt = "프롬프트"))

        val body = JsonMapper.builder().build().readTree(server.takeRequest().body!!.utf8())
        assertFalse(body.has("systemInstruction"))
    }

    @Test
    fun `여러 조각으로 온 텍스트는 이어 붙인다`() {
        server.enqueue(
            json(
                200,
                """{"candidates":[{"content":{"parts":[{"text":"앞"},{"text":"뒤"}],"role":"model"}}],
                   "usageMetadata":{"promptTokenCount":1,"candidatesTokenCount":2},"modelVersion":"m"}""",
            ),
        )

        assertEquals("앞뒤", client.generate(request).text)
    }

    @Test
    fun `호출 한도 초과는 다른 실패와 구분한다`() {
        server.enqueue(json(429, """{"error":{"code":429,"status":"RESOURCE_EXHAUSTED"}}"""))

        val error = assertFailsWith<AppException> { client.generate(request) }

        assertEquals(LlmError.LLM_RATE_LIMITED, error.errorCode)
    }

    @Test
    fun `서버 오류는 연동 실패로 바꾼다`() {
        server.enqueue(json(500, """{"error":{"code":500,"status":"INTERNAL"}}"""))

        val error = assertFailsWith<AppException> { client.generate(request) }

        assertEquals(LlmError.LLM_UNAVAILABLE, error.errorCode)
    }

    @Test
    fun `텍스트가 없어도 사용량이 있으면 빈 텍스트와 토큰 수를 돌려준다`() {
        // 안전 필터에 막히면 후보에 내용이 없다. 입력 토큰은 이미 과금됐으므로 실패 판단은 LlmService 가 사용량을 기록한 뒤 한다.
        server.enqueue(
            json(
                200,
                """{"candidates":[{"finishReason":"SAFETY"}],"usageMetadata":{"promptTokenCount":10},"modelVersion":"m"}""",
            ),
        )

        val response = client.generate(request)

        assertEquals(LlmResponse(text = "", model = "m", inputTokens = 10, outputTokens = 0), response)
    }

    @Test
    fun `텍스트도 사용량도 없는 응답은 연동 실패로 바꾼다`() {
        server.enqueue(json(200, """{"candidates":[{"finishReason":"SAFETY"}]}"""))

        val error = assertFailsWith<AppException> { client.generate(request) }

        assertEquals(LlmError.LLM_UNAVAILABLE, error.errorCode)
    }

    @Test
    fun `형식이 맞지 않는 응답은 연동 실패로 바꾼다`() {
        server.enqueue(json(200, "not-json"))

        val error = assertFailsWith<AppException> { client.generate(request) }

        assertEquals(LlmError.LLM_UNAVAILABLE, error.errorCode)
    }

    @Test
    fun `응답 시간을 넘기면 연동 실패로 바꾼다`() {
        server.enqueue(MockResponse.Builder().code(200).headersDelay(2, TimeUnit.SECONDS).body(SUCCESS_BODY).build())
        val impatient = clientWith(apiKey = "test-api-key", readTimeout = Duration.ofMillis(300))

        val error = assertFailsWith<AppException> { impatient.generate(request) }

        assertEquals(LlmError.LLM_UNAVAILABLE, error.errorCode)
    }

    @Test
    fun `응답 본문과 프롬프트는 예외에 남기지 않는다`() {
        server.enqueue(json(400, """{"error":{"message":"gemini-body-marker"}}"""))

        val error = assertFailsWith<AppException> { client.generate(LlmRequest(null, "prompt-marker")) }

        val messages = generateSequence<Throwable>(error) { it.cause }.mapNotNull { it.message }.toList()
        assertTrue(messages.none { "gemini-body-marker" in it || "prompt-marker" in it }, "예외 메시지에 원문이 남았다: $messages")
    }

    @Test
    fun `키가 없으면 부르지 않고 연동 실패로 끝낸다`() {
        val unconfigured = clientWith(apiKey = "", readTimeout = Duration.ofSeconds(5))

        val error = assertFailsWith<AppException> { unconfigured.generate(request) }

        assertEquals(LlmError.LLM_UNAVAILABLE, error.errorCode)
        assertEquals(0, server.requestCount)
    }

    private fun json(code: Int, body: String): MockResponse = MockResponse.Builder()
        .code(code)
        .addHeader("Content-Type", "application/json;charset=UTF-8")
        .body(body)
        .build()

    companion object {
        private val SUCCESS_BODY = """
            {
              "candidates": [
                {"content": {"parts": [{"text": "요약입니다"}], "role": "model"}, "finishReason": "STOP"}
              ],
              "usageMetadata": {
                "promptTokenCount": 120, "candidatesTokenCount": 30, "thoughtsTokenCount": 15, "totalTokenCount": 165
              },
              "modelVersion": "gemini-test-001"
            }
        """.trimIndent()
    }
}
