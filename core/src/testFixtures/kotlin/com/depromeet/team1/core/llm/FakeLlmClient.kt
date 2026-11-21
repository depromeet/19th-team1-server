package com.depromeet.team1.core.llm

/**
 * 정해 둔 응답을 돌려주는 [LlmClient]. [failWith] 를 주면 실제 구현이 외부 오류를 바꿔 던지듯 그 예외를 던진다.
 */
class FakeLlmClient(
    var response: LlmResponse = LlmResponse(text = "응답", model = "fake-model", inputTokens = 100, outputTokens = 20),
) : LlmClient {
    var failWith: RuntimeException? = null

    /** 테스트 확인용: 받은 요청. */
    val requests = mutableListOf<LlmRequest>()

    override fun generate(request: LlmRequest): LlmResponse {
        requests += request
        failWith?.let { throw it }
        return response
    }
}
