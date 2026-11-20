package com.depromeet.team1.core.member

import com.depromeet.team1.core.item.Category
import java.time.Instant

/** 회원이 홈에서 고른 관심 카테고리 하나. */
data class MemberInterestCategory(val memberId: Long, val category: Category, val createdAt: Instant)
