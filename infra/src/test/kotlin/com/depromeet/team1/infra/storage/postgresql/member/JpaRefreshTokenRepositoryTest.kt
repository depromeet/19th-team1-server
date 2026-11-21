package com.depromeet.team1.infra.storage.postgresql.member

import com.depromeet.team1.core.member.RefreshToken
import com.depromeet.team1.core.member.RefreshTokenRepository
import com.depromeet.team1.infra.storage.postgresql.StorageTest
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.dao.DataIntegrityViolationException
import java.time.Duration
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class JpaRefreshTokenRepositoryTest : StorageTest() {
    @Autowired
    private lateinit var refreshTokenRepository: RefreshTokenRepository

    @Test
    fun `저장한 토큰을 해시로 찾는다`() {
        val saved = refreshTokenRepository.save(token(memberId = 1, hash = HASH_A))

        val found = refreshTokenRepository.findByTokenHash(HASH_A)

        assertEquals(saved, found)
        assertNull(refreshTokenRepository.findByTokenHash(HASH_B))
    }

    @Test
    fun `같은 해시의 토큰은 두 개 저장할 수 없다`() {
        refreshTokenRepository.save(token(memberId = 1, hash = HASH_A))

        assertFailsWith<DataIntegrityViolationException> {
            refreshTokenRepository.save(token(memberId = 2, hash = HASH_A))
        }
    }

    @Test
    fun `폐기되지 않은 토큰만 폐기하고 두 번째 폐기는 아무것도 바꾸지 않는다`() {
        val saved = refreshTokenRepository.save(token(memberId = 1, hash = HASH_A))
        val later = NOW.plus(Duration.ofMinutes(1))

        val first = refreshTokenRepository.revoke(saved.id!!, NOW)
        val second = refreshTokenRepository.revoke(saved.id!!, later)

        assertTrue(first)
        assertFalse(second)
        assertEquals(NOW, refreshTokenRepository.findByTokenHash(HASH_A)?.revokedAt)
    }

    @Test
    fun `토큰 행을 지운다`() {
        val saved = refreshTokenRepository.save(token(memberId = 1, hash = HASH_A))
        refreshTokenRepository.save(token(memberId = 1, hash = HASH_B))

        refreshTokenRepository.delete(saved.id!!)

        assertNull(refreshTokenRepository.findByTokenHash(HASH_A))
        assertNotNull(refreshTokenRepository.findByTokenHash(HASH_B))
    }

    @Test
    fun `회원의 토큰을 폐기 여부와 무관하게 모두 지우고 다른 회원의 토큰은 남긴다`() {
        refreshTokenRepository.save(token(memberId = 1, hash = HASH_A))
        val rotated = refreshTokenRepository.save(token(memberId = 1, hash = HASH_B))
        refreshTokenRepository.revoke(rotated.id!!, NOW)
        refreshTokenRepository.save(token(memberId = 2, hash = HASH_C))

        val deleted = refreshTokenRepository.deleteAllByMemberId(1)

        assertEquals(2, deleted)
        assertNull(refreshTokenRepository.findByTokenHash(HASH_A))
        assertNull(refreshTokenRepository.findByTokenHash(HASH_B))
        assertNotNull(refreshTokenRepository.findByTokenHash(HASH_C))
    }

    private fun token(memberId: Long, hash: String) =
        RefreshToken(null, memberId, hash, NOW.plus(Duration.ofDays(14)), NOW, null)

    companion object {
        private val HASH_A = "a".repeat(64)
        private val HASH_B = "b".repeat(64)
        private val HASH_C = "c".repeat(64)
    }
}
