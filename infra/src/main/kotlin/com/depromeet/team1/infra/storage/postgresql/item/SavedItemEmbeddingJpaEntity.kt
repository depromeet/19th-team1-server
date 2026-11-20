package com.depromeet.team1.infra.storage.postgresql.item

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

/** 저장물당 하나. PK 가 saved_item_id 다. [embedding] 은 float32 배열(little-endian)이다. */
@Entity
@Table(name = "saved_item_embedding")
class SavedItemEmbeddingJpaEntity(
    @Id
    val savedItemId: Long,
    val memberId: Long,
    val embedding: ByteArray,
    val dimension: Int,
    @Column(length = 50)
    val embeddingModel: String,
    val createdAt: Instant,
)
