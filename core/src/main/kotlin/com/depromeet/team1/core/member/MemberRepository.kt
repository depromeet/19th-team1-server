package com.depromeet.team1.core.member

interface MemberRepository {
    fun save(member: Member): Member

    fun findById(id: Long): Member?

    /** 같은 제공자 계정의 활성 로그인 수단이 이미 있으면 유일 제약 위반으로 실패한다. */
    fun saveCredential(credential: MemberCredential): MemberCredential

    fun findCredentials(memberId: Long): List<MemberCredential>

    /** 연결을 끊지 않은 로그인 수단. 로그인할 때 회원을 찾는다. */
    fun findActiveCredential(provider: AuthProvider, providerUserId: String): MemberCredential?

    /** 회원과 그 회원의 첫 로그인 수단을 함께 저장한다. 같은 제공자 계정의 활성 로그인 수단이 있으면 둘 다 저장하지 않고 실패한다. */
    fun signUp(member: Member, provider: AuthProvider, providerUserId: String): Member

    fun saveInterestCategory(interestCategory: MemberInterestCategory): MemberInterestCategory

    fun findInterestCategories(memberId: Long): List<MemberInterestCategory>

    /** 여러 약관 동의를 함께 저장한다. 같은 회원·약관·버전이 이미 있으면 모두 저장하지 않고 실패한다. */
    fun saveTermsAgreements(agreements: List<MemberTermsAgreement>)

    fun findTermsAgreements(memberId: Long): List<MemberTermsAgreement>
}
