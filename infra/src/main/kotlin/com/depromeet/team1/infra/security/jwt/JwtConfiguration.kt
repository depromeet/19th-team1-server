package com.depromeet.team1.infra.security.jwt

import com.nimbusds.jose.jwk.source.ImmutableSecret
import org.slf4j.LoggerFactory
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.env.Environment
import org.springframework.core.env.Profiles
import org.springframework.security.oauth2.jose.jws.MacAlgorithm
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.oauth2.jwt.JwtEncoder
import org.springframework.security.oauth2.jwt.JwtTimestampValidator
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder
import java.time.Clock
import java.time.Duration

/**
 * 액세스 토큰(JWT) 발급·검증. 서명 키 처리를 이곳 한 곳에 둔다. api 의 Resource Server 는 [JwtDecoder] 빈으로 검증한다.
 * 서명 키(JWT_SECRET)가 비어 있으면 dev·prod 는 기동을 멈추고, 그 밖(local)은 경고를 남기고 임시 키로 뜬다.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(JwtProperties::class)
class JwtConfiguration {
    @Bean
    fun jwtSigningKey(properties: JwtProperties, environment: Environment): JwtSigningKey {
        if (properties.secret.isNotBlank()) return JwtSigningKey.fromBase64(properties.secret)
        check(!environment.acceptsProfiles(Profiles.of("dev | prod"))) { "JWT 서명 키가 비어 있다. JWT_SECRET 을 넣는다" }
        log.warn("JWT 서명 키가 비어 있다. 임시 키로 뜨고, 재시작하면 발급한 토큰이 모두 무효가 된다(JWT_SECRET)")
        return JwtSigningKey.random()
    }

    @Bean
    fun jwtEncoder(key: JwtSigningKey): JwtEncoder = NimbusJwtEncoder(ImmutableSecret(key.secretKey))

    /** 발급한 서버가 검증하므로 시계 오차 여유를 두지 않는다. 만료 시각이 지나면 검증하지 않는다(만료 오류는 JwtTimestampValidator 가 낸다). */
    @Bean
    fun jwtDecoder(key: JwtSigningKey, clock: Clock): JwtDecoder =
        NimbusJwtDecoder.withSecretKey(key.secretKey).macAlgorithm(MacAlgorithm.HS256).build().apply {
            setJwtValidator(JwtTimestampValidator(Duration.ZERO).apply { setClock(clock) })
        }

    @Bean
    fun jwtAccessTokenIssuer(encoder: JwtEncoder, properties: JwtProperties): JwtAccessTokenIssuer =
        JwtAccessTokenIssuer(encoder, properties)

    companion object {
        private val log = LoggerFactory.getLogger(JwtConfiguration::class.java)
    }
}
