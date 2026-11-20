package com.depromeet.team1.infra.storage.postgresql.metric

import com.depromeet.team1.core.metric.BehaviorEvent
import com.depromeet.team1.core.metric.BehaviorEventRepository
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional

@Repository
class JpaBehaviorEventRepository(private val behaviorEventJpaRepository: BehaviorEventJpaRepository) :
    BehaviorEventRepository {
    @Transactional
    override fun save(event: BehaviorEvent) {
        behaviorEventJpaRepository.save(event.toEntity())
    }
}

internal fun BehaviorEvent.toEntity() = BehaviorEventJpaEntity(
    id = null,
    eventName = eventName.code,
    memberId = memberId,
    properties = properties.ifEmpty { null },
    occurredAt = occurredAt,
)
