package com.depromeet.team1.core.llm

import java.time.Instant

/** LLM 호출 한 건의 토큰 사용량. 호출마다 따로 커밋해, 요청이 롤백돼도 쓴 토큰이 남게 한다. */
data class LlmUsage(
    val id: Long?,
    val memberId: Long,
    val purpose: LlmPurpose,
    val model: String,
    val inputTokens: Int,
    /** 임베딩은 0 이다. */
    val outputTokens: Int,
    /** 분석·임베딩 호출이면 처리 작업 ID. */
    val processingJobId: Long?,
    /** 채팅 호출이면 답변 메시지 ID. */
    val messageId: Long?,
    val createdAt: Instant,
) {
    init {
        require(model.isNotBlank() && model.length <= MODEL_MAX) { "모델 이름은 1~${MODEL_MAX}자다" }
        require(inputTokens >= 0 && outputTokens >= 0) { "토큰 수는 0 이상이다" }
    }

    companion object {
        const val MODEL_MAX = 50
    }
}

enum class LlmPurpose { ANALYZE, EMBED, CHAT }
