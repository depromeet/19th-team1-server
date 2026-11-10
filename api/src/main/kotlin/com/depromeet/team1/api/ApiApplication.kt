package com.depromeet.team1.api

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

// storage·external 의 구현체도 빈으로 등록되도록 com.depromeet.team1 전체를 스캔한다.
@SpringBootApplication(scanBasePackages = ["com.depromeet.team1"])
class ApiApplication

fun main(args: Array<String>) {
    runApplication<ApiApplication>(*args)
}
