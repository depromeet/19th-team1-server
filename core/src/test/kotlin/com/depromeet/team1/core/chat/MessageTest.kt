package com.depromeet.team1.core.chat

import org.junit.jupiter.api.Test
import java.time.Instant
import kotlin.test.assertFailsWith

class MessageTest {
    @Test
    fun `사용자 메시지는 DONE 이 아닐 수 없다`() {
        assertFailsWith<IllegalArgumentException> {
            Message(null, 1, MessageRole.USER, "질문", MessageStatus.GENERATING, NOW)
        }
    }

    @Test
    fun `답변은 생성 중에 내용이 비어 있어도 된다`() {
        Message(null, 1, MessageRole.ASSISTANT, "", MessageStatus.GENERATING, NOW)
    }

    companion object {
        private val NOW = Instant.parse("2026-11-21T03:00:00Z")
    }
}
