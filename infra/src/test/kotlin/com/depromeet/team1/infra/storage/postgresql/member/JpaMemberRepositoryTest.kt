package com.depromeet.team1.infra.storage.postgresql.member

import com.depromeet.team1.core.item.Category
import com.depromeet.team1.core.member.AuthProvider
import com.depromeet.team1.core.member.Member
import com.depromeet.team1.core.member.MemberCredential
import com.depromeet.team1.core.member.MemberInterestCategory
import com.depromeet.team1.core.member.MemberRepository
import com.depromeet.team1.core.member.MemberTermsAgreement
import com.depromeet.team1.core.member.TermsType
import com.depromeet.team1.infra.storage.postgresql.StorageTest
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.dao.DataIntegrityViolationException
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class JpaMemberRepositoryTest : StorageTest() {
    @Autowired
    private lateinit var memberRepository: MemberRepository

    @Test
    fun `저장한 회원을 ID 로 조회한다`() {
        val saved = memberRepository.save(Member.create("나현", null, NOW))

        val found = memberRepository.findById(assertNotNull(saved.id))

        assertEquals(saved, found)
    }

    @Test
    fun `같은 카카오 계정의 활성 로그인 수단은 두 개 만들 수 없다`() {
        val memberId = memberRepository.save(Member.create("나현", null, NOW)).id!!
        memberRepository.saveCredential(MemberCredential.create(memberId, AuthProvider.KAKAO, "kakao-1", NOW))

        assertFailsWith<DataIntegrityViolationException> {
            memberRepository.saveCredential(MemberCredential.create(memberId, AuthProvider.KAKAO, "kakao-1", NOW))
        }
    }

    @Test
    fun `연결을 끊은 카카오 계정으로 다시 가입할 수 있다`() {
        val oldMemberId = memberRepository.save(Member.create("나현", null, NOW)).id!!
        memberRepository.saveCredential(
            MemberCredential.create(oldMemberId, AuthProvider.KAKAO, "kakao-1", NOW).copy(revokedAt = NOW),
        )
        val newMemberId = memberRepository.save(Member.create("나현", null, NOW)).id!!

        memberRepository.saveCredential(MemberCredential.create(newMemberId, AuthProvider.KAKAO, "kakao-1", NOW))

        assertEquals(1, memberRepository.findCredentials(newMemberId).size)
    }

    @Test
    fun `관심 카테고리를 저장하고 회원별로 조회한다`() {
        val memberId = memberRepository.save(Member.create("나현", null, NOW)).id!!
        memberRepository.saveInterestCategory(MemberInterestCategory(memberId, Category.PLACE, NOW))
        memberRepository.saveInterestCategory(MemberInterestCategory(memberId, Category.CONTENT, NOW))

        val found = memberRepository.findInterestCategories(memberId)

        assertEquals(setOf(Category.PLACE, Category.CONTENT), found.map { it.category }.toSet())
    }

    @Test
    fun `제공자 계정으로 연결을 끊지 않은 로그인 수단을 찾는다`() {
        val oldMemberId = memberRepository.save(Member.create("예전", null, NOW)).id!!
        memberRepository.saveCredential(
            MemberCredential.create(oldMemberId, AuthProvider.KAKAO, "kakao-1", NOW).copy(revokedAt = NOW),
        )
        val memberId = memberRepository.save(Member.create("나현", null, NOW)).id!!
        memberRepository.saveCredential(MemberCredential.create(memberId, AuthProvider.KAKAO, "kakao-1", NOW))

        val found = memberRepository.findActiveCredential(AuthProvider.KAKAO, "kakao-1")

        assertEquals(memberId, found?.memberId)
        assertNull(memberRepository.findActiveCredential(AuthProvider.KAKAO, "kakao-2"))
    }

    @Test
    fun `가입하면 회원과 로그인 수단을 함께 저장한다`() {
        val member = memberRepository.signUp(Member.create("나현", null, NOW), AuthProvider.KAKAO, "kakao-1")

        assertEquals(member, memberRepository.findById(assertNotNull(member.id)))
        assertEquals(member.id, memberRepository.findActiveCredential(AuthProvider.KAKAO, "kakao-1")?.memberId)
    }

    @Test
    fun `이미 가입한 제공자 계정으로 가입하면 회원도 저장하지 않는다`() {
        memberRepository.signUp(Member.create("나현", null, NOW), AuthProvider.KAKAO, "kakao-1")

        assertFailsWith<DataIntegrityViolationException> {
            memberRepository.signUp(Member.create("중복", null, NOW), AuthProvider.KAKAO, "kakao-1")
        }

        assertEquals(1, jdbcTemplate.queryForObject("select count(*) from member", Int::class.java))
    }

    @Test
    fun `약관 동의를 함께 저장하고 회원별로 조회한다`() {
        val memberId = memberRepository.save(Member.create("나현", null, NOW)).id!!

        memberRepository.saveTermsAgreements(
            listOf(
                MemberTermsAgreement(null, memberId, TermsType.SERVICE, "1", NOW),
                MemberTermsAgreement(null, memberId, TermsType.PRIVACY, "1", NOW),
            ),
        )

        assertEquals(
            setOf(TermsType.SERVICE to "1", TermsType.PRIVACY to "1"),
            memberRepository.findTermsAgreements(memberId).map { it.termsType to it.termsVersion }.toSet(),
        )
    }

    @Test
    fun `같은 약관의 같은 버전에 다시 동의하면 함께 저장하려던 동의도 저장하지 않는다`() {
        val memberId = memberRepository.save(Member.create("나현", null, NOW)).id!!
        memberRepository.saveTermsAgreements(listOf(MemberTermsAgreement(null, memberId, TermsType.SERVICE, "1", NOW)))

        assertFailsWith<DataIntegrityViolationException> {
            memberRepository.saveTermsAgreements(
                listOf(
                    MemberTermsAgreement(null, memberId, TermsType.PRIVACY, "1", NOW),
                    MemberTermsAgreement(null, memberId, TermsType.SERVICE, "1", NOW),
                ),
            )
        }

        assertEquals(listOf(TermsType.SERVICE), memberRepository.findTermsAgreements(memberId).map { it.termsType })
    }
}
