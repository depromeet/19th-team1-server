package com.depromeet.team1.core.item

import org.junit.jupiter.api.Test
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class SavedItemTest {
    @Test
    fun `새 저장물은 처리 대기 상태이고 분석 결과가 비어 있다`() {
        val item = newItem()

        assertEquals(ProcessStatus.PENDING, item.processStatus)
        assertEquals(0, item.version)
        assertNull(item.category)
    }

    @Test
    fun `URL 해시가 16진수 64자가 아니면 만들 수 없다`() {
        assertFailsWith<IllegalArgumentException> { newItem(hash = "abc") }
    }

    @Test
    fun `메모가 200자를 넘으면 만들 수 없다`() {
        assertFailsWith<IllegalArgumentException> { newItem(memo = "가".repeat(201)) }
    }

    @Test
    fun `카테고리 없이 카테고리 출처만 있을 수 없다`() {
        assertFailsWith<IllegalArgumentException> { newItem().copy(categorySource = CategorySource.AI) }
    }

    @Test
    fun `제목이 500자를 넘는 분석 결과는 만들 수 없다`() {
        assertFailsWith<IllegalArgumentException> {
            AnalysisResult("가".repeat(501), null, Platform.OTHER, CollectLevel.AUTO, null, null, null, null, null)
        }
    }

    @Test
    fun `주제어가 50자를 넘으면 만들 수 없다`() {
        assertFailsWith<IllegalArgumentException> { SavedItemKeyword(1, "가".repeat(51), 0, NOW) }
    }

    @Test
    fun `임베딩 차원은 벡터 길이다`() {
        val embedding = SavedItemEmbedding(1, 1, floatArrayOf(1f, 2f), "model", NOW)

        assertEquals(2, embedding.dimension)
    }

    private fun newItem(hash: String = "0".repeat(64), memo: String? = null) = SavedItem.create(
        memberId = 1,
        originalUrl = "https://example.com",
        normalizedUrl = "https://example.com",
        normalizedUrlHash = hash,
        platform = Platform.OTHER,
        collectLevel = CollectLevel.LINK_MEMO,
        memo = memo,
        now = NOW,
    )

    companion object {
        private val NOW = Instant.parse("2026-11-21T03:00:00Z")
    }
}
