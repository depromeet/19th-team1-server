package com.depromeet.team1.infra.storage.postgresql.item

import com.depromeet.team1.core.item.Category
import com.depromeet.team1.core.item.CategorySource
import com.depromeet.team1.core.item.CollectLevel
import com.depromeet.team1.core.item.Platform
import com.depromeet.team1.core.item.ProcessStatus
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.time.Instant

/**
 * 삽입은 ON CONFLICT 네이티브 쿼리, 결과 저장·카테고리 수정은 조건부 UPDATE 로 한다([SavedItemJpaRepository]).
 * 엔티티를 읽어 고친 뒤 다시 저장하지 않는다.
 */
@Entity
@Table(name = "saved_item")
class SavedItemJpaEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long?,
    val memberId: Long,
    @Column(columnDefinition = "text")
    val originalUrl: String,
    @Column(columnDefinition = "text")
    val normalizedUrl: String,
    @Column(length = 64)
    val normalizedUrlHash: String,
    @Enumerated(EnumType.STRING)
    @Column(length = 30)
    val platform: Platform,
    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    val collectLevel: CollectLevel,
    @Column(length = 200)
    val memo: String?,
    @Column(length = 500)
    val title: String?,
    @Column(length = 2048)
    val ogImageUrl: String?,
    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    val processStatus: ProcessStatus,
    @Column(length = 30)
    val failCode: String?,
    @Enumerated(EnumType.STRING)
    @Column(length = 30)
    val category: Category?,
    @Enumerated(EnumType.STRING)
    @Column(length = 10)
    val categorySource: CategorySource?,
    @Column(columnDefinition = "text")
    val summary: String?,
    @JdbcTypeCode(SqlTypes.JSON)
    val categoryData: Map<String, String>?,
    @Column(length = 30)
    val promptVersion: String?,
    val version: Long,
    val createdAt: Instant,
    val updatedAt: Instant,
    val deletedAt: Instant?,
)
