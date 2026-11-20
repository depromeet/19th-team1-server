package com.depromeet.team1.infra.storage.postgresql

import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.context.annotation.Bean
import org.testcontainers.postgresql.PostgreSQLContainer

// 테스트용 PostgreSQL 컨테이너를 띄우고, 접속 정보를 DataSource 설정에 자동으로 넣는다.
// DB 가 필요한 테스트에서 @Import(PostgreSqlContainerConfiguration::class) 로 쓴다.
@TestConfiguration(proxyBeanMethods = false)
class PostgreSqlContainerConfiguration {
    @Bean
    @ServiceConnection
    fun postgresContainer(): PostgreSQLContainer = PostgreSQLContainer("postgres:16")
}
