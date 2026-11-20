package com.depromeet.team1.core.metric

import java.time.Instant

/**
 * 지표용 행동 이벤트(docs/metrics.md). `analytics` 스키마에 쌓고 도메인 코드는 읽지 않는다.
 * [properties] 에는 정해진 코드와 숫자만 넣는다. URL·제목·메모·질문 원문을 넣지 않는다.
 */
data class BehaviorEvent(
    val eventName: BehaviorEventName,
    /** 단순 값. 도메인 테이블과 조인하지 않는다. */
    val memberId: Long,
    val properties: Map<String, Any>,
    val occurredAt: Instant,
)

/** [code] 가 DB 에 저장되는 이벤트 이름이다. */
enum class BehaviorEventName(val code: String) {
    USER_ACTIVE("user_active"),
    HOME_VIEWED("home_viewed"),
    HOME_CARD_CLICKED("home_card_clicked"),
    ITEM_VIEWED("item_viewed"),
    ITEM_ORIGINAL_OPENED("item_original_opened"),
    CHAT_QUESTION_SENT("chat_question_sent"),
}
