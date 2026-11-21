package com.depromeet.team1

import com.depromeet.team1.infra.storage.postgresql.PostgreSqlContainerConfiguration
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import

// 큐에 접속하지 않도록 받기 루프와 예약 작업을 끈다. SQS 클라이언트는 만들기만 하고 호출하지 않는다.
@SpringBootTest(
    properties = [
        "SQS_QUEUE_URL=http://localhost:9324/000000000000/test",
        "SQS_REGION=ap-southeast-2",
        "WORKER_ENABLED=false",
        "SCHEDULER_ENABLED=false",
    ],
)
@Import(PostgreSqlContainerConfiguration::class)
class ApiApplicationTest {
    @Test
    fun contextLoads() {
    }
}
