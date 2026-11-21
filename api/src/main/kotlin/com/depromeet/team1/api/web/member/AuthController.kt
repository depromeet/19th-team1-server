package com.depromeet.team1.api.web.member

import com.depromeet.team1.api.web.common.ApiResponse
import com.depromeet.team1.core.member.AuthProvider
import com.depromeet.team1.core.member.AuthService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

/** 로그인·재발급·로그아웃. 토큰 없이 부른다(SecurityConfig). */
@Tag(name = "인증")
@RestController
@RequestMapping("/api/v1/auth")
class AuthController(private val authService: AuthService) {
    @Operation(
        summary = "카카오 로그인",
        description = "카카오 인가 코드로 로그인한다. 처음 보는 카카오 계정이면 가입시킨다. " +
            "termsAgreementRequired 가 참이면 약관 동의를 받는다. 인가 코드를 쓸 수 없으면 400 AUTH_INVALID_OAUTH_CODE.",
    )
    @PostMapping("/kakao/login")
    fun kakaoLogin(@Valid @RequestBody request: KakaoLoginRequest): ApiResponse<LoginResponse> =
        ApiResponse(LoginResponse.from(authService.login(AuthProvider.KAKAO, request.code)))

    @Operation(
        summary = "토큰 재발급",
        description = "리프레시 토큰으로 새 액세스·리프레시 토큰을 받는다. 쓴 리프레시 토큰은 폐기된다. " +
            "쓸 수 없는 토큰(만료·로그아웃·탈취 감지로 지워짐·발급한 적 없음)이면 401 AUTH_INVALID_REFRESH_TOKEN 이고 다시 로그인한다. " +
            "재발급에 이미 쓴 토큰이 30초(설정값) 안에 다시 오면 새 토큰 쌍을 주고, 지나서 오면 탈취로 보고 그 회원의 리프레시 토큰을 모두 지운다.",
    )
    @PostMapping("/reissue")
    fun reissue(@Valid @RequestBody request: RefreshTokenRequest): ApiResponse<TokenResponse> =
        ApiResponse(TokenResponse.from(authService.reissue(request.refreshToken)))

    @Operation(summary = "로그아웃", description = "리프레시 토큰을 지운다. 모르는 토큰이어도 204 다.")
    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun logout(@Valid @RequestBody request: RefreshTokenRequest) {
        authService.logout(request.refreshToken)
    }
}
