package com.depromeet.team1.infra.external.kakao

import org.slf4j.LoggerFactory
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.env.Environment
import org.springframework.core.env.Profiles

/**
 * 카카오 로그인 앱 정보(KAKAO_CLIENT_ID, KAKAO_CLIENT_SECRET, KAKAO_REDIRECT_URI)가 비어 있을 때
 * dev·prod 는 기동을 멈추고, 그 밖(local)은 경고만 남기고 뜬다. local 에서는 로그인 요청만 실패한다.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(KakaoProperties::class)
class KakaoConfiguration {
    @Bean
    fun kakaoOAuthClient(properties: KakaoProperties, environment: Environment): KakaoOAuthClient {
        if (!properties.isConfigured()) {
            check(!environment.acceptsProfiles(Profiles.of("dev | prod"))) {
                "카카오 로그인 앱 정보가 비어 있다. KAKAO_CLIENT_ID, KAKAO_CLIENT_SECRET, KAKAO_REDIRECT_URI 를 넣는다"
            }
            log.warn("카카오 로그인 앱 정보가 비어 있다. 로그인 요청은 실패한다(KAKAO_CLIENT_ID, KAKAO_CLIENT_SECRET, KAKAO_REDIRECT_URI)")
        }
        return KakaoOAuthClient(properties)
    }

    companion object {
        private val log = LoggerFactory.getLogger(KakaoConfiguration::class.java)
    }
}
