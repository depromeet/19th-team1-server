package com.depromeet.team1.core.llm

import com.depromeet.team1.core.common.error.AppException
import org.springframework.stereotype.Service
import java.time.Clock

/**
 * LLM 호출의 입구. 상한 검사 → 호출 → 사용량 기록 순서로 한다.
 *
 * 외부 호출을 하므로 `@Transactional` 을 붙이지 않는다. 사용량은 [LlmUsageRepository.save] 가 스스로 커밋해,
 * 부른 쪽의 저장이 실패해도 쓴 토큰이 남는다.
 */
@Service
class LlmService(
    private val llmClient: LlmClient,
    private val usageRepository: LlmUsageRepository,
    settings: LlmUsageSettings,
    private val clock: Clock,
) {
    private val usageGuard = LlmUsageGuard(usageRepository, settings, clock)

    /**
     * 상한에 걸리면 호출하지 않고 [LlmError.LLM_LIMIT_EXCEEDED] 를 던진다. 호출 실패는 [LlmClient] 가 던진 예외가 그대로 나가고
     * 사용량은 기록하지 않는다. 응답에 텍스트가 없으면 쓴 토큰은 기록하고 [LlmError.LLM_UNAVAILABLE] 을 던진다.
     *
     * @param processingJobId 분석·임베딩 호출이면 처리 작업 ID
     * @param messageId 채팅 호출이면 답변 메시지 ID
     */
    fun generate(
        memberId: Long,
        purpose: LlmPurpose,
        request: LlmRequest,
        processingJobId: Long? = null,
        messageId: Long? = null,
    ): LlmResponse {
        usageGuard.check(memberId)
        val response = llmClient.generate(request)
        usageRepository.save(
            LlmUsage(
                id = null,
                memberId = memberId,
                purpose = purpose,
                model = response.model,
                inputTokens = response.inputTokens,
                outputTokens = response.outputTokens,
                processingJobId = processingJobId,
                messageId = messageId,
                createdAt = clock.instant(),
            ),
        )
        if (response.text.isBlank()) {
            throw AppException(LlmError.LLM_UNAVAILABLE, "empty text model=${response.model}")
        }
        return response
    }
}
