package com.depromeet.team1.infra.external.kakao

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

/**
 * 카카오 로그인 설정. 키는 환경변수로만 받는다(docs/deployment.md).
 * 앱 정보가 비어 있을 때의 처리는 [KakaoConfiguration] 이 프로필에 따라 정한다.
 */
@ConfigurationProperties("kakao")
data class KakaoProperties(
    val clientId: String = "",
    val clientSecret: String = "",
    val redirectUri: String = "",
    val authBaseUrl: String = "https://kauth.kakao.com",
    val apiBaseUrl: String = "https://kapi.kakao.com",
    val connectTimeout: Duration = Duration.ofSeconds(3),
    val readTimeout: Duration = Duration.ofSeconds(5),
) {
    fun isConfigured(): Boolean = clientId.isNotBlank() && clientSecret.isNotBlank() && redirectUri.isNotBlank()
}
