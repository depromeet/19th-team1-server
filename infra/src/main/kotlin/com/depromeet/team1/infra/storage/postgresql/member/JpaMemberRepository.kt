package com.depromeet.team1.infra.storage.postgresql.member

import com.depromeet.team1.core.member.Member
import com.depromeet.team1.core.member.MemberCredential
import com.depromeet.team1.core.member.MemberInterestCategory
import com.depromeet.team1.core.member.MemberRepository
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional

@Repository
class JpaMemberRepository(
    private val memberJpaRepository: MemberJpaRepository,
    private val credentialJpaRepository: MemberCredentialJpaRepository,
    private val interestCategoryJpaRepository: MemberInterestCategoryJpaRepository,
) : MemberRepository {
    @Transactional
    override fun save(member: Member): Member = memberJpaRepository.save(member.toEntity()).toDomain()

    override fun findById(id: Long): Member? = memberJpaRepository.findById(id).orElse(null)?.toDomain()

    @Transactional
    override fun saveCredential(credential: MemberCredential): MemberCredential =
        credentialJpaRepository.saveAndFlush(credential.toEntity()).toDomain()

    override fun findCredentials(memberId: Long): List<MemberCredential> =
        credentialJpaRepository.findByMemberId(memberId).map { it.toDomain() }

    @Transactional
    override fun saveInterestCategory(interestCategory: MemberInterestCategory): MemberInterestCategory =
        interestCategoryJpaRepository.save(interestCategory.toEntity()).toDomain()

    override fun findInterestCategories(memberId: Long): List<MemberInterestCategory> =
        interestCategoryJpaRepository.findByMemberId(memberId).map { it.toDomain() }
}
