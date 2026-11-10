package com.depromeet.team1.api.web.common

/** 성공 응답 본문. 컨트롤러는 응답 데이터를 이 형태로 감싸 반환한다: `{ "data": ... }` */
data class ApiResponse<T>(val data: T)
