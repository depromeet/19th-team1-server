package com.depromeet.team1.infra.security.jwt

import com.depromeet.team1.core.member.AccessTokenIssuer
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.boot.test.system.CapturedOutput
import org.springframework.boot.test.system.OutputCaptureExtension
import org.springframework.security.oauth2.jwt.JwtDecoder
import java.time.Clock
import java.util.Base64
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@ExtendWith(OutputCaptureExtension::class)
class JwtConfigurationTest {
    private val runner = ApplicationContextRunner()
        .withUserConfiguration(JwtConfiguration::class.java)
        .withBean(Clock::class.java, { Clock.systemUTC() })

    @Test
    fun `dev 와 prod 에서는 서명 키가 없으면 기동하지 않는다`() {
        for (profile in listOf("dev", "prod")) {
            runner
                .withInitializer { it.environment.setActiveProfiles(profile) }
                .run { context -> assertNotNull(context.startupFailure, "$profile 프로필은 기동에 실패해야 한다") }
        }
    }

    @Test
    fun `서명 키가 32바이트보다 짧으면 어느 프로필에서도 기동하지 않는다`() {
        runner
            .withInitializer { it.environment.setActiveProfiles("local") }
            .withPropertyValues("jwt.secret=${Base64.getEncoder().encodeToString(ByteArray(31))}")
            .run { context -> assertNotNull(context.startupFailure) }
    }

    @Test
    fun `서명 키가 있으면 발급한 토큰을 같은 컨텍스트의 디코더가 검증한다`() {
        runner
            .withInitializer { it.environment.setActiveProfiles("prod") }
            .withPropertyValues("jwt.secret=${Base64.getEncoder().encodeToString(ByteArray(32) { 1 })}")
            .run { context ->
                assertNull(context.startupFailure)
                val token = context.getBean(AccessTokenIssuer::class.java).issue(7, Clock.systemUTC().instant())
                assertEquals("7", context.getBean(JwtDecoder::class.java).decode(token.value).subject)
            }
    }

    @Test
    fun `local 에서는 서명 키가 없어도 임시 키로 기동하고 경고를 남긴다`(output: CapturedOutput) {
        runner
            .withInitializer { it.environment.setActiveProfiles("local") }
            .run { context ->
                assertNull(context.startupFailure)
                val token = context.getBean(AccessTokenIssuer::class.java).issue(7, Clock.systemUTC().instant())
                assertEquals("7", context.getBean(JwtDecoder::class.java).decode(token.value).subject)
            }

        assertTrue(output.out.contains("JWT 서명 키가 비어 있다"))
    }
}
