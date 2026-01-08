package com.sokdak.gateway.util

import com.sokdak.gateway.config.JwtProperties
import com.sokdak.gateway.exception.InvalidTokenException
import com.sokdak.gateway.exception.TokenExpiredException
import io.jsonwebtoken.ExpiredJwtException
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.nio.charset.StandardCharsets
import javax.crypto.SecretKey

@Component
class JwtUtil(
    private val jwtProperties: JwtProperties
) {
    private val logger = LoggerFactory.getLogger(JwtUtil::class.java)

    private val secretKey: SecretKey by lazy {
        Keys.hmacShaKeyFor(jwtProperties.secret.toByteArray(StandardCharsets.UTF_8))
    }

    /**
     * JWT 토큰에서 사용자 ID를 추출합니다.
     * @param token JWT 토큰
     * @return 사용자 ID
     * @throws TokenExpiredException 토큰이 만료된 경우
     * @throws InvalidTokenException 토큰이 유효하지 않은 경우
     */
    fun getUserIdFromToken(token: String): String {
        try {
            val claims = Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .payload

            // type claim이 "refresh"인 경우 access token이 아니므로 거부
            val tokenType = claims["type"] as? String
            if (tokenType == "refresh") {
                logger.warn("Refresh token used as access token")
                throw InvalidTokenException("Refresh token cannot be used for authentication")
            }

            // subject에서 userId 추출
            val userId = claims.subject
            if (userId.isNullOrBlank()) {
                logger.warn("Token subject is null or blank")
                throw InvalidTokenException("Token subject is missing")
            }

            return userId
        } catch (e: ExpiredJwtException) {
            logger.warn("Token expired: ${e.message}")
            throw TokenExpiredException("Token has expired")
        } catch (e: Exception) {
            logger.error("Token validation failed: ${e.message}", e)
            throw InvalidTokenException("Invalid token: ${e.message}")
        }
    }

    /**
     * JWT 토큰의 유효성을 검증합니다.
     * @param token JWT 토큰
     * @return 유효성 여부
     */
    fun validateToken(token: String): Boolean {
        return try {
            getUserIdFromToken(token)
            true
        } catch (e: Exception) {
            false
        }
    }
}