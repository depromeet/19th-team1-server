package com.depromeet.team1.infra.storage.postgresql.member

import com.depromeet.team1.core.member.TermsType
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
@Table(name = "member_terms_agreement")
class MemberTermsAgreementJpaEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long?,
    val memberId: Long,
    @Enumerated(EnumType.STRING)
    @Column(length = 30)
    val termsType: TermsType,
    @Column(length = 20)
    val termsVersion: String,
    val agreedAt: Instant,
)
