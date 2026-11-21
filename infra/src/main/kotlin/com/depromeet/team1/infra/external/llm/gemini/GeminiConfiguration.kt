package com.depromeet.team1.infra.external.llm.gemini

import org.slf4j.LoggerFactory
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.env.Environment
import org.springframework.core.env.Profiles

/**
 * LLM 벤더 구현으로 Gemini 를 띄운다. 벤더를 바꾸면 이 설정 대신 다른 벤더의 설정이 `LlmClient` 빈을 만든다.
 *
 * 키(GEMINI_API_KEY)가 비어 있으면 dev·prod 는 기동을 멈추고, 그 밖(local)은 경고만 남기고 뜬다. local 에서는 LLM 호출만 실패한다.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(GeminiProperties::class)
class GeminiConfiguration {
    @Bean
    fun geminiLlmClient(properties: GeminiProperties, environment: Environment): GeminiLlmClient {
        if (!properties.isConfigured()) {
            check(!environment.acceptsProfiles(Profiles.of("dev | prod"))) {
                "Gemini API 키가 비어 있다. GEMINI_API_KEY 를 넣는다"
            }
            log.warn("Gemini API 키가 비어 있다. LLM 호출은 실패한다(GEMINI_API_KEY)")
        }
        return GeminiLlmClient(properties)
    }

    companion object {
        private val log = LoggerFactory.getLogger(GeminiConfiguration::class.java)
    }
}
