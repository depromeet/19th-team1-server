package com.depromeet.team1.infra.storage.postgresql

import org.junit.jupiter.api.AfterEach
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.jdbc.core.JdbcTemplate
import java.time.Instant

/**
 * 저장소 테스트의 공통 설정. 실제 PostgreSQL 에 Flyway 로 스키마를 만들고, 엔티티가 스키마와 맞는지(validate) 검사한다.
 * 저장소 메서드가 스스로 커밋하므로 롤백하지 않고, 테스트마다 데이터를 지운다.
 */
@SpringBootTest(classes = [StorageTestApplication::class])
@Import(PostgreSqlContainerConfiguration::class)
abstract class StorageTest {
    @Autowired
    protected lateinit var jdbcTemplate: JdbcTemplate

    @AfterEach
    fun cleanDatabase() {
        DatabaseCleaner(jdbcTemplate).clean()
    }

    companion object {
        val NOW: Instant = Instant.parse("2026-11-21T03:00:00Z")
    }
}
