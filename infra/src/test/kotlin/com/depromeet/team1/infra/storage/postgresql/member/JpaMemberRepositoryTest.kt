package com.depromeet.team1.infra.storage.postgresql.member

import com.depromeet.team1.core.item.Category
import com.depromeet.team1.core.member.AuthProvider
import com.depromeet.team1.core.member.Member
import com.depromeet.team1.core.member.MemberCredential
import com.depromeet.team1.core.member.MemberInterestCategory
import com.depromeet.team1.core.member.MemberRepository
import com.depromeet.team1.infra.storage.postgresql.StorageTest
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.dao.DataIntegrityViolationException
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull

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
}
