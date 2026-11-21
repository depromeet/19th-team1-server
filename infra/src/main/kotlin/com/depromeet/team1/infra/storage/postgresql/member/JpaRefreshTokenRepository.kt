package com.depromeet.team1.infra.storage.postgresql.member

import com.depromeet.team1.core.member.RefreshToken
import com.depromeet.team1.core.member.RefreshTokenRepository
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

@Repository
class JpaRefreshTokenRepository(private val jpaRepository: RefreshTokenJpaRepository) : RefreshTokenRepository {
    @Transactional
    override fun save(token: RefreshToken): RefreshToken = jpaRepository.saveAndFlush(token.toEntity()).toDomain()

    override fun findByTokenHash(tokenHash: String): RefreshToken? =
        jpaRepository.findByTokenHash(tokenHash)?.toDomain()

    @Transactional
    override fun revoke(id: Long, now: Instant): Boolean = jpaRepository.revoke(id, now) == 1

    @Transactional
    override fun revokeAllActive(memberId: Long, now: Instant): Int = jpaRepository.revokeAllActive(memberId, now)
}
