package com.depromeet.team1.core.item

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class CategoryTest {
    @Test
    fun `고정 카테고리는 8개다`() {
        assertEquals(8, Category.entries.size)
    }

    @Test
    fun `상세 정보 키는 카테고리 사이에서 겹치지 않는다`() {
        val keys = Category.entries.flatMap { category -> category.attributes.map { it.key } }

        assertEquals(keys.size, keys.toSet().size)
    }
}
