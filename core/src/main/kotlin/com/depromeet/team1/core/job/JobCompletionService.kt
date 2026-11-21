package com.depromeet.team1.core.job

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock

/**
 * 작업 완료와 결과 저장을 한 트랜잭션으로 묶는다. [ProcessingJobService] 와 따로 둔 것은 같은 빈 안에서 부르면
 * `@Transactional` 이 걸리지 않기 때문이다.
 */
@Service
class JobCompletionService(private val jobRepository: ProcessingJobRepository, private val clock: Clock) {
    /**
     * 차례 번호가 맞으면 DONE 으로 바꾸고 결과를 저장한다. 차례가 지났으면(다른 실행이 잡았거나 이미 끝남) 아무것도 저장하지 않고 false.
     * 결과 저장이 실패하면 완료도 롤백된다.
     */
    @Transactional
    fun complete(jobId: Long, attempt: Int, result: JobResult): Boolean {
        if (jobRepository.complete(jobId, attempt, clock.instant()) == 0) return false
        result.save()
        return true
    }
}
