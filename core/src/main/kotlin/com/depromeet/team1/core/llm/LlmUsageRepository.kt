package com.depromeet.team1.core.llm

interface LlmUsageRepository {
    fun save(usage: LlmUsage): LlmUsage

    fun findById(id: Long): LlmUsage?
}
