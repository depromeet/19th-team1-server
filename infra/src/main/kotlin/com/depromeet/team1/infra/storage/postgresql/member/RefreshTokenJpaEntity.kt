package com.depromeet.team1.infra.storage.postgresql.member

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

@Entity
@Table(name = "refresh_token")
class RefreshTokenJpaEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long?,
    val memberId: Long,
    @Column(length = 64)
    val tokenHash: String,
    val expiresAt: Instant,
    val createdAt: Instant,
    val revokedAt: Instant?,
)
