package com.depromeet.team1.api.web.common

import com.depromeet.team1.core.member.AuthError
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.security.core.AuthenticationException
import org.springframework.security.oauth2.jwt.JwtValidationException
import org.springframework.security.web.AuthenticationEntryPoint
import org.springframework.stereotype.Component
import tools.jackson.databind.json.JsonMapper

/**
 * 인증 실패(토큰 없음·위조·만료)를 다른 에러와 같은 [ErrorResponse] 형식의 401 로 응답한다.
 * 만료는 [AuthError.AUTH_TOKEN_EXPIRED] 로 구분해 웹이 재발급을 시도하게 하고, 나머지는 [AuthError.AUTH_UNAUTHORIZED] 다.
 */
@Component
class AuthenticationErrorEntryPoint(private val jsonMapper: JsonMapper) : AuthenticationEntryPoint {
    override fun commence(request: HttpServletRequest, response: HttpServletResponse, e: AuthenticationException) {
        val errorCode = if (isExpired(e)) AuthError.AUTH_TOKEN_EXPIRED else AuthError.AUTH_UNAUTHORIZED
        log.warn("[{}] {} {}: {}", errorCode.name, request.method, request.requestURI, e.message)
        response.status = HttpStatus.UNAUTHORIZED.value()
        response.contentType = MediaType.APPLICATION_JSON_VALUE
        response.characterEncoding = Charsets.UTF_8.name()
        jsonMapper.writeValue(response.outputStream, ErrorResponse.of(errorCode))
    }

    /** 서명은 맞고 만료만 된 토큰. 만료 오류는 Spring 의 JwtTimestampValidator 가 "Jwt expired at ..." 로 낸다. */
    private fun isExpired(e: AuthenticationException): Boolean = generateSequence<Throwable>(e) { it.cause }
        .filterIsInstance<JwtValidationException>()
        .any { validation -> validation.errors.any { it.description?.startsWith(EXPIRED_PREFIX) == true } }

    companion object {
        private const val EXPIRED_PREFIX = "Jwt expired"
        private val log = LoggerFactory.getLogger(AuthenticationErrorEntryPoint::class.java)
    }
}
