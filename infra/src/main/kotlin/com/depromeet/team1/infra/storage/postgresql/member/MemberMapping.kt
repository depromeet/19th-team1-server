package com.depromeet.team1.infra.storage.postgresql.member

import com.depromeet.team1.core.member.Member
import com.depromeet.team1.core.member.MemberCredential
import com.depromeet.team1.core.member.MemberInterestCategory

internal fun Member.toEntity() = MemberJpaEntity(
    id = id,
    nickname = nickname,
    email = email,
    acquisitionChannel = acquisitionChannel,
    status = status,
    createdAt = createdAt,
    updatedAt = updatedAt,
    withdrawnAt = withdrawnAt,
)

internal fun MemberJpaEntity.toDomain() = Member(
    id = id,
    nickname = nickname,
    email = email,
    acquisitionChannel = acquisitionChannel,
    status = status,
    createdAt = createdAt,
    updatedAt = updatedAt,
    withdrawnAt = withdrawnAt,
)

internal fun MemberCredential.toEntity() = MemberCredentialJpaEntity(
    id = id,
    memberId = memberId,
    provider = provider,
    providerUserId = providerUserId,
    createdAt = createdAt,
    revokedAt = revokedAt,
)

internal fun MemberCredentialJpaEntity.toDomain() = MemberCredential(
    id = id,
    memberId = memberId,
    provider = provider,
    providerUserId = providerUserId,
    createdAt = createdAt,
    revokedAt = revokedAt,
)

internal fun MemberInterestCategory.toEntity() = MemberInterestCategoryJpaEntity(
    memberId = memberId,
    category = category,
    createdAt = createdAt,
)

internal fun MemberInterestCategoryJpaEntity.toDomain() = MemberInterestCategory(
    memberId = memberId,
    category = category,
    createdAt = createdAt,
)
