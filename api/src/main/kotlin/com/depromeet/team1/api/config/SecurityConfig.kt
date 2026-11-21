package com.depromeet.team1.api.config

import com.depromeet.team1.api.web.common.AuthenticationErrorEntryPoint
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpMethod
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver
import org.springframework.security.oauth2.server.resource.web.DefaultBearerTokenResolver
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher

/**
 * 요청 인증. 무상태 JWT(Authorization: Bearer)만 쓰고 세션·CSRF·폼 로그인은 끈다.
 * 토큰 검증은 infra(security/jwt)가 만든 JwtDecoder 빈으로 한다. 인증 실패 응답은 [AuthenticationErrorEntryPoint] 가 만든다.
 */
@Configuration(proxyBeanMethods = false)
class SecurityConfig {
    @Bean
    fun securityFilterChain(http: HttpSecurity, entryPoint: AuthenticationErrorEntryPoint): SecurityFilterChain {
        http
            .csrf { it.disable() }
            .formLogin { it.disable() }
            .httpBasic { it.disable() }
            .logout { it.disable() }
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .authorizeHttpRequests {
                it.requestMatchers(*PUBLIC_PATHS).permitAll()
                it.requestMatchers(authApi).permitAll()
                it.anyRequest().authenticated()
            }
            .oauth2ResourceServer {
                it.jwt { }
                it.bearerTokenResolver(bearerTokenResolver())
                it.authenticationEntryPoint(entryPoint)
            }
            .exceptionHandling { it.authenticationEntryPoint(entryPoint) }
        return http.build()
    }

    /**
     * 인증 API(로그인·재발급·로그아웃)에서는 Authorization 헤더를 읽지 않는다.
     * 읽으면 웹이 만료된 액세스 토큰을 붙인 채 재발급을 부를 때 그 토큰 때문에 401 이 나서 재발급할 수 없다.
     */
    private fun bearerTokenResolver(): BearerTokenResolver {
        val default = DefaultBearerTokenResolver()
        return BearerTokenResolver { request -> if (authApi.matches(request)) null else default.resolve(request) }
    }

    companion object {
        /** 토큰 없이 부르는 경로. Swagger 는 prod 에서 꺼져 있다. */
        private val PUBLIC_PATHS = arrayOf(
            "/actuator/health",
            "/actuator/health/**",
            "/swagger-ui.html",
            "/swagger-ui/**",
            "/v3/api-docs/**",
            "/error",
        )

        private val authApi = PathPatternRequestMatcher.withDefaults().matcher(HttpMethod.POST, "/api/v1/auth/**")
    }
}
