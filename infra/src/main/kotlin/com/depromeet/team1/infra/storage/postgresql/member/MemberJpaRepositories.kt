package com.depromeet.team1.infra.storage.postgresql.member

import com.depromeet.team1.core.member.AuthProvider
import org.springframework.data.jpa.repository.JpaRepository

interface MemberJpaRepository : JpaRepository<MemberJpaEntity, Long>

interface MemberCredentialJpaRepository : JpaRepository<MemberCredentialJpaEntity, Long> {
    fun findByMemberId(memberId: Long): List<MemberCredentialJpaEntity>

    fun findByProviderAndProviderUserIdAndRevokedAtIsNull(
        provider: AuthProvider,
        providerUserId: String,
    ): MemberCredentialJpaEntity?
}

interface MemberInterestCategoryJpaRepository :
    JpaRepository<MemberInterestCategoryJpaEntity, MemberInterestCategoryId> {
    fun findByMemberId(memberId: Long): List<MemberInterestCategoryJpaEntity>
}

interface MemberTermsAgreementJpaRepository : JpaRepository<MemberTermsAgreementJpaEntity, Long> {
    fun findByMemberId(memberId: Long): List<MemberTermsAgreementJpaEntity>
}
