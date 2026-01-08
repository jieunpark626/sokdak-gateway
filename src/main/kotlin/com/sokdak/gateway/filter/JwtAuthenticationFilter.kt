package com.sokdak.gateway.filter

import com.sokdak.gateway.config.GatewaySecurityProperties
import com.sokdak.gateway.exception.InvalidTokenException
import com.sokdak.gateway.exception.TokenExpiredException
import com.sokdak.gateway.util.JwtUtil
import org.slf4j.LoggerFactory
import org.springframework.cloud.gateway.filter.GatewayFilter
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory
import org.springframework.core.io.buffer.DataBuffer
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import reactor.core.publisher.Mono

@Component
class JwtAuthenticationFilter(
    private val jwtUtil: JwtUtil,
    private val gatewaySecurityProperties: GatewaySecurityProperties
) : AbstractGatewayFilterFactory<JwtAuthenticationFilter.Config>(Config::class.java) {

    private val logger = LoggerFactory.getLogger(JwtAuthenticationFilter::class.java)

    class Config

    override fun apply(config: Config): GatewayFilter {
        return GatewayFilter { exchange, chain ->
            val request = exchange.request
            val authHeader = request.headers.getFirst(HttpHeaders.AUTHORIZATION)

            logger.debug("Processing request: ${request.path}")

            if (authHeader.isNullOrBlank() || !authHeader.startsWith("Bearer ")) {
                logger.warn("Missing or invalid Authorization header")
                return@GatewayFilter unauthorizedResponse(exchange, "missing_token", "Authorization header is missing or invalid")
            }

            val token = authHeader.substring(7)

            try {
                // JWT 토큰 검증 및 사용자 ID 추출
                val userId = jwtUtil.getUserIdFromToken(token)
                logger.debug("JWT validation successful for user: $userId")

                // 요청 헤더에 사용자 ID와 Gateway 보안 토큰 추가
                val mutatedRequest = request.mutate()
                    .header("X-User-Id", userId)
                    .header("X-Gateway-Token", gatewaySecurityProperties.token)
                    .build()

                val mutatedExchange = exchange.mutate().request(mutatedRequest).build()

                chain.filter(mutatedExchange)
            } catch (e: TokenExpiredException) {
                logger.warn("Token expired: ${e.message}")
                return@GatewayFilter unauthorizedResponse(exchange, "token_expired", "Token has expired")
            } catch (e: InvalidTokenException) {
                logger.warn("Invalid token: ${e.message}")
                return@GatewayFilter unauthorizedResponse(exchange, "invalid_token", "Invalid token")
            } catch (e: Exception) {
                logger.error("Unexpected error during JWT validation", e)
                return@GatewayFilter unauthorizedResponse(exchange, "invalid_token", "Token validation failed")
            }
        }
    }

    private fun unauthorizedResponse(exchange: org.springframework.web.server.ServerWebExchange, error: String, message: String): Mono<Void> {
        val response = exchange.response
        response.statusCode = HttpStatus.UNAUTHORIZED
        response.headers.contentType = MediaType.APPLICATION_JSON

        val errorResponse = """{"error":"$error","message":"$message"}"""
        val buffer: DataBuffer = response.bufferFactory().wrap(errorResponse.toByteArray())

        return response.writeWith(Mono.just(buffer))
    }
}