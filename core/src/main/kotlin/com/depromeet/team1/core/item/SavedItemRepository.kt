package com.depromeet.team1.core.item

import java.time.Instant

interface SavedItemRepository {
    /** 삭제되지 않은 같은 링크(회원, URL 해시)가 있으면 삽입하지 않고 기존 저장물 ID 를 돌려준다. */
    fun insert(item: SavedItem): SavedItemInsertResult

    /**
     * 삭제된 저장물도 돌려준다. 워커가 [SavedItem.deletedAt] 을 보고 작업을 `ITEM_DELETED` 로 끝내려면 필요하다.
     * 사용자에게 보여 주는 조회는 [SavedItem.deletedAt] 이 있으면 없는 것으로 다룬다.
     */
    fun findById(id: Long): SavedItem?

    /**
     * 작업 시작 때 읽은 [expectedVersion] 이 그대로이고 삭제되지 않았을 때만 결과를 반영하고 version 을 올린다.
     * 갱신한 행 수(0 또는 1)를 돌려준다. 0 이면 다시 읽어 판단한다.
     */
    fun saveAnalysisResult(id: Long, expectedVersion: Long, result: AnalysisResult, now: Instant): Int

    /**
     * 사용자가 카테고리를 고친다. 처리 상태가 DONE·FAILED 이고 삭제되지 않았을 때만 바꾼다.
     * 출처는 USER, 상세 정보는 비우고 version 을 올린다. 갱신한 행 수를 돌려준다.
     *
     * 재분석 작업과 한 트랜잭션으로 부른다. 먼저 [com.depromeet.team1.core.job.ProcessingJobRepository.create] 로
     * CATEGORY_CHANGED 작업을 만들고, null 이면(진행 중인 작업이 있음) 여기서 끝낸다(409).
     * 그다음 이 메서드를 부르고 0 이면 롤백한다. 순서가 반대면 카테고리만 바뀌고 재분석 작업이 없는 상태가 생길 수 있다.
     */
    fun changeCategory(id: Long, category: Category, now: Instant): Int

    /**
     * 화면 처리 상태를 바꾼다. version 은 올리지 않는다. 삭제된 저장물은 바꾸지 않는다. 갱신한 행 수를 돌려준다.
     * 지금 상태는 검사하지 않는다. 처리 작업 전이(3, 4, 6)가 성공한 뒤에만 부른다.
     */
    fun updateProcessStatus(id: Long, status: ProcessStatus, failCode: String?, now: Instant): Int

    /** 주제어를 [keywords] 로 바꾼다. 중복은 앞의 것만 남기고, 순서는 목록 순서다. */
    fun replaceKeywords(savedItemId: Long, keywords: List<String>, now: Instant): List<SavedItemKeyword>

    fun findKeywords(savedItemId: Long): List<SavedItemKeyword>

    /** 저장물의 임베딩을 저장한다. 이미 있으면 덮어쓴다. */
    fun saveEmbedding(embedding: SavedItemEmbedding): SavedItemEmbedding

    fun findEmbedding(savedItemId: Long): SavedItemEmbedding?
}
