package com.depromeet.team1.api.web.member

import com.depromeet.team1.api.config.SecurityConfig
import com.depromeet.team1.api.web.common.AuthenticationErrorEntryPoint
import com.depromeet.team1.api.web.common.TestJwt
import com.depromeet.team1.core.common.error.AppException
import com.depromeet.team1.core.member.AuthError
import com.depromeet.team1.core.member.AuthProvider
import com.depromeet.team1.core.member.AuthService
import com.depromeet.team1.core.member.IssuedTokens
import com.depromeet.team1.core.member.LoginResult
import org.junit.jupiter.api.Test
import org.mockito.BDDMockito.given
import org.mockito.Mockito.verify
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.Instant

@WebMvcTest(controllers = [AuthController::class])
@Import(SecurityConfig::class, AuthenticationErrorEntryPoint::class, TestJwt.DecoderConfig::class)
class AuthControllerTest {
    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockitoBean
    private lateinit var authService: AuthService

    @Test
    fun `카카오 인가 코드로 로그인하면 토큰과 약관 동의 필요 여부를 준다`() {
        given(authService.login(AuthProvider.KAKAO, "kakao-code"))
            .willReturn(LoginResult(memberId = 42, newMember = true, termsAgreementRequired = true, tokens = TOKENS))

        mockMvc
            .perform(json(post("/api/v1/auth/kakao/login"), """{"code":"kakao-code"}"""))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.memberId").value(42))
            .andExpect(jsonPath("$.data.newMember").value(true))
            .andExpect(jsonPath("$.data.termsAgreementRequired").value(true))
            .andExpect(jsonPath("$.data.accessToken").value("access"))
            .andExpect(jsonPath("$.data.accessTokenExpiresAt").value("2026-11-21T03:30:00Z"))
            .andExpect(jsonPath("$.data.refreshToken").value("refresh"))
            .andExpect(jsonPath("$.data.refreshTokenExpiresAt").value("2026-12-05T03:00:00Z"))
    }

    @Test
    fun `인가 코드가 비어 있으면 400 으로 응답한다`() {
        mockMvc
            .perform(json(post("/api/v1/auth/kakao/login"), """{"code":" "}"""))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.code").value("INVALID_INPUT"))
    }

    @Test
    fun `카카오가 인가 코드를 거절하면 400 AUTH_INVALID_OAUTH_CODE 로 응답한다`() {
        given(authService.login(AuthProvider.KAKAO, "used-code"))
            .willThrow(AppException(AuthError.AUTH_INVALID_OAUTH_CODE, "test"))

        mockMvc
            .perform(json(post("/api/v1/auth/kakao/login"), """{"code":"used-code"}"""))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.code").value("AUTH_INVALID_OAUTH_CODE"))
    }

    @Test
    fun `리프레시 토큰으로 재발급하면 새 토큰 쌍을 준다`() {
        given(authService.reissue("old-refresh")).willReturn(TOKENS)

        mockMvc
            .perform(json(post("/api/v1/auth/reissue"), """{"refreshToken":"old-refresh"}"""))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.accessToken").value("access"))
            .andExpect(jsonPath("$.data.accessTokenExpiresAt").value("2026-11-21T03:30:00Z"))
            .andExpect(jsonPath("$.data.refreshToken").value("refresh"))
            .andExpect(jsonPath("$.data.refreshTokenExpiresAt").value("2026-12-05T03:00:00Z"))
    }

    @Test
    fun `쓸 수 없는 리프레시 토큰이면 401 AUTH_INVALID_REFRESH_TOKEN 으로 응답한다`() {
        given(authService.reissue("revoked")).willThrow(AppException(AuthError.AUTH_INVALID_REFRESH_TOKEN, "test"))

        mockMvc
            .perform(json(post("/api/v1/auth/reissue"), """{"refreshToken":"revoked"}"""))
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.code").value("AUTH_INVALID_REFRESH_TOKEN"))
    }

    @Test
    fun `로그아웃하면 리프레시 토큰을 폐기하고 204 로 응답한다`() {
        mockMvc
            .perform(json(post("/api/v1/auth/logout"), """{"refreshToken":"refresh"}"""))
            .andExpect(status().isNoContent)

        verify(authService).logout("refresh")
    }

    private fun json(
        request: org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder,
        body: String,
    ) = request.contentType(MediaType.APPLICATION_JSON).content(body)

    companion object {
        private val TOKENS = IssuedTokens(
            accessToken = "access",
            accessTokenExpiresAt = Instant.parse("2026-11-21T03:30:00Z"),
            refreshToken = "refresh",
            refreshTokenExpiresAt = Instant.parse("2026-12-05T03:00:00Z"),
        )
    }
}
