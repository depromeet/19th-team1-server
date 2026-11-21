package com.depromeet.team1.infra.security.jwt

import java.security.SecureRandom
import java.util.Base64
import javax.crypto.SecretKey
import javax.crypto.spec.SecretKeySpec

/** 액세스 토큰 서명 키(HS256). 발급과 검증이 같은 키를 쓴다. */
class JwtSigningKey private constructor(val secretKey: SecretKey) {
    companion object {
        /** HS256 은 256비트 이상 키를 쓴다. */
        const val MIN_BYTES = 32
        private const val ALGORITHM = "HmacSHA256"

        fun fromBase64(secret: String): JwtSigningKey {
            val bytes = Base64.getDecoder().decode(secret.trim())
            require(bytes.size >= MIN_BYTES) { "JWT_SECRET 은 Base64 로 ${MIN_BYTES}바이트 이상이어야 한다" }
            return JwtSigningKey(SecretKeySpec(bytes, ALGORITHM))
        }

        /** local 에서 키가 없을 때 쓰는 임시 키. 재시작하면 바뀌어 이전 토큰은 검증되지 않는다. */
        fun random(): JwtSigningKey =
            JwtSigningKey(SecretKeySpec(ByteArray(MIN_BYTES).also(SecureRandom()::nextBytes), ALGORITHM))
    }
}
