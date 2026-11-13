package com.depromeet.team1

import com.depromeet.team1.infra.storage.mysql.MySqlContainerConfiguration
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import

@SpringBootTest
@Import(MySqlContainerConfiguration::class)
class ApiApplicationTest {
    @Test
    fun contextLoads() {
    }
}
