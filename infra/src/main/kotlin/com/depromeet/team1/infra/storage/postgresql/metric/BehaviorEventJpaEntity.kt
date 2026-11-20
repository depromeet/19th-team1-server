package com.depromeet.team1.infra.storage.postgresql.metric

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.time.Instant

/** `analytics` 스키마의 이벤트. [eventName] 은 이벤트 코드(`user_active` 등)다. */
@Entity
@Table(schema = "analytics", name = "behavior_event")
class BehaviorEventJpaEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long?,
    @Column(length = 50)
    val eventName: String,
    val memberId: Long,
    @JdbcTypeCode(SqlTypes.JSON)
    val properties: Map<String, Any>?,
    val occurredAt: Instant,
)
