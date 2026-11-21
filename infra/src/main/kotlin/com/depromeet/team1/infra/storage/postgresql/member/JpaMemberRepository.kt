package com.depromeet.team1.infra.storage.postgresql.member

import com.depromeet.team1.core.member.AuthProvider
import com.depromeet.team1.core.member.Member
import com.depromeet.team1.core.member.MemberCredential
import com.depromeet.team1.core.member.MemberInterestCategory
import com.depromeet.team1.core.member.MemberRepository
import com.depromeet.team1.core.member.MemberTermsAgreement
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional

@Repository
class JpaMemberRepository(
    private val memberJpaRepository: MemberJpaRepository,
    private val credentialJpaRepository: MemberCredentialJpaRepository,
    private val interestCategoryJpaRepository: MemberInterestCategoryJpaRepository,
    private val termsAgreementJpaRepository: MemberTermsAgreementJpaRepository,
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

    override fun findActiveCredential(provider: AuthProvider, providerUserId: String): MemberCredential? =
        credentialJpaRepository.findByProviderAndProviderUserIdAndRevokedAtIsNull(provider, providerUserId)?.toDomain()

    @Transactional
    override fun signUp(member: Member, provider: AuthProvider, providerUserId: String): Member {
        val saved = memberJpaRepository.save(member.toEntity()).toDomain()
        // 같은 계정의 활성 로그인 수단이 있으면 여기서 유일 제약 위반으로 실패하고 회원 저장도 롤백된다.
        credentialJpaRepository.saveAndFlush(
            MemberCredential.create(saved.id!!, provider, providerUserId, saved.createdAt).toEntity(),
        )
        return saved
    }

    @Transactional
    override fun saveTermsAgreements(agreements: List<MemberTermsAgreement>) {
        termsAgreementJpaRepository.saveAllAndFlush(agreements.map { it.toEntity() })
    }

    override fun findTermsAgreements(memberId: Long): List<MemberTermsAgreement> =
        termsAgreementJpaRepository.findByMemberId(memberId).map { it.toDomain() }
}
