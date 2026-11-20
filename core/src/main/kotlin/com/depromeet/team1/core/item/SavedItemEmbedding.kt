package com.depromeet.team1.core.item

import java.time.Instant

/** 저장물의 검색 벡터. 저장물당 하나이고, 다시 만들면 덮어쓴다. 유사도는 앱에서 계산한다. */
class SavedItemEmbedding(
    val savedItemId: Long,
    /** 회원 범위로 유사도를 계산하려고 함께 보관한다. */
    val memberId: Long,
    val vector: FloatArray,
    val embeddingModel: String,
    val createdAt: Instant,
) {
    init {
        require(vector.isNotEmpty()) { "벡터는 비어 있을 수 없다" }
        require(embeddingModel.isNotBlank() && embeddingModel.length <= MODEL_MAX) { "모델 이름은 1~${MODEL_MAX}자다" }
    }

    val dimension: Int get() = vector.size

    companion object {
        const val MODEL_MAX = 50
    }
}
