package com.depromeet.team1.core.item

import java.time.Instant

interface SavedItemRepository {
    /** 삭제되지 않은 같은 링크(회원, URL 해시)가 있으면 삽입하지 않고 기존 저장물 ID 를 돌려준다. */
    fun insert(item: SavedItem): SavedItemInsertResult

    /** 삭제된 저장물도 돌려준다. */
    fun findById(id: Long): SavedItem?

    /**
     * 작업 시작 때 읽은 [expectedVersion] 이 그대로이고 삭제되지 않았을 때만 결과를 반영하고 version 을 올린다.
     * 갱신한 행 수(0 또는 1)를 돌려준다. 0 이면 다시 읽어 판단한다.
     */
    fun saveAnalysisResult(id: Long, expectedVersion: Long, result: AnalysisResult, now: Instant): Int

    /**
     * 사용자가 카테고리를 고친다. 처리 상태가 DONE·FAILED 이고 삭제되지 않았을 때만 바꾼다.
     * 출처는 USER, 상세 정보는 비우고 version 을 올린다. 갱신한 행 수를 돌려준다.
     */
    fun changeCategory(id: Long, category: Category, now: Instant): Int

    /** 화면 처리 상태를 바꾼다. version 은 올리지 않는다. 삭제된 저장물은 바꾸지 않는다. 갱신한 행 수를 돌려준다. */
    fun updateProcessStatus(id: Long, status: ProcessStatus, failCode: String?, now: Instant): Int

    /** 주제어를 [keywords] 로 바꾼다. 중복은 앞의 것만 남기고, 순서는 목록 순서다. */
    fun replaceKeywords(savedItemId: Long, keywords: List<String>, now: Instant): List<SavedItemKeyword>

    fun findKeywords(savedItemId: Long): List<SavedItemKeyword>

    /** 저장물의 임베딩을 저장한다. 이미 있으면 덮어쓴다. */
    fun saveEmbedding(embedding: SavedItemEmbedding): SavedItemEmbedding

    fun findEmbedding(savedItemId: Long): SavedItemEmbedding?
}
