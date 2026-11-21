package com.depromeet.team1.core.member

import com.depromeet.team1.core.common.error.AppException
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class AuthServiceTest {
    private val memberRepository = FakeMemberRepository()
    private val kakao = FakeOAuthClient()
    private val authService = AuthService(
        listOf(kakao),
        memberRepository,
        FakeRefreshTokenRepository(),
        FakeAccessTokenIssuer(),
        AuthSettings(refreshTokenReuseGrace = Duration.ofSeconds(30)),
        Clock.fixed(NOW, ZoneOffset.UTC),
    )

    @Test
    fun `처음 보는 카카오 계정이면 회원과 로그인 수단을 만든다`() {
        kakao.register("code", OAuthUser(AuthProvider.KAKAO, "kakao-1", "나현", "user@example.com"))

        val result = authService.login(AuthProvider.KAKAO, "code")

        assertTrue(result.newMember)
        val member = memberRepository.findById(result.memberId)!!
        assertEquals("나현", member.nickname)
        assertEquals("user@example.com", member.email)
        assertEquals(NOW, member.createdAt)
        assertEquals(result.memberId, memberRepository.findActiveCredential(AuthProvider.KAKAO, "kakao-1")?.memberId)
    }

    @Test
    fun `이미 가입한 카카오 계정이면 같은 회원으로 로그인한다`() {
        kakao.register("first", OAuthUser(AuthProvider.KAKAO, "kakao-1", "나현", null))
        kakao.register("second", OAuthUser(AuthProvider.KAKAO, "kakao-1", "바뀐 닉네임", null))
        val first = authService.login(AuthProvider.KAKAO, "first")

        val second = authService.login(AuthProvider.KAKAO, "second")

        assertFalse(second.newMember)
        assertEquals(first.memberId, second.memberId)
        assertEquals(1, memberRepository.findCredentials(first.memberId).size)
    }

    @Test
    fun `연결을 끊은 카카오 계정으로 로그인하면 새 회원으로 가입한다`() {
        val oldMember = memberRepository.save(Member.create("예전 회원", null, NOW))
        memberRepository.saveCredential(
            MemberCredential.create(oldMember.id!!, AuthProvider.KAKAO, "kakao-1", NOW).copy(revokedAt = NOW),
        )
        kakao.register("code", OAuthUser(AuthProvider.KAKAO, "kakao-1", "나현", null))

        val result = authService.login(AuthProvider.KAKAO, "code")

        assertTrue(result.newMember)
        assertNotEquals(oldMember.id, result.memberId)
    }

    @Test
    fun `카카오 닉네임이 없으면 기본 닉네임으로 가입한다`() {
        kakao.register("code", OAuthUser(AuthProvider.KAKAO, "kakao-1", null, null))

        val result = authService.login(AuthProvider.KAKAO, "code")

        assertEquals("회원", memberRepository.findById(result.memberId)!!.nickname)
    }

    @Test
    fun `카카오 닉네임이 30자를 넘으면 30자까지만 쓴다`() {
        kakao.register("code", OAuthUser(AuthProvider.KAKAO, "kakao-1", "가".repeat(40), null))

        val result = authService.login(AuthProvider.KAKAO, "code")

        assertEquals("가".repeat(30), memberRepository.findById(result.memberId)!!.nickname)
    }

    @Test
    fun `카카오가 인가 코드를 거절하면 회원을 만들지 않는다`() {
        val error = assertFailsWith<AppException> { authService.login(AuthProvider.KAKAO, "unknown") }

        assertEquals(AuthError.AUTH_INVALID_OAUTH_CODE, error.errorCode)
        assertEquals(null, memberRepository.findActiveCredential(AuthProvider.KAKAO, "kakao-1"))
    }

    @Test
    fun `필수 약관에 동의하지 않은 회원은 로그인 결과에 약관 동의가 필요하다고 알린다`() {
        kakao.register("code", OAuthUser(AuthProvider.KAKAO, "kakao-1", "나현", null))

        val result = authService.login(AuthProvider.KAKAO, "code")

        assertTrue(result.termsAgreementRequired)
    }

    @Test
    fun `약관에 동의하면 종류와 현재 버전과 동의 시각을 기록한다`() {
        val memberId = signedUpMemberId()

        authService.agreeToTerms(memberId, setOf(TermsType.SERVICE, TermsType.PRIVACY))

        assertEquals(
            setOf(
                Triple(TermsType.SERVICE, TermsType.SERVICE.currentVersion, NOW),
                Triple(TermsType.PRIVACY, TermsType.PRIVACY.currentVersion, NOW),
            ),
            memberRepository.findTermsAgreements(memberId).map {
                Triple(it.termsType, it.termsVersion, it.agreedAt)
            }.toSet(),
        )
    }

    @Test
    fun `필수 약관에 모두 동의한 회원은 다음 로그인에서 약관 동의가 필요 없다`() {
        kakao.register("first", OAuthUser(AuthProvider.KAKAO, "kakao-1", "나현", null))
        kakao.register("second", OAuthUser(AuthProvider.KAKAO, "kakao-1", "나현", null))
        val memberId = authService.login(AuthProvider.KAKAO, "first").memberId
        authService.agreeToTerms(memberId, setOf(TermsType.SERVICE, TermsType.PRIVACY))

        val result = authService.login(AuthProvider.KAKAO, "second")

        assertFalse(result.termsAgreementRequired)
    }

    @Test
    fun `동의하지 않은 필수 약관이 빠져 있으면 거절하고 아무것도 기록하지 않는다`() {
        val memberId = signedUpMemberId()

        val error = assertFailsWith<AppException> { authService.agreeToTerms(memberId, setOf(TermsType.SERVICE)) }

        assertEquals(AuthError.AUTH_REQUIRED_TERMS_NOT_AGREED, error.errorCode)
        assertTrue(memberRepository.findTermsAgreements(memberId).isEmpty())
    }

    @Test
    fun `이미 동의한 약관에 다시 동의해도 한 번만 기록한다`() {
        val memberId = signedUpMemberId()
        authService.agreeToTerms(memberId, setOf(TermsType.SERVICE, TermsType.PRIVACY))

        authService.agreeToTerms(memberId, setOf(TermsType.SERVICE, TermsType.PRIVACY))

        assertEquals(2, memberRepository.findTermsAgreements(memberId).size)
    }

    private fun signedUpMemberId(): Long {
        kakao.register("signup", OAuthUser(AuthProvider.KAKAO, "kakao-signup", "나현", null))
        return authService.login(AuthProvider.KAKAO, "signup").memberId
    }

    companion object {
        private val NOW = Instant.parse("2026-11-21T03:00:00Z")
    }
}
