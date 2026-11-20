package com.depromeet.team1.infra.storage.postgresql.item

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

@Entity
@Table(name = "saved_item_keyword")
class SavedItemKeywordJpaEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long?,
    val savedItemId: Long,
    @Column(length = 50)
    val keyword: String,
    val sortOrder: Short,
    val createdAt: Instant,
)
