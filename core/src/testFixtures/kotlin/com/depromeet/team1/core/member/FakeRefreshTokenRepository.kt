package com.depromeet.team1.core.member

import java.time.Instant

/** 메모리에 저장하는 [RefreshTokenRepository]. 번호 발급, 해시 유일 제약, 조건부 폐기를 실제 구현처럼 지킨다. */
class FakeRefreshTokenRepository : RefreshTokenRepository {
    private val tokens = mutableMapOf<Long, RefreshToken>()
    private var sequence = 0L

    /**
     * 다음 [revoke] 직전에 한 번 실행한다. 같은 토큰으로 다른 요청이 먼저 재발급한 상황(동시 재발급)을 재현할 때 쓴다.
     */
    var beforeNextRevoke: (() -> Unit)? = null

    override fun save(token: RefreshToken): RefreshToken {
        check(tokens.values.none { it.tokenHash == token.tokenHash && it.id != token.id }) { "토큰 해시 중복" }
        val saved = token.copy(id = token.id ?: ++sequence)
        tokens[saved.id!!] = saved
        return saved
    }

    override fun findByTokenHash(tokenHash: String): RefreshToken? = tokens.values.find { it.tokenHash == tokenHash }

    override fun revoke(id: Long, now: Instant): Boolean {
        beforeNextRevoke?.let {
            beforeNextRevoke = null
            it()
        }
        val token = tokens[id] ?: return false
        if (token.revokedAt != null) return false
        tokens[id] = token.copy(revokedAt = now)
        return true
    }

    override fun delete(id: Long) {
        tokens.remove(id)
    }

    override fun deleteAllByMemberId(memberId: Long): Int {
        val ids = tokens.values.filter { it.memberId == memberId }.map { it.id!! }
        ids.forEach(tokens::remove)
        return ids.size
    }

    /** 테스트 확인용. 회원의 모든 토큰. */
    fun findAll(memberId: Long): List<RefreshToken> = tokens.values.filter { it.memberId == memberId }
}
