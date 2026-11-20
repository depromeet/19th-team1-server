package com.depromeet.team1.infra.storage.postgresql.llm

import org.springframework.data.jpa.repository.JpaRepository

interface LlmUsageJpaRepository : JpaRepository<LlmUsageJpaEntity, Long>
