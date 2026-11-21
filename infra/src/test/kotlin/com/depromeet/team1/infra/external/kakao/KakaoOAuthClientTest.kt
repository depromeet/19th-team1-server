package com.depromeet.team1.infra.external.kakao

import com.depromeet.team1.core.common.error.AppException
import com.depromeet.team1.core.member.AuthError
import com.depromeet.team1.core.member.AuthProvider
import com.depromeet.team1.core.member.OAuthUser
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Duration
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class KakaoOAuthClientTest {
    private val server = MockWebServer()
    private lateinit var client: KakaoOAuthClient

    @BeforeEach
    fun setUp() {
        server.start()
        // 응답 타임아웃은 본문을 다 읽을 때까지 걸린다. 첫 호출의 JSON 변환기 초기화가 느린 CI 에서도 넘지 않게 넉넉히 둔다.
        client = clientWithReadTimeout(Duration.ofSeconds(5))
    }

    private fun clientWithReadTimeout(readTimeout: Duration): KakaoOAuthClient {
        val baseUrl = server.url("/").toString().removeSuffix("/")
        return KakaoOAuthClient(
            KakaoProperties(
                clientId = "test-client-id",
                clientSecret = "test-client-secret",
                redirectUri = "https://web.example.com/login/kakao",
                authBaseUrl = baseUrl,
                apiBaseUrl = baseUrl,
                connectTimeout = Duration.ofMillis(500),
                readTimeout = readTimeout,
            ),
        )
    }

    @AfterEach
    fun tearDown() {
        server.close()
    }

    @Test
    fun `인가 코드로 카카오 사용자 정보를 가져온다`() {
        server.enqueue(json(200, """{"access_token":"kakao-access","token_type":"bearer","expires_in":21599}"""))
        server.enqueue(
            json(
                200,
                """{"id":4012345678,"kakao_account":{"profile":{"nickname":"나현"},"email":"user@example.com"}}""",
            ),
        )

        val user = client.fetchUser("auth-code")

        assertEquals(OAuthUser(AuthProvider.KAKAO, "4012345678", "나현", "user@example.com"), user)
    }

    @Test
    fun `토큰 요청에 인가 코드와 앱 정보를 담고 사용자 조회에 받은 토큰을 쓴다`() {
        server.enqueue(json(200, """{"access_token":"kakao-access","token_type":"bearer","expires_in":21599}"""))
        server.enqueue(json(200, """{"id":1}"""))

        client.fetchUser("auth-code")

        val tokenRequest = server.takeRequest()
        assertEquals("POST", tokenRequest.method)
        assertEquals("/oauth/token", tokenRequest.url.encodedPath)
        assertEquals(
            mapOf(
                "grant_type" to "authorization_code",
                "client_id" to "test-client-id",
                "client_secret" to "test-client-secret",
                "redirect_uri" to "https://web.example.com/login/kakao",
                "code" to "auth-code",
            ),
            formOf(tokenRequest.body!!.utf8()),
        )
        val userRequest = server.takeRequest()
        assertEquals("GET", userRequest.method)
        assertEquals("/v2/user/me", userRequest.url.encodedPath)
        assertEquals("Bearer kakao-access", userRequest.headers["Authorization"])
    }

    @Test
    fun `동의하지 않은 닉네임과 이메일은 비워 둔다`() {
        server.enqueue(json(200, """{"access_token":"kakao-access","token_type":"bearer","expires_in":21599}"""))
        server.enqueue(json(200, """{"id":4012345678,"kakao_account":{}}"""))

        val user = client.fetchUser("auth-code")

        assertNull(user.nickname)
        assertNull(user.email)
    }

    @Test
    fun `카카오가 인가 코드를 거절하면 잘못된 인가 코드로 실패한다`() {
        server.enqueue(json(400, """{"error":"invalid_grant","error_code":"KOE320"}"""))

        val error = assertFailsWith<AppException> { client.fetchUser("used-code") }

        assertEquals(AuthError.AUTH_INVALID_OAUTH_CODE, error.errorCode)
    }

    @Test
    fun `카카오 서버 오류는 연동 실패로 바꾼다`() {
        server.enqueue(json(200, """{"access_token":"kakao-access","token_type":"bearer","expires_in":21599}"""))
        server.enqueue(json(500, """{"msg":"internal error","code":-1}"""))

        val error = assertFailsWith<AppException> { client.fetchUser("auth-code") }

        assertEquals(AuthError.AUTH_OAUTH_UNAVAILABLE, error.errorCode)
    }

    @Test
    fun `카카오 오류 응답 본문은 예외와 그 원인에 남기지 않는다`() {
        server.enqueue(json(200, """{"access_token":"kakao-access","token_type":"bearer","expires_in":21599}"""))
        server.enqueue(json(500, """{"msg":"kakao-body-marker","code":-1}"""))

        val error = assertFailsWith<AppException> { client.fetchUser("auth-code") }

        assertEquals(AuthError.AUTH_OAUTH_UNAVAILABLE, error.errorCode)
        val messages = generateSequence<Throwable>(error) { it.cause }.mapNotNull { it.message }.toList()
        assertTrue(messages.none { "kakao-body-marker" in it }, "예외 메시지에 응답 본문이 남았다: $messages")
    }

    @Test
    fun `응답 시간을 넘기면 연동 실패로 바꾼다`() {
        server.enqueue(
            MockResponse.Builder()
                .code(200)
                .headersDelay(2, TimeUnit.SECONDS)
                .body("""{"access_token":"kakao-access"}""")
                .build(),
        )
        val impatientClient = clientWithReadTimeout(Duration.ofMillis(300))

        val error = assertFailsWith<AppException> { impatientClient.fetchUser("auth-code") }

        assertEquals(AuthError.AUTH_OAUTH_UNAVAILABLE, error.errorCode)
    }

    @Test
    fun `앱 정보가 없으면 카카오를 부르지 않고 연동 실패로 끝낸다`() {
        val unconfigured = KakaoOAuthClient(
            KakaoProperties(
                clientId = "",
                clientSecret = "",
                redirectUri = "",
                authBaseUrl = server.url("/").toString().removeSuffix("/"),
            ),
        )

        val error = assertFailsWith<AppException> { unconfigured.fetchUser("auth-code") }

        assertEquals(AuthError.AUTH_OAUTH_UNAVAILABLE, error.errorCode)
        assertEquals(0, server.requestCount)
    }

    private fun json(code: Int, body: String): MockResponse = MockResponse.Builder()
        .code(code)
        .addHeader("Content-Type", "application/json;charset=UTF-8")
        .body(body)
        .build()

    private fun formOf(body: String): Map<String, String> = body.split("&").associate {
        val (key, value) = it.split("=", limit = 2)
        java.net.URLDecoder.decode(key, Charsets.UTF_8) to java.net.URLDecoder.decode(value, Charsets.UTF_8)
    }
}
