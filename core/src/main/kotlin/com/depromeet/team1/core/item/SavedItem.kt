package com.depromeet.team1.core.item

import java.time.Instant

/**
 * 사용자가 저장한 링크 한 건과 수집·분석 결과.
 *
 * 분석 결과 저장과 카테고리 수정은 [SavedItemRepository] 의 조건부 UPDATE 로 한다. 이 모델을 다시 저장하지 않는다.
 */
data class SavedItem(
    val id: Long?,
    val memberId: Long,
    val originalUrl: String,
    val normalizedUrl: String,
    /** [normalizedUrl] 의 SHA-256 (16진수 64자). */
    val normalizedUrlHash: String,
    val platform: Platform,
    val collectLevel: CollectLevel,
    val memo: String?,
    val title: String?,
    val ogImageUrl: String?,
    val processStatus: ProcessStatus,
    val failCode: String?,
    val category: Category?,
    val categorySource: CategorySource?,
    val summary: String?,
    val categoryData: Map<String, String>?,
    val promptVersion: String?,
    val version: Long,
    val createdAt: Instant,
    val updatedAt: Instant,
    val deletedAt: Instant?,
) {
    init {
        require(originalUrl.isNotBlank()) { "원본 URL 은 비어 있을 수 없다" }
        require(normalizedUrl.isNotBlank()) { "정규화 URL 은 비어 있을 수 없다" }
        require(HASH_PATTERN.matches(normalizedUrlHash)) { "URL 해시는 16진수 64자다" }
        require(memo == null || memo.length <= MEMO_MAX) { "메모는 ${MEMO_MAX}자 이하다" }
        require(title == null || title.length <= TITLE_MAX) { "제목은 ${TITLE_MAX}자 이하다" }
        require(ogImageUrl == null || ogImageUrl.length <= OG_IMAGE_URL_MAX) {
            "이미지 주소는 ${OG_IMAGE_URL_MAX}자 이하다"
        }
        require(failCode == null || failCode.length <= CODE_MAX) { "실패 코드는 ${CODE_MAX}자 이하다" }
        require(promptVersion == null || promptVersion.length <= CODE_MAX) { "프롬프트 버전은 ${CODE_MAX}자 이하다" }
        require((category == null) == (categorySource == null)) { "카테고리와 카테고리 출처는 함께 채운다" }
        require(version >= 0) { "version 은 0 이상이다" }
    }

    companion object {
        const val MEMO_MAX = 200
        const val TITLE_MAX = 500
        const val OG_IMAGE_URL_MAX = 2048
        const val CODE_MAX = 30
        private val HASH_PATTERN = Regex("^[0-9a-f]{64}$")

        /** 저장 요청에서 만든다. 처리 상태는 PENDING, 분석 결과는 비어 있다. */
        fun create(
            memberId: Long,
            originalUrl: String,
            normalizedUrl: String,
            normalizedUrlHash: String,
            platform: Platform,
            collectLevel: CollectLevel,
            memo: String?,
            now: Instant,
        ): SavedItem = SavedItem(
            id = null,
            memberId = memberId,
            originalUrl = originalUrl,
            normalizedUrl = normalizedUrl,
            normalizedUrlHash = normalizedUrlHash,
            platform = platform,
            collectLevel = collectLevel,
            memo = memo,
            title = null,
            ogImageUrl = null,
            processStatus = ProcessStatus.PENDING,
            failCode = null,
            category = null,
            categorySource = null,
            summary = null,
            categoryData = null,
            promptVersion = null,
            version = 0,
            createdAt = now,
            updatedAt = now,
            deletedAt = null,
        )
    }
}

enum class Platform { YOUTUBE, BLOG, NOTION, OTHER }

/** 링크에서 얼마나 받는지(scope.md "플랫폼별 수집 수준"). 플랫폼이 아니라 수집 경로로 정해서, 같은 플랫폼도 주소에 따라 다르다. */
enum class CollectLevel { AUTO, PARTIAL, LINK_MEMO }

/** 화면에 보이는 처리 상태. 최초 처리와 재시도만 반영한다. */
enum class ProcessStatus { PENDING, PROCESSING, DONE, FAILED }

enum class CategorySource { AI, USER }
