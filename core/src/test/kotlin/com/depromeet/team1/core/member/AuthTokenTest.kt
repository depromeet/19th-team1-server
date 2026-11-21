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
        assertActive(reissued.refreshToken)
    }

    @Test
    fun `재발급으로 폐기되고 유예 시간이 지난 토큰이 다시 오면 거절하고 그 회원의 리프레시 토큰을 모두 지운다`() {
        val login = login()
        authService.reissue(login.tokens.refreshToken)
        login("other-device")

        val error = assertFailsWith<AppException> {
            authServiceAt(NOW.plus(REUSE_GRACE).plusSeconds(1)).reissue(login.tokens.refreshToken)
        }

        assertEquals(AuthError.AUTH_INVALID_REFRESH_TOKEN, error.errorCode)
        assertTrue(refreshTokenRepository.findAll(login.memberId).isEmpty())
    }

    @Test
    fun `탈취 감지로 지울 때 다른 회원의 토큰은 건드리지 않는다`() {
        val login = login()
        authService.reissue(login.tokens.refreshToken)
        kakao.register("someone", OAuthUser(AuthProvider.KAKAO, "kakao-2", "다른 회원", null))
        val someone = authService.login(AuthProvider.KAKAO, "someone")

        assertFailsWith<AppException> {
            authServiceAt(NOW.plus(REUSE_GRACE).plusSeconds(1)).reissue(login.tokens.refreshToken)
        }

        assertActive(someone.tokens.refreshToken)
    }

    @Test
    fun `탈취 감지로 지워진 토큰이 다시 오면 거절만 하고 그 뒤 로그인한 토큰은 그대로 둔다`() {
        val login = login()
        val reissued = authService.reissue(login.tokens.refreshToken)
        val afterGrace = authServiceAt(NOW.plus(REUSE_GRACE).plusSeconds(1))
        assertFailsWith<AppException> { afterGrace.reissue(login.tokens.refreshToken) }
        kakao.register("relogin", OAuthUser(AuthProvider.KAKAO, "kakao-1", "나현", null))
        val relogin = afterGrace.login(AuthProvider.KAKAO, "relogin")

        val error = assertFailsWith<AppException> { afterGrace.reissue(reissued.refreshToken) }

        assertEquals(AuthError.AUTH_INVALID_REFRESH_TOKEN, error.errorCode)
        assertActive(relogin.tokens.refreshToken)
    }

    @Test
    fun `재발급으로 폐기된 지 유예 시간 안에 다시 온 토큰이면 새 토큰 쌍을 주고 다른 토큰은 그대로 둔다`() {
        val login = login()
        val reissued = authService.reissue(login.tokens.refreshToken)

        val again = authServiceAt(NOW.plus(REUSE_GRACE)).reissue(login.tokens.refreshToken)

        assertNotEquals(reissued.refreshToken, again.refreshToken)
        assertActive(again.refreshToken)
        assertActive(reissued.refreshToken)
    }

    @Test
    fun `유예 시간 안이어도 만료된 토큰은 거절한다`() {
        val login = login()
        val beforeExpiry = NOW.plus(RefreshToken.VALIDITY).minusSeconds(10)
        val reissued = authServiceAt(beforeExpiry).reissue(login.tokens.refreshToken)

        val error = assertFailsWith<AppException> {
            authServiceAt(beforeExpiry.plusSeconds(20)).reissue(login.tokens.refreshToken)
        }

        assertEquals(AuthError.AUTH_INVALID_REFRESH_TOKEN, error.errorCode)
        assertActive(reissued.refreshToken)
    }

    @Test
    fun `유예 시간은 설정값을 따른다`() {
        val login = login()
        authService.reissue(login.tokens.refreshToken)
        val tenSecondsLater = NOW.plusSeconds(10)

        assertFailsWith<AppException> {
            authServiceAt(tenSecondsLater, reuseGrace = Duration.ofSeconds(5)).reissue(login.tokens.refreshToken)
        }

        assertTrue(refreshTokenRepository.findAll(login.memberId).isEmpty())
    }

    @Test
    fun `같은 토큰으로 동시에 재발급해 진 쪽도 새 토큰 쌍을 받는다`() {
        val login = login()
        lateinit var winner: IssuedTokens
        refreshTokenRepository.beforeNextRevoke = { winner = authService.reissue(login.tokens.refreshToken) }

        val loser = authService.reissue(login.tokens.refreshToken)

        assertNotEquals(winner.refreshToken, loser.refreshToken)
        assertActive(winner.refreshToken)
        assertActive(loser.refreshToken)
    }

    @Test
    fun `탈퇴한 회원의 리프레시 토큰은 거절하고 그 회원의 토큰을 모두 지운다`() {
        val login = login()
        login("other-device")
        withdraw(login.memberId)

        val error = assertFailsWith<AppException> { authService.reissue(login.tokens.refreshToken) }

        assertEquals(AuthError.AUTH_INVALID_REFRESH_TOKEN, error.errorCode)
        assertTrue(refreshTokenRepository.findAll(login.memberId).isEmpty())
    }

    @Test
    fun `탈퇴한 회원이면 재발급에 쓰인 지 유예 시간 안의 토큰도 거절한다`() {
        val login = login()
        authService.reissue(login.tokens.refreshToken)
        withdraw(login.memberId)

        val error = assertFailsWith<AppException> {
            authServiceAt(NOW.plusSeconds(5)).reissue(login.tokens.refreshToken)
        }

        assertEquals(AuthError.AUTH_INVALID_REFRESH_TOKEN, error.errorCode)
        assertTrue(refreshTokenRepository.findAll(login.memberId).isEmpty())
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
    fun `로그아웃하면 그 리프레시 토큰을 지운다`() {
        val login = login()

        authService.logout(login.tokens.refreshToken)

        assertNull(refreshTokenRepository.findByTokenHash(sha256Hex(login.tokens.refreshToken)))
    }

    @Test
    fun `로그아웃한 토큰이 다시 오면 시간과 무관하게 거절만 하고 다른 기기의 토큰은 그대로 둔다`() {
        val login = login()
        val otherDevice = login("other-device")
        authService.logout(login.tokens.refreshToken)

        val right = assertFailsWith<AppException> { authService.reissue(login.tokens.refreshToken) }
        val afterGrace = assertFailsWith<AppException> {
            authServiceAt(NOW.plus(REUSE_GRACE).plusSeconds(1)).reissue(login.tokens.refreshToken)
        }

        assertEquals(AuthError.AUTH_INVALID_REFRESH_TOKEN, right.errorCode)
        assertEquals(AuthError.AUTH_INVALID_REFRESH_TOKEN, afterGrace.errorCode)
        assertActive(otherDevice.tokens.refreshToken)
    }

    @Test
    fun `로그아웃은 다른 기기의 리프레시 토큰을 지우지 않는다`() {
        val login = login()
        val otherDevice = login("other-device")

        authService.logout(login.tokens.refreshToken)

        assertActive(otherDevice.tokens.refreshToken)
    }

    @Test
    fun `모르는 토큰이나 이미 로그아웃한 토큰으로 로그아웃해도 실패하지 않는다`() {
        val login = login()
        authService.logout(login.tokens.refreshToken)

        authService.logout(login.tokens.refreshToken)
        authService.logout("not-issued-token")
    }

    private fun login(code: String = "code"): LoginResult {
        kakao.register(code, OAuthUser(AuthProvider.KAKAO, "kakao-1", "나현", null))
        return authService.login(AuthProvider.KAKAO, code)
    }

    private fun withdraw(memberId: Long) {
        val member = memberRepository.findById(memberId)!!
        memberRepository.save(member.copy(status = MemberStatus.WITHDRAWN, withdrawnAt = NOW, updatedAt = NOW))
    }

    /** 토큰 행이 남아 있고 폐기되지 않았다. */
    private fun assertActive(refreshToken: String) {
        val stored = assertNotNull(refreshTokenRepository.findByTokenHash(sha256Hex(refreshToken)), "토큰 행이 없다")
        assertNull(stored.revokedAt, "토큰이 폐기됐다")
    }

    private fun sha256Hex(value: String): String =
        MessageDigest.getInstance("SHA-256").digest(value.toByteArray()).joinToString("") { "%02x".format(it) }

    companion object {
        private val NOW = Instant.parse("2026-11-21T03:00:00Z")
        private val REUSE_GRACE = Duration.ofSeconds(30)
    }
}
