package com.depromeet.team1.infra.external.llm.gemini

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

/**
 * Gemini 설정. 키는 환경변수 GEMINI_API_KEY 로만 받는다(docs/deployment.md).
 * 모델·응답 타임아웃은 application.yml 이 GEMINI_MODEL, LLM_TIMEOUT 으로 채운다.
 * 키가 비어 있을 때의 처리는 [GeminiConfiguration] 이 프로필에 따라 정한다.
 */
@ConfigurationProperties("gemini")
data class GeminiProperties(
    val apiKey: String = "",
    val model: String = "gemini-3.5-flash-lite",
    val baseUrl: String = "https://generativelanguage.googleapis.com",
    val connectTimeout: Duration = Duration.ofSeconds(3),
    /** 처리 작업의 가시성 제한 시간(JOB_VISIBILITY_TIMEOUT)보다 짧게, 링크 수집 타임아웃과 합쳐도 넘지 않게 둔다. */
    val readTimeout: Duration = Duration.ofSeconds(60),
) {
    fun isConfigured(): Boolean = apiKey.isNotBlank()
}
