package com.depromeet.team1.api.web.common

import com.nimbusds.jose.jwk.source.ImmutableSecret
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.security.oauth2.jose.jws.MacAlgorithm
import org.springframework.security.oauth2.jwt.JwsHeader
import org.springframework.security.oauth2.jwt.JwtClaimsSet
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.oauth2.jwt.JwtEncoderParameters
import org.springframework.security.oauth2.jwt.JwtTimestampValidator
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import javax.crypto.SecretKey
import javax.crypto.spec.SecretKeySpec

/** 컨트롤러·보안 테스트용 액세스 토큰. infra 의 JwtConfiguration 과 같은 방식(HS256, 시계 오차 0)으로 만들고 검증한다. */
object TestJwt {
    val NOW: Instant = Instant.parse("2026-11-21T03:00:00Z")
    val KEY: SecretKey = SecretKeySpec(ByteArray(32) { it.toByte() }, "HmacSHA256")

    fun token(memberId: Long, issuedAt: Instant = NOW, key: SecretKey = KEY): String {
        val claims = JwtClaimsSet.builder()
            .subject(memberId.toString())
            .issuedAt(issuedAt)
            .expiresAt(issuedAt.plus(Duration.ofMinutes(30)))
            .build()
        return NimbusJwtEncoder(ImmutableSecret(key))
            .encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
            .tokenValue
    }

    /** [KEY] 로 검증하고 시각을 [NOW] 로 고정한 디코더. */
    @TestConfiguration
    class DecoderConfig {
        @Bean
        fun jwtDecoder(): JwtDecoder =
            NimbusJwtDecoder.withSecretKey(KEY).macAlgorithm(MacAlgorithm.HS256).build().apply {
                setJwtValidator(
                    JwtTimestampValidator(Duration.ZERO).apply {
                        setClock(Clock.fixed(NOW, ZoneOffset.UTC))
                    },
                )
            }
    }
}
