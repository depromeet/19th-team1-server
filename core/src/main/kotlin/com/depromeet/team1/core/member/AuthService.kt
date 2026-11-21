package com.depromeet.team1.core.member

import com.depromeet.team1.core.common.error.AppException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Clock
import java.time.Instant
import java.util.Base64

/** 소셜 로그인과 가입, 최초 로그인 약관 동의, 토큰 발급·재발급·로그아웃. */
@Service
class AuthService(
    oauthClients: List<OAuthClient>,
    private val memberRepository: MemberRepository,
    private val refreshTokenRepository: RefreshTokenRepository,
    private val accessTokenIssuer: AccessTokenIssuer,
    private val settings: AuthSettings,
    private val clock: Clock,
) {
    private val oauthClients: Map<AuthProvider, OAuthClient> = oauthClients.associateBy { it.provider }

    /**
     * 제공자 인가 코드로 로그인한다. 처음 보는 제공자 계정이면 가입시킨다.
     * 제공자 호출이 있어 트랜잭션을 걸지 않는다. 가입은 저장소 메서드 하나([MemberRepository.signUp])로 커밋한다.
     */
    fun login(provider: AuthProvider, authorizationCode: String): LoginResult {
        val client = checkNotNull(oauthClients[provider]) { "로그인 제공자 구현이 없다: $provider" }
        val user = client.fetchUser(authorizationCode)

        val credential = memberRepository.findActiveCredential(user.provider, user.providerUserId)
        val memberId = credential?.memberId ?: signUp(user)
        return LoginResult(
            memberId = memberId,
            newMember = credential == null,
            termsAgreementRequired = missingRequiredTerms(memberId).isNotEmpty(),
            tokens = issueTokens(memberId, clock.instant()),
        )
    }

    /** 약관에 동의한다. 아직 동의하지 않은 필수 약관이 [termsTypes] 에 모두 있어야 한다. 이미 동의한 약관은 다시 기록하지 않는다. */
    @Transactional
    fun agreeToTerms(memberId: Long, termsTypes: Set<TermsType>) {
        val missing = missingRequiredTerms(memberId) - termsTypes
        if (missing.isNotEmpty()) {
            throw AppException(AuthError.AUTH_REQUIRED_TERMS_NOT_AGREED, "memberId=$memberId missing=$missing")
        }

        val agreedAt = clock.instant()
        val agreements = (termsTypes - agreedCurrentTerms(memberId)).map {
            MemberTermsAgreement(null, memberId, it, it.currentVersion, agreedAt)
        }
        if (agreements.isNotEmpty()) memberRepository.saveTermsAgreements(agreements)
    }

    /**
     * 리프레시 토큰으로 새 토큰 쌍을 발급하고 쓴 토큰은 폐기한다(로테이션, revoked_at 기록).
     * 로테이션으로 폐기된 토큰이 다시 오면, 폐기된 지 유예 시간([AuthSettings.refreshTokenReuseGrace]) 안이면
     * 동시 재발급으로 보고 새 토큰 쌍을 주고, 지났으면 탈취로 보고 그 회원의 리프레시 토큰을 모두 지운 뒤 거절한다.
     * 로그아웃·탈취 감지로 지워진 토큰은 모르는 토큰과 같게 거절한다. 탈퇴한 회원이면 그 회원의 토큰을 모두 지우고 거절한다.
     *
     * 트랜잭션을 걸지 않는다. 탈취 감지의 일괄 삭제는 거절 예외와 함께 롤백되면 안 되므로 저장소 메서드가 각각 커밋한다.
     * 폐기 뒤 새 토큰 저장이 실패하면 사용자는 다시 로그인한다.
     */
    fun reissue(refreshToken: String): IssuedTokens {
        val now = clock.instant()
        val stored = refreshTokenRepository.findByTokenHash(hash(refreshToken)) ?: throw unknownRefreshToken()
        rejectIfWithdrawn(stored.memberId)
        if (stored.revokedAt != null) return reissueRotated(stored, now)
        rejectIfExpired(stored, now)
        // 같은 토큰으로 동시에 재발급하면 조건부 폐기가 하나만 통과시킨다. 진 쪽은 다시 읽어 로테이션된 토큰의 규칙을 적용한다.
        if (!refreshTokenRepository.revoke(stored.id!!, now)) {
            val current = refreshTokenRepository.findByTokenHash(stored.tokenHash) ?: throw unknownRefreshToken()
            return reissueRotated(current, now)
        }
        return issueTokens(stored.memberId, now)
    }

    /** 리프레시 토큰 행을 지운다. 모르는 토큰이어도 실패하지 않는다. 다른 기기의 토큰은 그대로 둔다. */
    fun logout(refreshToken: String) {
        val stored = refreshTokenRepository.findByTokenHash(hash(refreshToken)) ?: return
        refreshTokenRepository.delete(stored.id!!)
    }

    /** 로테이션으로 이미 폐기된 토큰이 다시 왔다. */
    private fun reissueRotated(stored: RefreshToken, now: Instant): IssuedTokens {
        val revokedAt = checkNotNull(stored.revokedAt)
        if (now.isAfter(revokedAt.plus(settings.refreshTokenReuseGrace))) {
            val deleted = refreshTokenRepository.deleteAllByMemberId(stored.memberId)
            throw AppException(
                AuthError.AUTH_INVALID_REFRESH_TOKEN,
                "로테이션된 리프레시 토큰 재사용(탈취 감지) id=${stored.id} memberId=${stored.memberId}, 토큰 ${deleted}개 삭제",
            )
        }
        rejectIfExpired(stored, now)
        return issueTokens(stored.memberId, now)
    }

    /** 탈퇴 처리에서 토큰을 지우지 못했더라도 탈퇴한 회원에게 토큰을 주지 않는다. */
    private fun rejectIfWithdrawn(memberId: Long) {
        if (memberRepository.findById(memberId)?.status == MemberStatus.ACTIVE) return
        val deleted = refreshTokenRepository.deleteAllByMemberId(memberId)
        throw AppException(
            AuthError.AUTH_INVALID_REFRESH_TOKEN,
            "탈퇴했거나 없는 회원의 리프레시 토큰 memberId=$memberId, 토큰 ${deleted}개 삭제",
        )
    }

    private fun rejectIfExpired(stored: RefreshToken, now: Instant) {
        if (!now.isBefore(stored.expiresAt)) {
            throw AppException(AuthError.AUTH_INVALID_REFRESH_TOKEN, "만료된 리프레시 토큰 id=${stored.id}")
        }
    }

    private fun unknownRefreshToken() = AppException(AuthError.AUTH_INVALID_REFRESH_TOKEN, "모르는 리프레시 토큰(발급하지 않았거나 지워짐)")

    private fun issueTokens(memberId: Long, now: Instant): IssuedTokens {
        val accessToken = accessTokenIssuer.issue(memberId, now)
        val refreshToken = newRefreshToken()
        val stored = refreshTokenRepository.save(
            RefreshToken(null, memberId, hash(refreshToken), now.plus(RefreshToken.VALIDITY), now, null),
        )
        return IssuedTokens(accessToken.value, accessToken.expiresAt, refreshToken, stored.expiresAt)
    }

    private fun signUp(user: OAuthUser): Long {
        val member = Member.create(nicknameOf(user), user.email, clock.instant())
        return memberRepository.signUp(member, user.provider, user.providerUserId).id!!
    }

    private fun missingRequiredTerms(memberId: Long): Set<TermsType> =
        TermsType.entries.filter { it.required }.toSet() - agreedCurrentTerms(memberId)

    /** 지금 받는 버전에 동의한 약관. 버전이 바뀌면 예전 버전 동의는 세지 않는다. */
    private fun agreedCurrentTerms(memberId: Long): Set<TermsType> = memberRepository.findTermsAgreements(memberId)
        .filter { it.termsVersion == it.termsType.currentVersion }
        .map { it.termsType }
        .toSet()

    /** 제공자가 닉네임을 주지 않으면(동의 안 함) 기본 닉네임을 쓰고, 길면 자른다. */
    private fun nicknameOf(user: OAuthUser): String {
        val nickname = user.nickname?.trim()
        return if (nickname.isNullOrEmpty()) DEFAULT_NICKNAME else nickname.take(Member.NICKNAME_MAX)
    }

    /** 추측할 수 없는 무작위 256비트. 서버가 DB 에서 찾아 확인하므로 서명하지 않는다. */
    private fun newRefreshToken(): String {
        val bytes = ByteArray(REFRESH_TOKEN_BYTES).also(secureRandom::nextBytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    /** 무작위 토큰이라 느린 해시(bcrypt 등)가 필요 없다. 같은 원문은 같은 해시가 나와 해시로 찾을 수 있다. */
    private fun hash(refreshToken: String): String =
        MessageDigest.getInstance("SHA-256").digest(refreshToken.toByteArray()).joinToString("") { "%02x".format(it) }

    companion object {
        const val DEFAULT_NICKNAME = "회원"
        private const val REFRESH_TOKEN_BYTES = 32
        private val secureRandom = SecureRandom()
    }
}
