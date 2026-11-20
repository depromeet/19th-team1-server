package com.depromeet.team1.infra.storage.postgresql.member

import com.depromeet.team1.core.member.MemberStatus
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

@Entity
@Table(name = "member")
class MemberJpaEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long?,
    @Column(length = 30)
    val nickname: String,
    @Column(length = 320)
    val email: String?,
    @Column(length = 30)
    val acquisitionChannel: String?,
    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    val status: MemberStatus,
    val createdAt: Instant,
    val updatedAt: Instant,
    val withdrawnAt: Instant?,
)
