package com.depromeet.team1.core.item

/**
 * 링크에서 분석 재료를 받아 온다. 구현은 infra 의 `external/link` 에 둔다(`docs/architecture.md` 경계 인터페이스).
 */
interface LinkContentFetcher {
    fun fetch(url: String): FetchedLink
}

/** 수집 결과. [title]·[ogImageUrl] 은 저장하고, [author]·[description]·[body] 는 분석에만 쓴다. */
data class FetchedLink(
    /** 수집에 쓴 주소. 네이버 블로그는 바꾼 모바일 주소이고, 나머지는 받은 주소 그대로다. 내용을 못 받아도 들어간다. 분석에만 쓰고, 저장물의 주소는 바꾸지 않는다. */
    val finalUrl: String,
    val platform: Platform,
    val collectLevel: CollectLevel,
    val title: String?,
    /** 작성자. 유튜브는 채널 이름이다. */
    val author: String?,
    val description: String?,
    /** 본문. 분석에만 쓰고 저장하지 않는다. */
    val body: String?,
    /** 대표 이미지 주소. 유튜브는 썸네일이다. */
    val ogImageUrl: String?,
    /** 수집 누락. 내용을 다 받았으면 null 이다. 작업을 실패로 끝낼지는 분석이 정한다. */
    val miss: FetchMiss?,
)

/** 수집이 내용을 받지 못한 이유. */
enum class FetchMiss {
    /** 약관이나 봇 차단 때문에 요청하지 않기로 한 곳이거나, 요청하지 않는 주소 형태(영상이 아닌 유튜브 주소 등)다. */
    NOT_REQUESTED,

    /** 로그인이나 권한이 있어야 볼 수 있어 받지 못했다. */
    BLOCKED,
}
