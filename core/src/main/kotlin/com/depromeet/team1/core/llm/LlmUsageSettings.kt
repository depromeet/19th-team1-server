package com.depromeet.team1.core.llm

import java.time.ZoneId

/**
 * LLM 사용량 상한. 값은 실행 모듈이 환경변수로 채운다(`docs/deployment.md` 5절).
 * 토큰 수는 입력과 출력의 합이고, 하루는 [zone] 의 자정부터다.
 */
data class LlmUsageSettings(
    val memberDailyTokenLimit: Long,
    val globalDailyTokenLimit: Long,
    val zone: ZoneId = ZoneId.of("Asia/Seoul"),
) {
    init {
        require(memberDailyTokenLimit >= 1) { "사용자별 상한은 1 이상이다" }
        require(globalDailyTokenLimit >= 1) { "전체 상한은 1 이상이다" }
    }
}
