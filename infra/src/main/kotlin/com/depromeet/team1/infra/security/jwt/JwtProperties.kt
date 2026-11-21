package com.depromeet.team1.infra.security.jwt

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

/** 액세스 토큰 설정. 서명 키는 환경변수 JWT_SECRET(Base64, 32바이트 이상)으로만 받는다. */
@ConfigurationProperties("jwt")
data class JwtProperties(val secret: String = "", val accessTokenValidity: Duration = Duration.ofMinutes(30))
