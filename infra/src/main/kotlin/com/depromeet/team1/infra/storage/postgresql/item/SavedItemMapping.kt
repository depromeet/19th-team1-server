package com.depromeet.team1.infra.storage.postgresql.item

import com.depromeet.team1.core.item.SavedItem
import com.depromeet.team1.core.item.SavedItemEmbedding
import com.depromeet.team1.core.item.SavedItemKeyword
import java.nio.ByteBuffer
import java.nio.ByteOrder

internal fun SavedItemJpaEntity.toDomain() = SavedItem(
    id = id,
    memberId = memberId,
    originalUrl = originalUrl,
    normalizedUrl = normalizedUrl,
    normalizedUrlHash = normalizedUrlHash,
    platform = platform,
    collectLevel = collectLevel,
    memo = memo,
    title = title,
    ogImageUrl = ogImageUrl,
    processStatus = processStatus,
    failCode = failCode,
    category = category,
    categorySource = categorySource,
    summary = summary,
    categoryData = categoryData,
    promptVersion = promptVersion,
    version = version,
    createdAt = createdAt,
    updatedAt = updatedAt,
    deletedAt = deletedAt,
)

internal fun SavedItemKeywordJpaEntity.toDomain() = SavedItemKeyword(
    savedItemId = savedItemId,
    keyword = keyword,
    sortOrder = sortOrder.toInt(),
    createdAt = createdAt,
)

internal fun SavedItemKeyword.toEntity() = SavedItemKeywordJpaEntity(
    id = null,
    savedItemId = savedItemId,
    keyword = keyword,
    sortOrder = sortOrder.toShort(),
    createdAt = createdAt,
)

internal fun SavedItemEmbedding.toEntity() = SavedItemEmbeddingJpaEntity(
    savedItemId = savedItemId,
    memberId = memberId,
    embedding = vector.toLittleEndianBytes(),
    dimension = dimension,
    embeddingModel = embeddingModel,
    createdAt = createdAt,
)

internal fun SavedItemEmbeddingJpaEntity.toDomain() = SavedItemEmbedding(
    savedItemId = savedItemId,
    memberId = memberId,
    vector = embedding.toFloatArray(),
    embeddingModel = embeddingModel,
    createdAt = createdAt,
)

private fun FloatArray.toLittleEndianBytes(): ByteArray {
    val buffer = ByteBuffer.allocate(size * Float.SIZE_BYTES).order(ByteOrder.LITTLE_ENDIAN)
    forEach { buffer.putFloat(it) }
    return buffer.array()
}

private fun ByteArray.toFloatArray(): FloatArray {
    val buffer = ByteBuffer.wrap(this).order(ByteOrder.LITTLE_ENDIAN).asFloatBuffer()
    return FloatArray(buffer.remaining()).also { buffer.get(it) }
}
