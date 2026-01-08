package com.sokdak.gateway.filter

import com.sokdak.gateway.config.GatewaySecurityProperties
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.cloud.gateway.filter.GatewayFilter
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory
import org.springframework.core.io.buffer.DataBuffer
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.WebClientResponseException
import reactor.core.publisher.Mono

@Component
class UsageLimitCheckFilter(
    private val gatewaySecurityProperties: GatewaySecurityProperties,
    @Value("\${SOKDAK_SERVER_URL:http://localhost:8080}")
    private val sokdakServerUrl: String
) : AbstractGatewayFilterFactory<UsageLimitCheckFilter.Config>(Config::class.java) {

    private val logger = LoggerFactory.getLogger(UsageLimitCheckFilter::class.java)

    private val webClient by lazy {
        WebClient.builder()
            .baseUrl(sokdakServerUrl)
            .build()
    }

    class Config {
        var action: String = "AI_CHAT" // 기본값
    }

    override fun apply(config: Config): GatewayFilter {
        return GatewayFilter { exchange, chain ->
            val request = exchange.request
            val userId = request.headers.getFirst("X-User-Id")

            if (userId.isNullOrBlank()) {
                logger.warn("Missing X-User-Id header for usage limit check")
                return@GatewayFilter errorResponse(
                    exchange,
                    HttpStatus.BAD_REQUEST,
                    "missing_user_id",
                    "User ID is required"
                )
            }

            logger.debug("Checking usage limit for user: $userId, action: ${config.action}")

            // sokdak 서버의 /users/{userId}/limits/consume 호출
            webClient.post()
                .uri("/users/$userId/limits/consume")
                .header("X-Gateway-Token", gatewaySecurityProperties.token)
                .header("X-User-Id", userId)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(
                    mapOf(
                        "action" to config.action,
                        "count" to 1
                    )
                )
                .retrieve()
                .bodyToMono(String::class.java)
                .flatMap {
                    logger.debug("Usage limit check passed for user: $userId, action: ${config.action}")
                    // 사용량 체크 성공 시 원래 요청 진행
                    chain.filter(exchange)
                }
                .onErrorResume { error ->
                    when (error) {
                        is WebClientResponseException -> {
                            logger.warn("Usage limit check failed for user: $userId, status: ${error.statusCode}")
                            when (error.statusCode) {
                                HttpStatus.TOO_MANY_REQUESTS -> {
                                    errorResponse(
                                        exchange,
                                        HttpStatus.TOO_MANY_REQUESTS,
                                        "usage_limit_exceeded",
                                        "Usage limit exceeded. Please try again later."
                                    )
                                }

                                HttpStatus.NOT_FOUND -> {
                                    errorResponse(
                                        exchange,
                                        HttpStatus.NOT_FOUND,
                                        "user_not_found",
                                        "User not found"
                                    )
                                }

                                else -> {
                                    errorResponse(
                                        exchange,
                                        HttpStatus.INTERNAL_SERVER_ERROR,
                                        "usage_check_failed",
                                        "Failed to check usage limit"
                                    )
                                }
                            }
                        }

                        else -> {
                            logger.error("Unexpected error during usage limit check", error)
                            errorResponse(
                                exchange,
                                HttpStatus.INTERNAL_SERVER_ERROR,
                                "usage_check_error",
                                "An error occurred while checking usage limit"
                            )
                        }
                    }
                }
        }
    }

    private fun errorResponse(
        exchange: org.springframework.web.server.ServerWebExchange,
        status: HttpStatus,
        error: String,
        message: String
    ): Mono<Void> {
        val response = exchange.response
        response.statusCode = status
        response.headers.contentType = MediaType.APPLICATION_JSON

        val errorResponse = """{"error":"$error","message":"$message"}"""
        val buffer: DataBuffer = response.bufferFactory().wrap(errorResponse.toByteArray())

        return response.writeWith(Mono.just(buffer))
    }
}