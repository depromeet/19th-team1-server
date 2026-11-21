package com.depromeet.team1.infra.security.jwt

import com.depromeet.team1.core.member.AccessToken
import com.depromeet.team1.core.member.AccessTokenIssuer
import org.springframework.security.oauth2.jose.jws.MacAlgorithm
import org.springframework.security.oauth2.jwt.JwsHeader
import org.springframework.security.oauth2.jwt.JwtClaimsSet
import org.springframework.security.oauth2.jwt.JwtEncoder
import org.springframework.security.oauth2.jwt.JwtEncoderParameters
import java.time.Instant

/** 액세스 토큰을 HS256 JWT 로 발급한다. 담는 값은 회원 번호(sub), 발급·만료 시각뿐이다. */
class JwtAccessTokenIssuer(private val encoder: JwtEncoder, private val properties: JwtProperties) : AccessTokenIssuer {
    override fun issue(memberId: Long, issuedAt: Instant): AccessToken {
        val expiresAt = issuedAt.plus(properties.accessTokenValidity)
        val claims = JwtClaimsSet.builder()
            .subject(memberId.toString())
            .issuedAt(issuedAt)
            .expiresAt(expiresAt)
            .build()
        val header = JwsHeader.with(MacAlgorithm.HS256).build()
        val jwt = encoder.encode(JwtEncoderParameters.from(header, claims))
        return AccessToken(jwt.tokenValue, expiresAt)
    }
}
