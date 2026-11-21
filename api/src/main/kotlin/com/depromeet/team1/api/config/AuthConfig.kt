package com.depromeet.team1.api.config

import com.depromeet.team1.core.member.AuthSettings
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.time.Duration

/** 인증 설정(auth.*)을 core 의 [AuthSettings] 로 넘긴다. 환경변수로 바꿀 수 있다(docs/deployment.md). */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(AuthProperties::class)
class AuthConfig {
    @Bean
    fun authSettings(properties: AuthProperties): AuthSettings =
        AuthSettings(refreshTokenReuseGrace = properties.refreshTokenReuseGrace)
}

@ConfigurationProperties("auth")
data class AuthProperties(
    /** 재발급으로 폐기된 리프레시 토큰이 다시 와도 탈취로 보지 않고 새 토큰 쌍을 주는 시간. 웹의 동시 재발급을 감안한다. */
    val refreshTokenReuseGrace: Duration = Duration.ofSeconds(30),
)
