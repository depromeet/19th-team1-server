package com.depromeet.team1.api.config

import com.depromeet.team1.core.llm.LlmUsageSettings
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/** LLM 사용량 상한(llm.*)을 core 의 [LlmUsageSettings] 로 넘긴다. 값과 기본값은 application.yml 에 있다(docs/deployment.md). */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(LlmProperties::class)
class LlmConfig {
    @Bean
    fun llmUsageSettings(properties: LlmProperties): LlmUsageSettings = LlmUsageSettings(
        memberDailyTokenLimit = properties.memberDailyTokenLimit,
        globalDailyTokenLimit = properties.globalDailyTokenLimit,
    )
}

@ConfigurationProperties("llm")
data class LlmProperties(
    /** 한 사용자가 하루(한국 시간 자정 기준)에 쓸 수 있는 입력·출력 토큰 합. */
    val memberDailyTokenLimit: Long,
    /** 모든 사용자가 하루에 쓸 수 있는 입력·출력 토큰 합. */
    val globalDailyTokenLimit: Long,
)
