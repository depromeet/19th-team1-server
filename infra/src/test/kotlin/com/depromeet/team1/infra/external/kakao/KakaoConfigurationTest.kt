package com.depromeet.team1.infra.external.kakao

import com.depromeet.team1.core.member.OAuthClient
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.boot.test.system.CapturedOutput
import org.springframework.boot.test.system.OutputCaptureExtension
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@ExtendWith(OutputCaptureExtension::class)
class KakaoConfigurationTest {
    private val runner = ApplicationContextRunner().withUserConfiguration(KakaoConfiguration::class.java)

    @Test
    fun `dev 와 prod 에서는 카카오 앱 정보가 비어 있으면 기동하지 않는다`() {
        for (profile in listOf("dev", "prod")) {
            runner
                .withInitializer { it.environment.setActiveProfiles(profile) }
                .withPropertyValues("kakao.client-id=", "kakao.client-secret=s", "kakao.redirect-uri=r")
                .run { context -> assertNotNull(context.startupFailure, "$profile 프로필은 기동에 실패해야 한다") }
        }
    }

    @Test
    fun `dev 에서 카카오 앱 정보가 모두 있으면 기동한다`() {
        runner
            .withInitializer { it.environment.setActiveProfiles("dev") }
            .withPropertyValues("kakao.client-id=c", "kakao.client-secret=s", "kakao.redirect-uri=r")
            .run { context ->
                assertNull(context.startupFailure)
                assertNotNull(context.getBean(OAuthClient::class.java))
            }
    }

    @Test
    fun `local 에서는 카카오 앱 정보가 없어도 기동하고 경고를 남긴다`(output: CapturedOutput) {
        runner
            .withInitializer { it.environment.setActiveProfiles("local") }
            .run { context ->
                assertNull(context.startupFailure)
                assertNotNull(context.getBean(OAuthClient::class.java))
            }

        assertTrue(output.out.contains("카카오 로그인 앱 정보가 비어 있다"))
    }
}
