package com.depromeet.team1.infra.storage.postgresql

import org.springframework.jdbc.core.JdbcTemplate

/**
 * 커밋하는 저장소 테스트가 만든 데이터를 지운다. 테스트마다 끝에서 부른다.
 * Flyway 이력(flyway_schema_history)은 지우지 않는다.
 */
class DatabaseCleaner(private val jdbcTemplate: JdbcTemplate) {
    fun clean() {
        val tables = jdbcTemplate.queryForList(
            """
            select schemaname || '.' || tablename from pg_tables
            where schemaname in ('public', 'analytics') and tablename <> 'flyway_schema_history'
            """.trimIndent(),
            String::class.java,
        )
        if (tables.isNotEmpty()) {
            jdbcTemplate.execute("truncate table ${tables.joinToString()} restart identity")
        }
    }
}
