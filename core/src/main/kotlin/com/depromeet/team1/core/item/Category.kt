package com.depromeet.team1.core.item

/**
 * 고정 카테고리. 테이블 없이 `saved_item.category` 에 enum 이름을 저장한다.
 *
 * - enum 이름은 배포 후 바꾸지 않는다. 바꾸거나 빼려면 데이터 마이그레이션이 먼저다.
 * - [displayName], [criteria], [attributes] 는 자유롭게 바꿔도 된다.
 * - 이름·분류 기준·속성은 디자인팀 초안(확정 전)이다. 확정되면 고친다.
 */
enum class Category(
    val displayName: String,
    /** 분류 기준. LLM 분석 프롬프트에 넣는다. */
    val criteria: String,
    /** 이 카테고리의 상세 정보(`category_data`) 키 정의. */
    val attributes: List<CategoryAttribute>,
) {
    PLACE(
        "맛집·장소",
        "식당, 카페, 여행지, 가 볼 곳",
        listOf(
            CategoryAttribute("place_name", "장소명"),
            CategoryAttribute("location", "위치"),
            CategoryAttribute("place_type", "장소 유형", listOf("식당", "카페", "관광지", "기타")),
        ),
    ),
    SHOPPING(
        "제품·쇼핑",
        "사고 싶은 제품, 상품 페이지",
        listOf(
            CategoryAttribute("product_name", "제품명"),
            CategoryAttribute("product_type", "제품 유형"),
            CategoryAttribute("brand", "브랜드명"),
        ),
    ),
    LIFESTYLE(
        "레시피·생활",
        "요리, 청소, 정리·수납, 세탁, 수리·관리, 건강·생활 방법",
        listOf(
            CategoryAttribute(
                "usage_type",
                "활용 유형",
                listOf("요리", "청소", "정리·수납", "세탁", "수리·관리", "건강·생활", "기타"),
            ),
        ),
    ),
    WORK_STUDY(
        "업무·학습",
        "방법 습득, 업무 수행, 실습·참고용 실용 자료",
        listOf(CategoryAttribute("material_type", "자료 유형", listOf("가이드", "템플릿", "강의", "기타"))),
    ),
    CONTENT(
        "콘텐츠·아티클",
        "정보 습득, 시사·트렌드, 견해·이야기 중심 콘텐츠",
        listOf(CategoryAttribute("content_type", "콘텐츠 유형", listOf("기사", "영상", "블로그", "기타"))),
    ),
    DESIGN(
        "디자인·아이디어",
        "디자인 레퍼런스, 아이디어",
        listOf(CategoryAttribute("reference_type", "레퍼런스 유형", listOf("UI", "그래픽", "브랜딩", "기타"))),
    ),
    BENEFIT(
        "쿠폰·티켓·기프티콘",
        "쿠폰, 할인·이벤트, 티켓, 기프티콘",
        listOf(
            CategoryAttribute("benefit_name", "혜택(이벤트)명"),
            CategoryAttribute("benefit_type", "유형", listOf("쿠폰", "티켓", "기프티콘")),
            CategoryAttribute("valid_until", "유효기간"),
        ),
    ),
    ETC("기타", "위 어디에도 맞지 않는 것", emptyList()),
}

/** 카테고리별 상세 정보의 키 하나. [choices] 가 비어 있으면 자유 텍스트다. */
data class CategoryAttribute(val key: String, val label: String, val choices: List<String> = emptyList())
