package com.depromeet.team1.core.member

/**
 * 메모리에 저장하는 [MemberRepository]. 번호 발급과 유일 제약(활성 로그인 수단, 같은 약관·버전 동의)을 실제 구현처럼 지킨다.
 * 실제 구현은 유일 제약 위반에 Spring 예외를 던지고, 이 구현은 [IllegalStateException] 을 던진다.
 */
class FakeMemberRepository : MemberRepository {
    private val members = mutableMapOf<Long, Member>()
    private val credentials = mutableListOf<MemberCredential>()
    private val interestCategories = mutableListOf<MemberInterestCategory>()
    private val termsAgreements = mutableListOf<MemberTermsAgreement>()
    private var sequence = 0L

    override fun save(member: Member): Member {
        val saved = member.copy(id = member.id ?: ++sequence)
        members[saved.id!!] = saved
        return saved
    }

    override fun findById(id: Long): Member? = members[id]

    override fun saveCredential(credential: MemberCredential): MemberCredential {
        if (credential.revokedAt == null) {
            check(findActiveCredential(credential.provider, credential.providerUserId) == null) { "활성 로그인 수단 중복" }
        }
        val saved = credential.copy(id = credential.id ?: ++sequence)
        credentials.removeIf { it.id == saved.id }
        credentials += saved
        return saved
    }

    override fun findCredentials(memberId: Long): List<MemberCredential> = credentials.filter {
        it.memberId == memberId
    }

    override fun findActiveCredential(provider: AuthProvider, providerUserId: String): MemberCredential? =
        credentials.find { it.provider == provider && it.providerUserId == providerUserId && it.revokedAt == null }

    override fun signUp(member: Member, provider: AuthProvider, providerUserId: String): Member {
        check(findActiveCredential(provider, providerUserId) == null) { "활성 로그인 수단 중복" }
        val saved = save(member)
        saveCredential(MemberCredential.create(saved.id!!, provider, providerUserId, saved.createdAt))
        return saved
    }

    override fun saveInterestCategory(interestCategory: MemberInterestCategory): MemberInterestCategory {
        interestCategories += interestCategory
        return interestCategory
    }

    override fun findInterestCategories(memberId: Long): List<MemberInterestCategory> =
        interestCategories.filter { it.memberId == memberId }

    override fun saveTermsAgreements(agreements: List<MemberTermsAgreement>) {
        agreements.forEach { agreement ->
            check(
                termsAgreements.none {
                    it.memberId == agreement.memberId &&
                        it.termsType == agreement.termsType &&
                        it.termsVersion == agreement.termsVersion
                },
            ) { "같은 약관·버전 동의 중복" }
        }
        termsAgreements += agreements.map { it.copy(id = ++sequence) }
    }

    override fun findTermsAgreements(memberId: Long): List<MemberTermsAgreement> =
        termsAgreements.filter { it.memberId == memberId }
}
