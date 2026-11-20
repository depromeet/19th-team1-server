package com.depromeet.team1.infra.storage.postgresql.member

import com.depromeet.team1.core.member.AuthProvider
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
@Table(name = "member_credential")
class MemberCredentialJpaEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long?,
    val memberId: Long,
    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    val provider: AuthProvider,
    @Column(length = 100)
    val providerUserId: String,
    val createdAt: Instant,
    val revokedAt: Instant?,
)
