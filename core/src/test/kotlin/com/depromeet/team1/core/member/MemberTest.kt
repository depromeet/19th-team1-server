package com.depromeet.team1.core.member

import org.junit.jupiter.api.Test
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class MemberTest {
    @Test
    fun `새 회원은 활성 상태다`() {
        assertEquals(MemberStatus.ACTIVE, Member.create("나현", null, NOW).status)
    }

    @Test
    fun `닉네임이 비어 있거나 30자를 넘으면 만들 수 없다`() {
        assertFailsWith<IllegalArgumentException> { Member.create(" ", null, NOW) }
        assertFailsWith<IllegalArgumentException> { Member.create("가".repeat(31), null, NOW) }
    }

    @Test
    fun `탈퇴 시각 없이 탈퇴 상태일 수 없다`() {
        assertFailsWith<IllegalArgumentException> {
            Member.create("나현", null, NOW).copy(status = MemberStatus.WITHDRAWN)
        }
    }

    companion object {
        private val NOW = Instant.parse("2026-11-21T03:00:00Z")
    }
}
