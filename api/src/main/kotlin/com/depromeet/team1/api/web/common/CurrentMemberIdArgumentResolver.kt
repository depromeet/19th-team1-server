package com.depromeet.team1.api.web.common

import com.depromeet.team1.core.common.error.AppException
import com.depromeet.team1.core.member.AuthError
import org.springframework.core.MethodParameter
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken
import org.springframework.web.bind.support.WebDataBinderFactory
import org.springframework.web.context.request.NativeWebRequest
import org.springframework.web.method.support.HandlerMethodArgumentResolver
import org.springframework.web.method.support.ModelAndViewContainer

/** [CurrentMemberId] 파라미터에 액세스 토큰의 회원 번호(sub)를 넣는다. "현재 사용자" 해석은 이곳 한 곳에서 한다. */
class CurrentMemberIdArgumentResolver : HandlerMethodArgumentResolver {
    override fun supportsParameter(parameter: MethodParameter): Boolean =
        parameter.hasParameterAnnotation(CurrentMemberId::class.java)

    override fun resolveArgument(
        parameter: MethodParameter,
        mavContainer: ModelAndViewContainer?,
        webRequest: NativeWebRequest,
        binderFactory: WebDataBinderFactory?,
    ): Any {
        // 인증 없이 열어 둔 경로(인증 API 등)에서 쓰면 토큰이 없다.
        val authentication = SecurityContextHolder.getContext().authentication as? JwtAuthenticationToken
        return authentication?.token?.subject?.toLongOrNull()
            ?: throw AppException(AuthError.AUTH_UNAUTHORIZED, "현재 사용자 없음: ${webRequest.getDescription(false)}")
    }
}
