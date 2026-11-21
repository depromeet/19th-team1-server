package com.depromeet.team1.core.llm

/**
 * LLM 텍스트 생성. 벤더마다 구현을 두고 하나만 빈으로 띄운다(`docs/architecture.md` 경계 인터페이스).
 *
 * 직접 부르지 않고 [LlmService] 를 거친다. 사용량 상한 검사와 사용량 기록이 거기서 이뤄진다.
 * 외부 오류는 [LlmError] 의 [com.depromeet.team1.core.common.error.AppException] 으로 바꿔 던진다.
 * 호출 한도 초과(429)는 [LlmError.LLM_RATE_LIMITED], 그 밖의 실패는 [LlmError.LLM_UNAVAILABLE] 이다.
 *
 * 텍스트는 없지만 사용량은 온 응답(안전 필터 등)은 던지지 않고 빈 텍스트의 [LlmResponse] 로 돌려준다.
 * 이미 과금된 토큰을 [LlmService] 가 기록한 뒤 실패로 끝낸다. 텍스트도 사용량도 없으면 [LlmError.LLM_UNAVAILABLE] 을 던진다.
 */
interface LlmClient {
    fun generate(request: LlmRequest): LlmResponse
}

/** [systemInstruction] 은 모델에 주는 지시문, [prompt] 는 사용자 차례의 입력이다. 둘 다 로그에 남기지 않는다. */
data class LlmRequest(val systemInstruction: String?, val prompt: String) {
    init {
        require(prompt.isNotBlank()) { "프롬프트는 비어 있을 수 없다" }
    }
}

/** [model] 은 실제로 응답한 모델 이름이고 사용량 기록에 쓴다. [text] 가 비어 있으면 [LlmService] 가 실패로 끝낸다. */
data class LlmResponse(val text: String, val model: String, val inputTokens: Int, val outputTokens: Int)
