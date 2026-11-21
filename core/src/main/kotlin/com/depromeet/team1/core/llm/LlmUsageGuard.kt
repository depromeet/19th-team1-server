package com.depromeet.team1.core.llm

import com.depromeet.team1.core.common.error.AppException
import java.time.Clock
import java.time.LocalDate

/**
 * LLM 사용량 상한 검사. 호출 전의 오늘 합계로 검사하는 느슨한 상한이라, 동시에 들어온 호출이 함께 통과해 조금 넘을 수 있다.
 * 상한에 걸린 처리 작업은 재시도하지 않고 FAILED 로 끝낸다(`docs/architecture.md` 처리 작업 상태 전이).
 *
 * Spring 빈이 아니다. [LlmService] 가 만들어 쓴다.
 */
class LlmUsageGuard(
    private val usageRepository: LlmUsageRepository,
    private val settings: LlmUsageSettings,
    private val clock: Clock,
) {
    /** 사용자별·전체 상한 중 하나라도 이미 찼으면 [LlmError.LLM_LIMIT_EXCEEDED] 를 던진다. */
    fun check(memberId: Long) {
        val since = LocalDate.now(clock.withZone(settings.zone)).atStartOfDay(settings.zone).toInstant()
        if (usageRepository.sumTokensByMemberSince(memberId, since) >= settings.memberDailyTokenLimit) {
            throw AppException(LlmError.LLM_LIMIT_EXCEEDED, "scope=MEMBER memberId=$memberId")
        }
        if (usageRepository.sumTokensSince(since) >= settings.globalDailyTokenLimit) {
            throw AppException(LlmError.LLM_LIMIT_EXCEEDED, "scope=GLOBAL")
        }
    }
}
