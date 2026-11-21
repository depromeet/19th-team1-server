package com.depromeet.team1.api.web.member

import com.depromeet.team1.api.web.common.CurrentMemberId
import com.depromeet.team1.core.member.AuthService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@Tag(name = "회원")
@RestController
@RequestMapping("/api/v1/members/me")
class MemberController(private val authService: AuthService) {
    @Operation(
        summary = "약관 동의",
        description = "로그인 응답의 termsAgreementRequired 가 참일 때 부른다. 약관마다 현재 버전과 동의 시각을 기록한다. " +
            "아직 동의하지 않은 필수 약관이 빠지면 400 AUTH_REQUIRED_TERMS_NOT_AGREED. 이미 동의한 약관은 다시 기록하지 않는다.",
    )
    @PostMapping("/terms-agreements")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun agreeToTerms(
        @Parameter(hidden = true) @CurrentMemberId memberId: Long,
        @Valid @RequestBody request: TermsAgreementRequest,
    ) {
        authService.agreeToTerms(memberId, request.termsTypes)
    }
}
