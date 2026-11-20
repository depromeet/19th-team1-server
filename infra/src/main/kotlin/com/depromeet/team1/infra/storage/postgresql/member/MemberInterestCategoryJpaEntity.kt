package com.depromeet.team1.infra.storage.postgresql.member

import com.depromeet.team1.core.item.Category
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.IdClass
import jakarta.persistence.Table
import java.io.Serializable
import java.time.Instant

/** PK 가 (member_id, category) 인 복합 키 테이블이다. */
@Entity
@Table(name = "member_interest_category")
@IdClass(MemberInterestCategoryId::class)
class MemberInterestCategoryJpaEntity(
    @Id
    val memberId: Long,
    @Id
    @Enumerated(EnumType.STRING)
    @Column(length = 30)
    val category: Category,
    val createdAt: Instant,
)

data class MemberInterestCategoryId(val memberId: Long = 0, val category: Category = Category.ETC) : Serializable
