package com.depromeet.team1.core.member

import com.depromeet.team1.core.common.error.AppException
import org.junit.jupiter.api.Test
import java.security.MessageDigest
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** [AuthService] 의 토큰 발급·재발급·로그아웃. */
class AuthTokenTest {
    private val memberRepository = FakeMemberRepository()
    private val refreshTokenRepository = FakeRefreshTokenRepository()
    private val kakao = FakeOAuthClient()

    private fun authServiceAt(now: Instant, reuseGrace: Duration = REUSE_GRACE) = AuthService(
        listOf(kakao),
        memberRepository,
        refreshTokenRepository,
        FakeAccessTokenIssuer(),
        AuthSettings(refreshTokenReuseGrace = reuseGrace),
        Clock.fixed(now, ZoneOffset.UTC),
    )

    private val authService = authServiceAt(NOW)

    @Test
    fun `로그인하면 액세스 토큰과 14일 뒤 만료되는 리프레시 토큰을 준다`() {
        val result = login()

        assertEquals("access:${result.memberId}:${NOW.epochSecond}", result.tokens.accessToken)
        assertEquals(NOW.plus(Duration.ofMinutes(30)), result.tokens.accessTokenExpiresAt)
        assertEquals(NOW.plus(Duration.ofDays(14)), result.tokens.refreshTokenExpiresAt)
    }

    @Test
    fun `리프레시 토큰은 원문 대신 SHA-256 해시로 저장한다`() {
        val result = login()

        val stored = refreshTokenRepository.findAll(result.memberId).single()
        assertEquals(sha256Hex(result.tokens.refreshToken), stored.tokenHash)
        assertNotEquals(result.tokens.refreshToken, stored.tokenHash)
        assertEquals(NOW.plus(Duration.ofDays(14)), stored.expiresAt)
    }

    @Test
    fun `로그인할 때마다 다른 리프레시 토큰을 준다`() {
        val first = login("first")
        val second = login("second")

        assertNotEquals(first.tokens.refreshToken, second.tokens.refreshToken)
    }

    @Test
    fun `리프레시 토큰으로 재발급하면 새 토큰 쌍을 주고 쓴 토큰은 폐기한다`() {
        val login = login()
        val later = NOW.plus(Duration.ofHours(1))

        val reissued = authServiceAt(later).reissue(login.tokens.refreshToken)

        assertEquals("access:${login.memberId}:${later.epochSecond}", reissued.accessToken)
        assertEquals(later.plus(Duration.ofDays(14)), reissued.refreshTokenExpiresAt)
        assertNotEquals(login.tokens.refreshToken, reissued.refreshToken)
        assertEquals(later, refreshTokenRepository.findByTokenHash(sha256Hex(login.tokens.refreshToken))?.revokedAt)
        assertNull(refreshTokenRepository.findByTokenHash(sha256Hex(reissued.refreshToken))?.revokedAt)
    }

    @Test
    fun `폐기되고 유예 시간이 지난 리프레시 토큰이 다시 오면 거절하고 그 회원의 살아 있는 리프레시 토큰을 모두 폐기한다`() {
        val login = login()
        val reissued = authService.reissue(login.tokens.refreshToken)
        val otherDevice = login("other-device")

        val error = assertFailsWith<AppException> {
            authServiceAt(NOW.plus(REUSE_GRACE).plusSeconds(1)).reissue(login.tokens.refreshToken)
        }

        assertEquals(AuthError.AUTH_INVALID_REFRESH_TOKEN, error.errorCode)
        assertNotNull(refreshTokenRepository.findByTokenHash(sha256Hex(reissued.refreshToken))?.revokedAt)
        assertNotNull(refreshTokenRepository.findByTokenHash(sha256Hex(otherDevice.tokens.refreshToken))?.revokedAt)
        assertTrue(refreshTokenRepository.findAll(login.memberId).all { it.revokedAt != null })
    }

    @Test
    fun `탈취 감지로 폐기할 때 다른 회원의 토큰은 건드리지 않는다`() {
        val login = login()
        authService.reissue(login.tokens.refreshToken)
        kakao.register("someone", OAuthUser(AuthProvider.KAKAO, "kakao-2", "다른 회원", null))
        val someone = authService.login(AuthProvider.KAKAO, "someone")
        val afterGrace = authServiceAt(NOW.plus(REUSE_GRACE).plusSeconds(1))

        assertFailsWith<AppException> { afterGrace.reissue(login.tokens.refreshToken) }

        assertTrue(refreshTokenRepository.findAll(login.memberId).all { it.revokedAt != null })
        assertNull(refreshTokenRepository.findByTokenHash(sha256Hex(someone.tokens.refreshToken))?.revokedAt)
    }

    @Test
    fun `폐기된 지 유예 시간 안에 다시 온 리프레시 토큰은 거절만 하고 다른 토큰은 폐기하지 않는다`() {
        val login = login()
        val reissued = authService.reissue(login.tokens.refreshToken)

        val error = assertFailsWith<AppException> {
            authServiceAt(NOW.plus(REUSE_GRACE)).reissue(login.tokens.refreshToken)
        }

        assertEquals(AuthError.AUTH_INVALID_REFRESH_TOKEN, error.errorCode)
        assertNull(refreshTokenRepository.findByTokenHash(sha256Hex(reissued.refreshToken))?.revokedAt)
    }

    @Test
    fun `유예 시간은 설정값을 따른다`() {
        val login = login()
        val reissued = authService.reissue(login.tokens.refreshToken)
        val tenSecondsLater = NOW.plusSeconds(10)

        assertFailsWith<AppException> {
            authServiceAt(tenSecondsLater, reuseGrace = Duration.ofSeconds(5)).reissue(login.tokens.refreshToken)
        }

        assertNotNull(refreshTokenRepository.findByTokenHash(sha256Hex(reissued.refreshToken))?.revokedAt)
    }

    @Test
    fun `같은 토큰으로 동시에 재발급해 진 쪽은 거절만 하고 이긴 쪽의 새 토큰은 폐기하지 않는다`() {
        val login = login()
        lateinit var winner: IssuedTokens
        refreshTokenRepository.beforeNextRevoke = { winner = authService.reissue(login.tokens.refreshToken) }

        val error = assertFailsWith<AppException> { authService.reissue(login.tokens.refreshToken) }

        assertEquals(AuthError.AUTH_INVALID_REFRESH_TOKEN, error.errorCode)
        assertNull(refreshTokenRepository.findByTokenHash(sha256Hex(winner.refreshToken))?.revokedAt)
    }

    @Test
    fun `로그아웃하고 유예 시간이 지나 그 토큰이 다시 오면 탈취로 보고 모두 폐기한다`() {
        val login = login()
        val otherDevice = login("other-device")
        authService.logout(login.tokens.refreshToken)

        assertFailsWith<AppException> {
            authServiceAt(NOW.plus(REUSE_GRACE).plusSeconds(1)).reissue(login.tokens.refreshToken)
        }

        assertNotNull(refreshTokenRepository.findByTokenHash(sha256Hex(otherDevice.tokens.refreshToken))?.revokedAt)
    }

    @Test
    fun `만료된 리프레시 토큰은 거절한다`() {
        val login = login()
        val expired = NOW.plus(Duration.ofDays(14))

        val error = assertFailsWith<AppException> { authServiceAt(expired).reissue(login.tokens.refreshToken) }

        assertEquals(AuthError.AUTH_INVALID_REFRESH_TOKEN, error.errorCode)
    }

    @Test
    fun `발급하지 않은 리프레시 토큰은 거절한다`() {
        login()

        val error = assertFailsWith<AppException> { authService.reissue("not-issued-token") }

        assertEquals(AuthError.AUTH_INVALID_REFRESH_TOKEN, error.errorCode)
    }

    @Test
    fun `로그아웃하면 그 리프레시 토큰으로 재발급할 수 없다`() {
        val login = login()

        authService.logout(login.tokens.refreshToken)

        val error = assertFailsWith<AppException> { authService.reissue(login.tokens.refreshToken) }
        assertEquals(AuthError.AUTH_INVALID_REFRESH_TOKEN, error.errorCode)
    }

    @Test
    fun `로그아웃은 다른 기기의 리프레시 토큰을 폐기하지 않는다`() {
        val login = login()
        val otherDevice = login("other-device")

        authService.logout(login.tokens.refreshToken)

        assertNull(refreshTokenRepository.findByTokenHash(sha256Hex(otherDevice.tokens.refreshToken))?.revokedAt)
    }

    @Test
    fun `모르는 토큰이나 이미 폐기된 토큰으로 로그아웃해도 실패하지 않는다`() {
        val login = login()
        authService.logout(login.tokens.refreshToken)

        authService.logout(login.tokens.refreshToken)
        authService.logout("not-issued-token")
    }

    private fun login(code: String = "code"): LoginResult {
        kakao.register(code, OAuthUser(AuthProvider.KAKAO, "kakao-1", "나현", null))
        return authService.login(AuthProvider.KAKAO, code)
    }

    private fun sha256Hex(value: String): String =
        MessageDigest.getInstance("SHA-256").digest(value.toByteArray()).joinToString("") { "%02x".format(it) }

    companion object {
        private val NOW = Instant.parse("2026-11-21T03:00:00Z")
        private val REUSE_GRACE = Duration.ofSeconds(30)
    }
}
