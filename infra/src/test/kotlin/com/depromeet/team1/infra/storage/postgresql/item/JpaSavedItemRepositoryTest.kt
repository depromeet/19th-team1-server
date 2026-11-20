package com.depromeet.team1.infra.storage.postgresql.item

import com.depromeet.team1.core.item.AnalysisResult
import com.depromeet.team1.core.item.Category
import com.depromeet.team1.core.item.CategorySource
import com.depromeet.team1.core.item.CollectLevel
import com.depromeet.team1.core.item.Platform
import com.depromeet.team1.core.item.ProcessStatus
import com.depromeet.team1.core.item.SavedItem
import com.depromeet.team1.core.item.SavedItemEmbedding
import com.depromeet.team1.core.item.SavedItemInsertResult
import com.depromeet.team1.core.item.SavedItemRepository
import com.depromeet.team1.infra.storage.postgresql.StorageTest
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.time.Instant
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class JpaSavedItemRepositoryTest : StorageTest() {
    @Autowired
    private lateinit var savedItemRepository: SavedItemRepository

    @Test
    fun `새 링크를 삽입하면 새 ID 를 돌려준다`() {
        val result = savedItemRepository.insert(newItem())

        assertIs<SavedItemInsertResult.Inserted>(result)
        assertEquals(ProcessStatus.PENDING, savedItemRepository.findById(result.id)?.processStatus)
    }

    @Test
    fun `같은 회원이 같은 링크를 다시 삽입하면 기존 저장물을 돌려준다`() {
        val first = savedItemRepository.insert(newItem())

        val second = savedItemRepository.insert(newItem())

        assertEquals(SavedItemInsertResult.AlreadySaved(first.id), second)
    }

    @Test
    fun `다른 회원은 같은 링크를 따로 저장한다`() {
        val first = savedItemRepository.insert(newItem(memberId = 1))

        val second = savedItemRepository.insert(newItem(memberId = 2))

        assertIs<SavedItemInsertResult.Inserted>(second)
        assertNotEquals(first.id, second.id)
    }

    @Test
    fun `삭제한 링크를 다시 저장하면 새 행을 만든다`() {
        val first = savedItemRepository.insert(newItem())
        jdbcTemplate.update("update saved_item set deleted_at = now() where id = ?", first.id)

        val second = savedItemRepository.insert(newItem())

        assertIs<SavedItemInsertResult.Inserted>(second)
        assertNotEquals(first.id, second.id)
    }

    @Test
    fun `읽은 version 이 그대로면 분석 결과를 반영하고 version 을 올린다`() {
        val id = savedItemRepository.insert(newItem()).id
        val later = NOW.plusSeconds(60)

        val updated = savedItemRepository.saveAnalysisResult(id, 0, analysisResult(), later)

        assertEquals(1, updated)
        val item = assertNotNull(savedItemRepository.findById(id))
        assertEquals(1, item.version)
        assertEquals(Category.PLACE, item.category)
        assertEquals(mapOf("place_name" to "성수 카페"), item.categoryData)
        assertEquals(later, item.updatedAt)
    }

    @Test
    fun `version 이 어긋나면 분석 결과를 반영하지 않는다`() {
        val id = savedItemRepository.insert(newItem()).id
        savedItemRepository.saveAnalysisResult(id, 0, analysisResult(), NOW)

        val updated = savedItemRepository.saveAnalysisResult(id, 0, analysisResult(summary = "늦은 결과"), NOW)

        assertEquals(0, updated)
        assertEquals("요약", savedItemRepository.findById(id)?.summary)
    }

    @Test
    fun `삭제된 저장물에는 분석 결과를 반영하지 않는다`() {
        val id = savedItemRepository.insert(newItem()).id
        jdbcTemplate.update("update saved_item set deleted_at = now() where id = ?", id)

        val updated = savedItemRepository.saveAnalysisResult(id, 0, analysisResult(), NOW)

        assertEquals(0, updated)
    }

    @Test
    fun `카테고리를 고치면 출처는 USER 가 되고 상세 정보는 비고 version 이 오른다`() {
        val id = savedItemRepository.insert(newItem()).id
        savedItemRepository.saveAnalysisResult(id, 0, analysisResult(), NOW)
        savedItemRepository.updateProcessStatus(id, ProcessStatus.DONE, null, NOW)

        val updated = savedItemRepository.changeCategory(id, Category.SHOPPING, NOW)

        assertEquals(1, updated)
        val item = assertNotNull(savedItemRepository.findById(id))
        assertEquals(Category.SHOPPING, item.category)
        assertEquals(CategorySource.USER, item.categorySource)
        assertNull(item.categoryData)
        assertEquals(2, item.version)
    }

    @Test
    fun `처리 중인 저장물은 카테고리를 고칠 수 없다`() {
        val id = savedItemRepository.insert(newItem()).id
        savedItemRepository.updateProcessStatus(id, ProcessStatus.PROCESSING, null, NOW)

        val updated = savedItemRepository.changeCategory(id, Category.SHOPPING, NOW)

        assertEquals(0, updated)
    }

    @Test
    fun `화면 처리 상태를 바꿔도 version 은 그대로다`() {
        val id = savedItemRepository.insert(newItem()).id

        savedItemRepository.updateProcessStatus(id, ProcessStatus.FAILED, "TIMEOUT", NOW)

        val item = assertNotNull(savedItemRepository.findById(id))
        assertEquals(ProcessStatus.FAILED, item.processStatus)
        assertEquals("TIMEOUT", item.failCode)
        assertEquals(0, item.version)
    }

    @Test
    fun `주제어를 교체하면 겹치는 주제어가 있어도 새 목록 순서로 바뀐다`() {
        val id = savedItemRepository.insert(newItem()).id
        savedItemRepository.replaceKeywords(id, listOf("카페", "성수"), NOW)

        savedItemRepository.replaceKeywords(id, listOf("성수", "디저트", "성수"), NOW)

        val keywords = savedItemRepository.findKeywords(id)
        assertEquals(listOf("성수", "디저트"), keywords.map { it.keyword })
        assertEquals(listOf(0, 1), keywords.map { it.sortOrder })
    }

    @Test
    fun `임베딩을 저장하고 같은 벡터로 읽는다`() {
        val id = savedItemRepository.insert(newItem()).id
        val vector = floatArrayOf(0.1f, -2.5f, 3.0f)

        savedItemRepository.saveEmbedding(SavedItemEmbedding(id, 1, vector, "embed-model", NOW))

        val found = assertNotNull(savedItemRepository.findEmbedding(id))
        assertContentEquals(vector, found.vector)
        assertEquals(3, found.dimension)
    }

    @Test
    fun `임베딩을 다시 만들면 덮어쓴다`() {
        val id = savedItemRepository.insert(newItem()).id
        savedItemRepository.saveEmbedding(SavedItemEmbedding(id, 1, floatArrayOf(1f), "old", NOW))

        savedItemRepository.saveEmbedding(SavedItemEmbedding(id, 1, floatArrayOf(2f, 3f), "new", NOW))

        val found = assertNotNull(savedItemRepository.findEmbedding(id))
        assertContentEquals(floatArrayOf(2f, 3f), found.vector)
        assertEquals("new", found.embeddingModel)
    }

    private fun newItem(memberId: Long = 1, createdAt: Instant = NOW): SavedItem = SavedItem.create(
        memberId = memberId,
        originalUrl = "https://example.com/a?utm_source=x",
        normalizedUrl = "https://example.com/a",
        normalizedUrlHash = "a".repeat(64),
        platform = Platform.OTHER,
        collectLevel = CollectLevel.AUTO,
        memo = null,
        now = createdAt,
    )

    private fun analysisResult(summary: String = "요약") = AnalysisResult(
        title = "제목",
        ogImageUrl = null,
        platform = Platform.NAVER_BLOG,
        collectLevel = CollectLevel.AUTO,
        category = Category.PLACE,
        categorySource = CategorySource.AI,
        summary = summary,
        categoryData = mapOf("place_name" to "성수 카페"),
        promptVersion = "v1",
    )
}
