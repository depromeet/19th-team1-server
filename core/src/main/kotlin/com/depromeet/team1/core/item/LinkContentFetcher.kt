package com.depromeet.team1.core.item

/**
 * 링크에서 분석 재료를 받아 온다. 구현은 infra 의 `external/link` 에 둔다(`docs/architecture.md` 경계 인터페이스).
 */
interface LinkContentFetcher {
    fun fetch(url: String): FetchedLink
}

/** 수집 결과. [title]·[ogImageUrl] 은 저장하고, [author] 는 분석에만 쓴다. */
data class FetchedLink(
    val platform: Platform,
    val collectLevel: CollectLevel,
    val title: String?,
    /** 작성자. 유튜브는 채널 이름이다. */
    val author: String?,
    /** 대표 이미지 주소. 유튜브는 썸네일이다. */
    val ogImageUrl: String?,
)
