package com.depromeet.team1.infra.storage.postgresql.member

import org.springframework.data.jpa.repository.JpaRepository

interface MemberJpaRepository : JpaRepository<MemberJpaEntity, Long>

interface MemberCredentialJpaRepository : JpaRepository<MemberCredentialJpaEntity, Long> {
    fun findByMemberId(memberId: Long): List<MemberCredentialJpaEntity>
}

interface MemberInterestCategoryJpaRepository :
    JpaRepository<MemberInterestCategoryJpaEntity, MemberInterestCategoryId> {
    fun findByMemberId(memberId: Long): List<MemberInterestCategoryJpaEntity>
}
