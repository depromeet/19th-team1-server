package com.depromeet.team1

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

// 최상위 패키지에 둬서 storage·external 의 빈, JPA 엔티티·리포지토리까지 별도 설정 없이 찾게 한다.
@SpringBootApplication
class ApiApplication

fun main(args: Array<String>) {
    runApplication<ApiApplication>(*args)
}
