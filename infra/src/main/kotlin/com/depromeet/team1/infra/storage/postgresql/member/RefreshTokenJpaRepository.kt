package com.depromeet.team1.infra.storage.postgresql.member

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.Instant

/**
 * 리프레시 토큰. 로테이션 폐기는 "아직 폐기되지 않음" 조건을 붙인 UPDATE 로 해서, 동시에 쓰인 토큰은 한 번만 폐기된다.
 * 로그아웃과 탈취 감지는 행을 지운다.
 */
interface RefreshTokenJpaRepository : JpaRepository<RefreshTokenJpaEntity, Long> {
    fun findByTokenHash(tokenHash: String): RefreshTokenJpaEntity?

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update RefreshTokenJpaEntity t set t.revokedAt = :now where t.id = :id and t.revokedAt is null")
    fun revoke(@Param("id") id: Long, @Param("now") now: Instant): Int

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from RefreshTokenJpaEntity t where t.memberId = :memberId")
    fun deleteAllByMemberId(@Param("memberId") memberId: Long): Int
}
