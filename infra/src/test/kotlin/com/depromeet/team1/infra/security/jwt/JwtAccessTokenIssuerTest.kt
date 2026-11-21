package com.depromeet.team1.infra.security.jwt

import org.junit.jupiter.api.Test
import org.springframework.security.oauth2.jwt.BadJwtException
import org.springframework.security.oauth2.jwt.JwtValidationException
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import java.util.Base64
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class JwtAccessTokenIssuerTest {
    private val configuration = JwtConfiguration()
    private val key = JwtSigningKey.fromBase64(SECRET)
    private val issuer = JwtAccessTokenIssuer(configuration.jwtEncoder(key), JwtProperties(secret = SECRET))

    @Test
    fun `발급한 토큰은 같은 키로 검증되고 회원 번호와 30분 뒤 만료 시각을 담는다`() {
        val token = issuer.issue(42, NOW)

        val jwt = configuration.jwtDecoder(key, clockAt(NOW.plusSeconds(60))).decode(token.value)

        assertEquals("42", jwt.subject)
        assertEquals(NOW, jwt.issuedAt)
        assertEquals(NOW.plus(Duration.ofMinutes(30)), jwt.expiresAt)
        assertEquals(NOW.plus(Duration.ofMinutes(30)), token.expiresAt)
    }

    @Test
    fun `다른 키로 서명한 토큰은 검증하지 않는다`() {
        val otherKey = JwtSigningKey.fromBase64(Base64.getEncoder().encodeToString(ByteArray(32) { 7 }))
        val forged = JwtAccessTokenIssuer(
            configuration.jwtEncoder(otherKey),
            JwtProperties(secret = SECRET),
        ).issue(42, NOW)

        assertFailsWith<BadJwtException> {
            configuration.jwtDecoder(key, clockAt(NOW.plusSeconds(60))).decode(forged.value)
        }
    }

    @Test
    fun `만료 시각이 지난 토큰은 검증하지 않는다`() {
        val token = issuer.issue(42, NOW)

        assertFailsWith<JwtValidationException> {
            configuration.jwtDecoder(key, clockAt(NOW.plus(Duration.ofMinutes(30)).plusSeconds(1))).decode(token.value)
        }
    }

    @Test
    fun `만료 시각까지는 검증한다`() {
        val token = issuer.issue(42, NOW)

        val jwt = configuration.jwtDecoder(key, clockAt(NOW.plus(Duration.ofMinutes(30)))).decode(token.value)

        assertEquals("42", jwt.subject)
    }

    private fun clockAt(instant: Instant) = Clock.fixed(instant, ZoneOffset.UTC)

    companion object {
        private val NOW = Instant.parse("2026-11-21T03:00:00Z")
        private val SECRET = Base64.getEncoder().encodeToString(ByteArray(32) { it.toByte() })
    }
}
