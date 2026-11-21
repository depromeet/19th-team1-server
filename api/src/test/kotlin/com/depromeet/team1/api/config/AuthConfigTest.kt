package com.depromeet.team1.api.config

import com.depromeet.team1.core.member.AuthSettings
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import java.time.Duration
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class AuthConfigTest {
    private val runner = ApplicationContextRunner().withUserConfiguration(AuthConfig::class.java)

    @Test
    fun `리프레시 토큰 재사용 유예 시간을 정하지 않으면 30초다`() {
        runner.run { context ->
            assertEquals(Duration.ofSeconds(30), context.getBean(AuthSettings::class.java).refreshTokenReuseGrace)
        }
    }

    @Test
    fun `리프레시 토큰 재사용 유예 시간은 설정값을 따른다`() {
        runner
            .withPropertyValues("auth.refresh-token-reuse-grace=10s")
            .run { context ->
                assertEquals(Duration.ofSeconds(10), context.getBean(AuthSettings::class.java).refreshTokenReuseGrace)
            }
    }

    @Test
    fun `유예 시간이 음수면 기동하지 않는다`() {
        runner
            .withPropertyValues("auth.refresh-token-reuse-grace=-1s")
            .run { context -> assertNotNull(context.startupFailure) }
    }
}
