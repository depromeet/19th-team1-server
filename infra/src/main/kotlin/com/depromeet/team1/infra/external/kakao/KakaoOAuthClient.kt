package com.depromeet.team1.infra.external.kakao

import com.depromeet.team1.core.common.error.AppException
import com.depromeet.team1.core.member.AuthError
import com.depromeet.team1.core.member.AuthProvider
import com.depromeet.team1.core.member.OAuthClient
import com.depromeet.team1.core.member.OAuthUser
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatusCode
import org.springframework.http.MediaType
import org.springframework.http.client.JdkClientHttpRequestFactory
import org.springframework.util.LinkedMultiValueMap
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientException
import org.springframework.web.client.RestClientResponseException
import java.net.http.HttpClient

/**
 * 카카오 로그인. 인가 코드 → 카카오 토큰 → 사용자 정보 순서로 호출한다.
 * 카카오 응답 DTO 는 이 파일 밖으로 내보내지 않고 [OAuthUser] 로 바꿔 돌려준다.
 */
class KakaoOAuthClient(private val properties: KakaoProperties) : OAuthClient {
    override val provider = AuthProvider.KAKAO

    private val restClient: RestClient = RestClient.builder()
        .requestFactory(
            JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(properties.connectTimeout).build(),
            ).apply { setReadTimeout(properties.readTimeout) },
        )
        .build()

    override fun fetchUser(authorizationCode: String): OAuthUser {
        // local 에서는 앱 정보 없이도 뜬다(KakaoConfiguration). 이때는 카카오를 부르지 않고 실패시킨다.
        if (!properties.isConfigured()) throw unavailable("로그인", "앱 정보 없음")
        val accessToken = requestToken(authorizationCode)
        return requestUser(accessToken).toOAuthUser()
    }

    private fun requestToken(authorizationCode: String): String {
        val form = LinkedMultiValueMap<String, String>().apply {
            add("grant_type", "authorization_code")
            add("client_id", properties.clientId)
            add("client_secret", properties.clientSecret)
            add("redirect_uri", properties.redirectUri)
            add("code", authorizationCode)
        }
        val response = call("토큰 발급") {
            restClient.post()
                .uri("${properties.authBaseUrl}/oauth/token")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .onStatus(HttpStatusCode::is4xxClientError) { _, res ->
                    // 만료·재사용·리다이렉트 URI 불일치처럼 인가 코드를 쓸 수 없는 경우다. 응답 본문은 남기지 않는다.
                    val status = res.statusCode.value()
                    log.warn("카카오 인가 코드 거절: status={}", status)
                    throw AppException(AuthError.AUTH_INVALID_OAUTH_CODE, "kakao token status=$status")
                }
                .body(KakaoTokenResponse::class.java)
        }
        return response?.accessToken ?: throw unavailable("토큰 발급", "빈 응답")
    }

    private fun requestUser(accessToken: String): KakaoUserResponse = call("사용자 조회") {
        restClient.get()
            .uri("${properties.apiBaseUrl}/v2/user/me")
            .header("Authorization", "Bearer $accessToken")
            .retrieve()
            .body(KakaoUserResponse::class.java)
    } ?: throw unavailable("사용자 조회", "빈 응답")

    /**
     * 카카오 호출의 오류를 내부 예외로 바꾼다. 이미 바꾼 [AppException] 은 그대로 던진다.
     * 원인 예외는 붙이지 않고 종류만 남긴다. 원인의 메시지에 카카오 응답 본문이 들어 있어 에러 로그에 그대로 찍히기 때문이다.
     */
    private fun <T> call(step: String, block: () -> T): T = try {
        block()
    } catch (e: AppException) {
        throw e
    } catch (e: RestClientResponseException) {
        throw unavailable(step, "status=${e.statusCode.value()} ${e.javaClass.simpleName}")
    } catch (e: RestClientException) {
        throw unavailable(step, e.javaClass.simpleName)
    }

    private fun unavailable(step: String, reason: String): AppException {
        log.warn("카카오 {} 실패: {}", step, reason)
        return AppException(AuthError.AUTH_OAUTH_UNAVAILABLE, "kakao $step: $reason")
    }

    companion object {
        private val log = LoggerFactory.getLogger(KakaoOAuthClient::class.java)
    }
}

@JsonIgnoreProperties(ignoreUnknown = true)
private data class KakaoTokenResponse(@JsonProperty("access_token") val accessToken: String?)

@JsonIgnoreProperties(ignoreUnknown = true)
private data class KakaoUserResponse(val id: Long, @JsonProperty("kakao_account") val kakaoAccount: KakaoAccount?) {
    fun toOAuthUser() = OAuthUser(
        provider = AuthProvider.KAKAO,
        providerUserId = id.toString(),
        nickname = kakaoAccount?.profile?.nickname,
        email = kakaoAccount?.email,
    )
}

@JsonIgnoreProperties(ignoreUnknown = true)
private data class KakaoAccount(val profile: KakaoProfile?, val email: String?)

@JsonIgnoreProperties(ignoreUnknown = true)
private data class KakaoProfile(val nickname: String?)
