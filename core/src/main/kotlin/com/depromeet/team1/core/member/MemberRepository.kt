package com.depromeet.team1.core.member

interface MemberRepository {
    fun save(member: Member): Member

    fun findById(id: Long): Member?

    /** 같은 제공자 계정의 활성 로그인 수단이 이미 있으면 유일 제약 위반으로 실패한다. */
    fun saveCredential(credential: MemberCredential): MemberCredential

    fun findCredentials(memberId: Long): List<MemberCredential>

    fun saveInterestCategory(interestCategory: MemberInterestCategory): MemberInterestCategory

    fun findInterestCategories(memberId: Long): List<MemberInterestCategory>
}
