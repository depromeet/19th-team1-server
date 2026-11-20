package com.depromeet.team1.core.item

/**
 * 처리 작업이 저장물에 반영하는 수집·분석 결과. 주제어는 [SavedItemRepository.replaceKeywords] 로 따로 바꾼다.
 *
 * 사용자가 정한 카테고리(`category_source = USER`)를 지키려면 호출하는 쪽이 [category], [categorySource] 에 기존 값을 넣는다.
 */
data class AnalysisResult(
    val title: String?,
    val ogImageUrl: String?,
    val platform: Platform,
    val collectLevel: CollectLevel,
    val category: Category?,
    val categorySource: CategorySource?,
    val summary: String?,
    val categoryData: Map<String, String>?,
    val promptVersion: String?,
) {
    init {
        require(title == null || title.length <= SavedItem.TITLE_MAX) { "제목은 ${SavedItem.TITLE_MAX}자 이하다" }
        require(ogImageUrl == null || ogImageUrl.length <= SavedItem.OG_IMAGE_URL_MAX) {
            "이미지 주소는 ${SavedItem.OG_IMAGE_URL_MAX}자 이하다"
        }
        require(promptVersion == null || promptVersion.length <= SavedItem.CODE_MAX) {
            "프롬프트 버전은 ${SavedItem.CODE_MAX}자 이하다"
        }
        require((category == null) == (categorySource == null)) { "카테고리와 카테고리 출처는 함께 채운다" }
    }
}
