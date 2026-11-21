package com.depromeet.team1.api.web.member

import com.depromeet.team1.api.config.SecurityConfig
import com.depromeet.team1.api.web.common.AuthenticationErrorEntryPoint
import com.depromeet.team1.api.web.common.TestJwt
import com.depromeet.team1.core.common.error.AppException
import com.depromeet.team1.core.member.AuthError
import com.depromeet.team1.core.member.AuthService
import com.depromeet.team1.core.member.TermsType
import org.junit.jupiter.api.Test
import org.mockito.BDDMockito.willThrow
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoInteractions
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@WebMvcTest(controllers = [MemberController::class])
@Import(SecurityConfig::class, AuthenticationErrorEntryPoint::class, TestJwt.DecoderConfig::class)
class MemberControllerTest {
    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockitoBean
    private lateinit var authService: AuthService

    @Test
    fun `약관 동의는 현재 사용자의 동의로 기록하고 204 로 응답한다`() {
        mockMvc
            .perform(agree(TestJwt.token(42), """{"termsTypes":["SERVICE","PRIVACY"]}"""))
            .andExpect(status().isNoContent)

        verify(authService).agreeToTerms(42, setOf(TermsType.SERVICE, TermsType.PRIVACY))
    }

    @Test
    fun `필수 약관이 빠지면 400 AUTH_REQUIRED_TERMS_NOT_AGREED 로 응답한다`() {
        willThrow(AppException(AuthError.AUTH_REQUIRED_TERMS_NOT_AGREED, "test"))
            .given(authService).agreeToTerms(42, setOf(TermsType.SERVICE))

        mockMvc
            .perform(agree(TestJwt.token(42), """{"termsTypes":["SERVICE"]}"""))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.code").value("AUTH_REQUIRED_TERMS_NOT_AGREED"))
    }

    @Test
    fun `모르는 약관 종류면 400 으로 응답한다`() {
        mockMvc
            .perform(agree(TestJwt.token(42), """{"termsTypes":["MARKETING_UNKNOWN"]}"""))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.code").value("INVALID_INPUT"))

        verifyNoInteractions(authService)
    }

    @Test
    fun `약관 종류가 비어 있으면 400 으로 응답한다`() {
        mockMvc
            .perform(agree(TestJwt.token(42), """{"termsTypes":[]}"""))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.code").value("INVALID_INPUT"))
    }

    @Test
    fun `토큰 없이 약관에 동의하면 401 로 응답한다`() {
        mockMvc
            .perform(
                post("/api/v1/members/me/terms-agreements")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"termsTypes":["SERVICE","PRIVACY"]}"""),
            )
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.code").value("AUTH_UNAUTHORIZED"))

        verifyNoInteractions(authService)
    }

    private fun agree(token: String, body: String) = post("/api/v1/members/me/terms-agreements")
        .header(HttpHeaders.AUTHORIZATION, "Bearer $token")
        .contentType(MediaType.APPLICATION_JSON)
        .content(body)
}
