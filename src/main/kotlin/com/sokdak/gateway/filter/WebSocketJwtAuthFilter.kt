package com.sokdak.gateway.filter

import com.sokdak.gateway.config.GatewaySecurityProperties
import com.sokdak.gateway.util.JwtUtil
import org.slf4j.LoggerFactory
import org.springframework.cloud.gateway.filter.GatewayFilter
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory
import org.springframework.core.io.buffer.DataBuffer
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import reactor.core.publisher.Mono

@Component
class WebSocketJwtAuthFilter(
    private val jwtUtil: JwtUtil,
    private val gatewaySecurityProperties: GatewaySecurityProperties
) : AbstractGatewayFilterFactory<WebSocketJwtAuthFilter.Config>(Config::class.java) {

    private val logger = LoggerFactory.getLogger(WebSocketJwtAuthFilter::class.java)

    class Config

    override fun apply(config: Config): GatewayFilter {
        return GatewayFilter { exchange, chain ->
            val request = exchange.request
            val token = request.queryParams.getFirst("token")

            if (token.isNullOrBlank()) {
                logger.warn("WebSocket connection missing token query parameter")
                return@GatewayFilter unauthorizedResponse(exchange, "missing_token", "Token query parameter is required")
            }

            try {
                val userId = jwtUtil.getUserIdFromToken(token)
                logger.debug("WebSocket JWT validation successful for user: $userId")

                val mutatedRequest = request.mutate()
                    .header("X-User-Id", userId)
                    .header("X-Gateway-Token", gatewaySecurityProperties.token)
                    .build()

                val mutatedExchange = exchange.mutate().request(mutatedRequest).build()
                chain.filter(mutatedExchange)
            } catch (e: Exception) {
                logger.warn("WebSocket JWT validation failed: ${e.message}")
                return@GatewayFilter unauthorizedResponse(exchange, "invalid_token", "Token validation failed")
            }
        }
    }

    private fun unauthorizedResponse(
        exchange: org.springframework.web.server.ServerWebExchange,
        error: String,
        message: String
    ): Mono<Void> {
        val response = exchange.response
        response.statusCode = HttpStatus.UNAUTHORIZED
        response.headers.contentType = MediaType.APPLICATION_JSON

        val errorResponse = """{"error":"$error","message":"$message"}"""
        val buffer: DataBuffer = response.bufferFactory().wrap(errorResponse.toByteArray())

        return response.writeWith(Mono.just(buffer))
    }
}
