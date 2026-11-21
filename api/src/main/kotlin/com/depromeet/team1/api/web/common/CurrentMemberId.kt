package com.depromeet.team1.api.web.common

/** 컨트롤러 메서드의 Long 파라미터에 붙이면 요청한 회원의 ID 를 넣는다. 해석은 [CurrentMemberIdArgumentResolver] 한 곳에서 한다. */
@Target(AnnotationTarget.VALUE_PARAMETER)
@Retention(AnnotationRetention.RUNTIME)
annotation class CurrentMemberId
