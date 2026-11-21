package com.depromeet.team1.api.web.member

import com.depromeet.team1.core.member.IssuedTokens
import com.depromeet.team1.core.member.LoginResult
import com.depromeet.team1.core.member.TermsType
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotEmpty
import java.time.Instant

data class KakaoLoginRequest(
    @Schema(description = "카카오가 리다이렉트 URI 로 넘겨준 인가 코드")
    @NotBlank
    val code: String,
)

/**
 * 리프레시 토큰을 받는 요청. 토큰은 응답 본문으로 주고 요청 본문으로 받는다.
 * 웹이 httpOnly 쿠키를 원하면 이 요청과 [TokenResponse]·[LoginResponse] 의 리프레시 필드만 쿠키로 바꾼다.
 */
data class RefreshTokenRequest(
    @NotBlank
    val refreshToken: String,
)

data class LoginResponse(
    val memberId: Long,
    @Schema(description = "이번 로그인으로 가입했는지")
    val newMember: Boolean,
    @Schema(description = "참이면 약관 동의 화면을 띄우고 POST /api/v1/members/me/terms-agreements 를 부른다")
    val termsAgreementRequired: Boolean,
    val accessToken: String,
    val accessTokenExpiresAt: Instant,
    val refreshToken: String,
    val refreshTokenExpiresAt: Instant,
) {
    companion object {
        fun from(result: LoginResult) = LoginResponse(
            memberId = result.memberId,
            newMember = result.newMember,
            termsAgreementRequired = result.termsAgreementRequired,
            accessToken = result.tokens.accessToken,
            accessTokenExpiresAt = result.tokens.accessTokenExpiresAt,
            refreshToken = result.tokens.refreshToken,
            refreshTokenExpiresAt = result.tokens.refreshTokenExpiresAt,
        )
    }
}

data class TokenResponse(
    val accessToken: String,
    val accessTokenExpiresAt: Instant,
    val refreshToken: String,
    val refreshTokenExpiresAt: Instant,
) {
    companion object {
        fun from(tokens: IssuedTokens) = TokenResponse(
            accessToken = tokens.accessToken,
            accessTokenExpiresAt = tokens.accessTokenExpiresAt,
            refreshToken = tokens.refreshToken,
            refreshTokenExpiresAt = tokens.refreshTokenExpiresAt,
        )
    }
}

data class TermsAgreementRequest(
    @Schema(description = "동의한 약관 종류. 아직 동의하지 않은 필수 약관이 모두 있어야 한다")
    @NotEmpty
    val termsTypes: Set<TermsType>,
)
