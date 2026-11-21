package com.depromeet.team1.core.llm

import com.depromeet.team1.core.common.error.AppException
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class LlmServiceTest {
    private val repository = FakeLlmUsageRepository()
    private val client =
        FakeLlmClient(LlmResponse(text = "요약", model = "model-a", inputTokens = 300, outputTokens = 40))
    private val clock = Clock.fixed(NOW, ZoneOffset.UTC)
    private val service = LlmService(
        client,
        repository,
        LlmUsageSettings(memberDailyTokenLimit = 1_000, globalDailyTokenLimit = 5_000),
        clock,
    )
    private val request = LlmRequest(systemInstruction = "지시문", prompt = "프롬프트")

    @Test
    fun `호출하고 응답을 돌려준다`() {
        val response = service.generate(
            memberId = 1,
            purpose = LlmPurpose.ANALYZE,
            request = request,
            processingJobId = 7,
        )

        assertEquals("요약", response.text)
        assertEquals(listOf(request), client.requests)
    }

    @Test
    fun `호출 한 건의 사용량을 기록한다`() {
        service.generate(memberId = 1, purpose = LlmPurpose.ANALYZE, request = request, processingJobId = 7)

        assertEquals(
            listOf(LlmUsage(1, 1, LlmPurpose.ANALYZE, "model-a", 300, 40, 7, null, NOW)),
            repository.all(),
        )
    }

    @Test
    fun `채팅 호출이면 답변 메시지 ID 로 기록한다`() {
        service.generate(memberId = 1, purpose = LlmPurpose.CHAT, request = request, messageId = 9)

        val usage = repository.all().single()
        assertEquals(LlmPurpose.CHAT, usage.purpose)
        assertEquals(9, usage.messageId)
        assertEquals(null, usage.processingJobId)
    }

    @Test
    fun `상한을 넘었으면 호출하지 않고 기록도 남기지 않는다`() {
        repository.save(LlmUsage(null, 1, LlmPurpose.ANALYZE, "model-a", 1_000, 0, null, null, NOW))

        val e =
            assertFailsWith<AppException> {
                service.generate(memberId = 1, purpose = LlmPurpose.ANALYZE, request = request)
            }

        assertEquals(LlmError.LLM_LIMIT_EXCEEDED, e.errorCode)
        assertTrue(client.requests.isEmpty())
        assertEquals(1, repository.all().size)
    }

    @Test
    fun `호출이 실패하면 사용량을 기록하지 않는다`() {
        client.failWith = AppException(LlmError.LLM_UNAVAILABLE, "status=500")

        assertFailsWith<AppException> {
            service.generate(memberId = 1, purpose = LlmPurpose.ANALYZE, request = request)
        }

        assertTrue(repository.all().isEmpty())
    }

    @Test
    fun `텍스트가 없는 응답이면 사용량을 기록한 뒤 연동 실패로 끝낸다`() {
        client.response = LlmResponse(text = "", model = "model-a", inputTokens = 300, outputTokens = 0)

        val e = assertFailsWith<AppException> {
            service.generate(memberId = 1, purpose = LlmPurpose.ANALYZE, request = request, processingJobId = 7)
        }

        assertEquals(LlmError.LLM_UNAVAILABLE, e.errorCode)
        assertEquals(listOf(LlmUsage(1, 1, LlmPurpose.ANALYZE, "model-a", 300, 0, 7, null, NOW)), repository.all())
    }

    @Test
    fun `텍스트가 없는 응답의 토큰도 상한 계산에 들어간다`() {
        client.response = LlmResponse(text = "", model = "model-a", inputTokens = 1_000, outputTokens = 0)
        assertFailsWith<AppException> {
            service.generate(memberId = 1, purpose = LlmPurpose.ANALYZE, request = request)
        }
        client.response = LlmResponse(text = "요약", model = "model-a", inputTokens = 10, outputTokens = 1)

        val e = assertFailsWith<AppException> {
            service.generate(memberId = 1, purpose = LlmPurpose.ANALYZE, request = request)
        }

        assertEquals(LlmError.LLM_LIMIT_EXCEEDED, e.errorCode)
    }

    companion object {
        val NOW: Instant = Instant.parse("2026-11-21T03:00:00Z")
    }
}
