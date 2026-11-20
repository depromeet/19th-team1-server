package com.depromeet.team1.infra.storage.postgresql

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class SchemaTest : StorageTest() {
    @Test
    fun `Flyway 가 모든 마이그레이션을 적용하고 엔티티가 스키마와 맞는다`() {
        val applied = jdbcTemplate.queryForObject(
            "select count(*) from flyway_schema_history where success",
            Int::class.java,
        )

        assertEquals(6, applied)
    }
}
