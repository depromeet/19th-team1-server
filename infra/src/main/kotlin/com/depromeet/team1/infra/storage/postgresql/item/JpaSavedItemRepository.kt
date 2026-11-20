package com.depromeet.team1.infra.storage.postgresql.item

import com.depromeet.team1.core.item.AnalysisResult
import com.depromeet.team1.core.item.Category
import com.depromeet.team1.core.item.CategorySource
import com.depromeet.team1.core.item.ProcessStatus
import com.depromeet.team1.core.item.SavedItem
import com.depromeet.team1.core.item.SavedItemEmbedding
import com.depromeet.team1.core.item.SavedItemInsertResult
import com.depromeet.team1.core.item.SavedItemKeyword
import com.depromeet.team1.core.item.SavedItemRepository
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

@Repository
class JpaSavedItemRepository(
    private val savedItemJpaRepository: SavedItemJpaRepository,
    private val keywordJpaRepository: SavedItemKeywordJpaRepository,
    private val embeddingJpaRepository: SavedItemEmbeddingJpaRepository,
) : SavedItemRepository {
    /** 새 저장물만 받는다. 분석 결과(카테고리·상세 정보 등)는 [saveAnalysisResult] 로 채운다. */
    @Transactional
    override fun insert(item: SavedItem): SavedItemInsertResult {
        require(item.id == null && item.category == null && item.categoryData == null) { "새 저장물만 삽입한다" }
        val insertedId = savedItemJpaRepository.insertIfAbsent(
            memberId = item.memberId,
            originalUrl = item.originalUrl,
            normalizedUrl = item.normalizedUrl,
            normalizedUrlHash = item.normalizedUrlHash,
            platform = item.platform.name,
            collectLevel = item.collectLevel.name,
            memo = item.memo,
            title = item.title,
            ogImageUrl = item.ogImageUrl,
            processStatus = item.processStatus.name,
            now = item.createdAt,
        )
        if (insertedId != null) return SavedItemInsertResult.Inserted(insertedId)

        val existingId = checkNotNull(savedItemJpaRepository.findActiveId(item.memberId, item.normalizedUrlHash)) {
            "삽입이 충돌했는데 기존 저장물이 없다. 그 사이 삭제됐다. memberId=${item.memberId}"
        }
        return SavedItemInsertResult.AlreadySaved(existingId)
    }

    override fun findById(id: Long): SavedItem? = savedItemJpaRepository.findById(id).orElse(null)?.toDomain()

    @Transactional
    override fun saveAnalysisResult(id: Long, expectedVersion: Long, result: AnalysisResult, now: Instant): Int =
        savedItemJpaRepository.updateAnalysisResult(
            id = id,
            expectedVersion = expectedVersion,
            title = result.title,
            ogImageUrl = result.ogImageUrl,
            platform = result.platform,
            collectLevel = result.collectLevel,
            category = result.category,
            categorySource = result.categorySource,
            summary = result.summary,
            categoryData = result.categoryData,
            promptVersion = result.promptVersion,
            now = now,
        )

    @Transactional
    override fun changeCategory(id: Long, category: Category, now: Instant): Int =
        savedItemJpaRepository.updateCategory(
            id = id,
            category = category,
            source = CategorySource.USER,
            changeable = CATEGORY_CHANGEABLE,
            now = now,
        )

    @Transactional
    override fun updateProcessStatus(id: Long, status: ProcessStatus, failCode: String?, now: Instant): Int =
        savedItemJpaRepository.updateProcessStatus(id, status, failCode, now)

    @Transactional
    override fun replaceKeywords(savedItemId: Long, keywords: List<String>, now: Instant): List<SavedItemKeyword> {
        val replacements = keywords.distinct().mapIndexed { index, keyword ->
            SavedItemKeyword(savedItemId, keyword, index, now)
        }
        keywordJpaRepository.deleteBySavedItemId(savedItemId)
        return keywordJpaRepository.saveAll(replacements.map { it.toEntity() }).map { it.toDomain() }
    }

    override fun findKeywords(savedItemId: Long): List<SavedItemKeyword> =
        keywordJpaRepository.findBySavedItemIdOrderBySortOrder(savedItemId).map { it.toDomain() }

    @Transactional
    override fun saveEmbedding(embedding: SavedItemEmbedding): SavedItemEmbedding =
        embeddingJpaRepository.save(embedding.toEntity()).toDomain()

    override fun findEmbedding(savedItemId: Long): SavedItemEmbedding? =
        embeddingJpaRepository.findById(savedItemId).orElse(null)?.toDomain()

    companion object {
        /** 처리가 끝난 저장물만 카테고리를 고칠 수 있다. 처리 중에 고치면 PROCESSING 에 갇힐 수 있다. */
        private val CATEGORY_CHANGEABLE = listOf(ProcessStatus.DONE, ProcessStatus.FAILED)
    }
}
