package com.depromeet.team1.api.config

import io.swagger.v3.oas.models.Components
import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.info.Info
import io.swagger.v3.oas.models.security.SecurityRequirement
import io.swagger.v3.oas.models.security.SecurityScheme
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * Swagger 문서 정보. 문서 화면: /swagger-ui.html (prod 에서는 꺼져 있다)
 * 화면의 Authorize 에 로그인 응답의 accessToken 을 넣으면 인증이 필요한 API 를 부를 수 있다.
 */
@Configuration(proxyBeanMethods = false)
class OpenApiConfig {
    @Bean
    fun openApi(): OpenAPI = OpenAPI().info(
        Info()
            .title("team1-server API")
            .description("디프만 19기 1팀 서버 API")
            .version("v1"),
    )
        .components(
            Components().addSecuritySchemes(
                BEARER,
                SecurityScheme().type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT"),
            ),
        )
        .addSecurityItem(SecurityRequirement().addList(BEARER))

    companion object {
        private const val BEARER = "bearer"
    }
}
