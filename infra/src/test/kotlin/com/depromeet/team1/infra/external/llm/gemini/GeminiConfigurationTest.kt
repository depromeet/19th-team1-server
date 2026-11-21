package com.depromeet.team1.infra.external.llm.gemini

import com.depromeet.team1.core.llm.LlmClient
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.boot.test.system.CapturedOutput
import org.springframework.boot.test.system.OutputCaptureExtension
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@ExtendWith(OutputCaptureExtension::class)
class GeminiConfigurationTest {
    private val runner = ApplicationContextRunner().withUserConfiguration(GeminiConfiguration::class.java)

    @Test
    fun `dev 와 prod 에서는 Gemini 키가 비어 있으면 기동하지 않는다`() {
        for (profile in listOf("dev", "prod")) {
            runner
                .withInitializer { it.environment.setActiveProfiles(profile) }
                .withPropertyValues("gemini.api-key=")
                .run { context -> assertNotNull(context.startupFailure, "$profile 프로필은 기동에 실패해야 한다") }
        }
    }

    @Test
    fun `dev 에서 Gemini 키가 있으면 기동한다`() {
        runner
            .withInitializer { it.environment.setActiveProfiles("dev") }
            .withPropertyValues("gemini.api-key=k")
            .run { context ->
                assertNull(context.startupFailure)
                assertNotNull(context.getBean(LlmClient::class.java))
            }
    }

    @Test
    fun `local 에서는 Gemini 키가 없어도 기동하고 경고를 남긴다`(output: CapturedOutput) {
        runner
            .withInitializer { it.environment.setActiveProfiles("local") }
            // 실행 환경에 GEMINI_API_KEY 가 있어도 비어 있는 경우를 확인하도록 직접 비운다.
            .withPropertyValues("gemini.api-key=")
            .run { context ->
                assertNull(context.startupFailure)
                assertNotNull(context.getBean(LlmClient::class.java))
            }

        assertTrue(output.out.contains("Gemini API 키가 비어 있다"))
    }
}
