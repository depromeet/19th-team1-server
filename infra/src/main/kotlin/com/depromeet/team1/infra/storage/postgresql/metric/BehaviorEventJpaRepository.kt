package com.depromeet.team1.infra.storage.postgresql.metric

import org.springframework.data.jpa.repository.JpaRepository

interface BehaviorEventJpaRepository : JpaRepository<BehaviorEventJpaEntity, Long>
