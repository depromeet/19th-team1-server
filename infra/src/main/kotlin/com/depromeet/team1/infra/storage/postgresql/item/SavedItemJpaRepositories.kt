package com.depromeet.team1.infra.storage.postgresql.item

import com.depromeet.team1.core.item.Category
import com.depromeet.team1.core.item.CategorySource
import com.depromeet.team1.core.item.CollectLevel
import com.depromeet.team1.core.item.Platform
import com.depromeet.team1.core.item.ProcessStatus
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.Instant

interface SavedItemJpaRepository : JpaRepository<SavedItemJpaEntity, Long> {
    /**
     * 삭제되지 않은 같은 링크가 없을 때만 삽입하고 새 ID 를 돌려준다. 있으면 null.
     * ON CONFLICT 에는 부분 유일 인덱스의 WHERE 조건까지 적어야 그 인덱스를 고른다.
     */
    @Query(
        value = """
            insert into saved_item (
                member_id, original_url, normalized_url, normalized_url_hash, platform, collect_level, memo,
                title, og_image_url, process_status, version, created_at, updated_at
            ) values (
                :memberId, :originalUrl, :normalizedUrl, :normalizedUrlHash, :platform, :collectLevel, :memo,
                :title, :ogImageUrl, :processStatus, 0, :now, :now
            )
            on conflict (member_id, normalized_url_hash) where deleted_at is null do nothing
            returning id
        """,
        nativeQuery = true,
    )
    fun insertIfAbsent(
        @Param("memberId") memberId: Long,
        @Param("originalUrl") originalUrl: String,
        @Param("normalizedUrl") normalizedUrl: String,
        @Param("normalizedUrlHash") normalizedUrlHash: String,
        @Param("platform") platform: String,
        @Param("collectLevel") collectLevel: String,
        @Param("memo") memo: String?,
        @Param("title") title: String?,
        @Param("ogImageUrl") ogImageUrl: String?,
        @Param("processStatus") processStatus: String,
        @Param("now") now: Instant,
    ): Long?

    @Query(
        """
        select e.id from SavedItemJpaEntity e
        where e.memberId = :memberId and e.normalizedUrlHash = :hash and e.deletedAt is null
        """,
    )
    fun findActiveId(@Param("memberId") memberId: Long, @Param("hash") normalizedUrlHash: String): Long?

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(
        """
        update SavedItemJpaEntity e set
            e.title = :title, e.ogImageUrl = :ogImageUrl, e.platform = :platform, e.collectLevel = :collectLevel,
            e.category = :category, e.categorySource = :categorySource, e.summary = :summary,
            e.categoryData = :categoryData, e.promptVersion = :promptVersion,
            e.version = e.version + 1, e.updatedAt = :now
        where e.id = :id and e.version = :expectedVersion and e.deletedAt is null
        """,
    )
    fun updateAnalysisResult(
        @Param("id") id: Long,
        @Param("expectedVersion") expectedVersion: Long,
        @Param("title") title: String?,
        @Param("ogImageUrl") ogImageUrl: String?,
        @Param("platform") platform: Platform,
        @Param("collectLevel") collectLevel: CollectLevel,
        @Param("category") category: Category?,
        @Param("categorySource") categorySource: CategorySource?,
        @Param("summary") summary: String?,
        @Param("categoryData") categoryData: Map<String, String>?,
        @Param("promptVersion") promptVersion: String?,
        @Param("now") now: Instant,
    ): Int

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(
        """
        update SavedItemJpaEntity e set
            e.category = :category, e.categorySource = :source, e.categoryData = null,
            e.version = e.version + 1, e.updatedAt = :now
        where e.id = :id and e.deletedAt is null and e.processStatus in :changeable
        """,
    )
    fun updateCategory(
        @Param("id") id: Long,
        @Param("category") category: Category,
        @Param("source") source: CategorySource,
        @Param("changeable") changeable: Collection<ProcessStatus>,
        @Param("now") now: Instant,
    ): Int

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(
        """
        update SavedItemJpaEntity e set e.processStatus = :status, e.failCode = :failCode, e.updatedAt = :now
        where e.id = :id and e.deletedAt is null
        """,
    )
    fun updateProcessStatus(
        @Param("id") id: Long,
        @Param("status") status: ProcessStatus,
        @Param("failCode") failCode: String?,
        @Param("now") now: Instant,
    ): Int
}

interface SavedItemKeywordJpaRepository : JpaRepository<SavedItemKeywordJpaEntity, Long> {
    fun findBySavedItemIdOrderBySortOrder(savedItemId: Long): List<SavedItemKeywordJpaEntity>

    /** 바로 DELETE 를 실행한다. 이어서 삽입해도 Hibernate 의 INSERT 우선 순서에 걸리지 않는다. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from SavedItemKeywordJpaEntity k where k.savedItemId = :savedItemId")
    fun deleteBySavedItemId(@Param("savedItemId") savedItemId: Long): Int
}

interface SavedItemEmbeddingJpaRepository : JpaRepository<SavedItemEmbeddingJpaEntity, Long>
