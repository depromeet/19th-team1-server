package com.depromeet.team1.infra.storage.postgresql.metric

import com.depromeet.team1.core.metric.BehaviorEvent
import com.depromeet.team1.core.metric.BehaviorEventName
import com.depromeet.team1.core.metric.BehaviorEventRepository
import com.depromeet.team1.infra.storage.postgresql.StorageTest
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import kotlin.test.assertEquals

class JpaBehaviorEventRepositoryTest : StorageTest() {
    @Autowired
    private lateinit var behaviorEventRepository: BehaviorEventRepository

    @Test
    fun `이벤트를 analytics 스키마에 이벤트 코드와 jsonb 속성으로 저장한다`() {
        behaviorEventRepository.save(
            BehaviorEvent(BehaviorEventName.ITEM_VIEWED, 1, mapOf("entryPath" to "CHAT"), NOW),
        )

        val row = jdbcTemplate.queryForMap(
            "select event_name, properties ->> 'entryPath' as entry_path from analytics.behavior_event",
        )
        assertEquals("item_viewed", row["event_name"])
        assertEquals("CHAT", row["entry_path"])
    }
}
