package com.depromeet.team1.api.web.common

import com.depromeet.team1.api.config.SecurityConfig
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.http.HttpHeaders
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RestController
import java.time.Duration
import javax.crypto.spec.SecretKeySpec

/** 요청 인증과 현재 사용자 해석. 보안 설정과 테스트용 컨트롤러만 띄운다. */
@WebMvcTest(controllers = [AuthenticationTest.SampleController::class])
@Import(
    SecurityConfig::class,
    AuthenticationErrorEntryPoint::class,
    AuthenticationTest.SampleController::class,
    TestJwt.DecoderConfig::class,
)
class AuthenticationTest {
    @Autowired
    private lateinit var mockMvc: MockMvc

    @Test
    fun `유효한 액세스 토큰이면 컨트롤러가 현재 사용자 ID 를 받는다`() {
        mockMvc
            .perform(get("/sample/me").header(HttpHeaders.AUTHORIZATION, "Bearer ${TestJwt.token(42)}"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data").value(42))
    }

    @Test
    fun `토큰이 없으면 401 AUTH_UNAUTHORIZED 로 응답한다`() {
        mockMvc
            .perform(get("/sample/me"))
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.code").value("AUTH_UNAUTHORIZED"))
    }

    @Test
    fun `다른 키로 서명한 토큰이면 401 AUTH_UNAUTHORIZED 로 응답한다`() {
        val forged = TestJwt.token(42, key = SecretKeySpec(ByteArray(32) { 9 }, "HmacSHA256"))

        mockMvc
            .perform(get("/sample/me").header(HttpHeaders.AUTHORIZATION, "Bearer $forged"))
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.code").value("AUTH_UNAUTHORIZED"))
    }

    @Test
    fun `토큰 형식이 아니면 401 AUTH_UNAUTHORIZED 로 응답한다`() {
        mockMvc
            .perform(get("/sample/me").header(HttpHeaders.AUTHORIZATION, "Bearer not-a-jwt"))
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.code").value("AUTH_UNAUTHORIZED"))
    }

    @Test
    fun `만료된 토큰이면 401 AUTH_TOKEN_EXPIRED 로 응답한다`() {
        val expired = TestJwt.token(42, issuedAt = TestJwt.NOW.minus(Duration.ofMinutes(31)))

        mockMvc
            .perform(get("/sample/me").header(HttpHeaders.AUTHORIZATION, "Bearer $expired"))
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.code").value("AUTH_TOKEN_EXPIRED"))
    }

    @Test
    fun `인증 API 는 토큰 없이 부를 수 있다`() {
        mockMvc
            .perform(post("/api/v1/auth/sample"))
            .andExpect(status().isOk)
    }

    @Test
    fun `인증 API 에 만료된 토큰이 붙어 와도 토큰을 검사하지 않는다`() {
        val expired = TestJwt.token(42, issuedAt = TestJwt.NOW.minus(Duration.ofMinutes(31)))

        mockMvc
            .perform(post("/api/v1/auth/sample").header(HttpHeaders.AUTHORIZATION, "Bearer $expired"))
            .andExpect(status().isOk)
    }

    @RestController
    class SampleController {
        @GetMapping("/sample/me")
        fun me(@CurrentMemberId memberId: Long) = ApiResponse(memberId)

        @PostMapping("/api/v1/auth/sample")
        fun open() = ApiResponse("ok")
    }
}
