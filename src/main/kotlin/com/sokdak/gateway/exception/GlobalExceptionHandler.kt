package com.sokdak.gateway.exception

import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.boot.web.reactive.error.ErrorWebExceptionHandler
import org.springframework.core.annotation.Order
import org.springframework.core.io.buffer.DataBuffer
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.server.ResponseStatusException
import org.springframework.web.server.ServerWebExchange
import reactor.core.publisher.Mono

@Component
@Order(-1)
class GlobalExceptionHandler : ErrorWebExceptionHandler {

    private val logger = LoggerFactory.getLogger(GlobalExceptionHandler::class.java)
    private val objectMapper = ObjectMapper()

    override fun handle(exchange: ServerWebExchange, ex: Throwable): Mono<Void> {
        val response = exchange.response

        logger.error("Error occurred: ${ex.message}", ex)

        val (status, errorCode, message) = when (ex) {
            is TokenExpiredException -> Triple(HttpStatus.UNAUTHORIZED, "token_expired", ex.message ?: "Token has expired")
            is InvalidTokenException -> Triple(HttpStatus.UNAUTHORIZED, "invalid_token", ex.message ?: "Invalid token")
            is ResponseStatusException -> Triple(ex.statusCode, "service_error", ex.reason ?: "Service error occurred")
            else -> {
                // 라우팅 실패 등 기타 에러
                if (ex.message?.contains("Connection refused") == true || ex.message?.contains("503") == true) {
                    Triple(HttpStatus.SERVICE_UNAVAILABLE, "service_unavailable", "Service is temporarily unavailable")
                } else {
                    Triple(HttpStatus.INTERNAL_SERVER_ERROR, "internal_error", "Internal server error")
                }
            }
        }

        response.statusCode = status
        response.headers.contentType = MediaType.APPLICATION_JSON

        val errorResponse = mapOf(
            "error" to errorCode,
            "message" to message,
            "status" to status.value()
        )

        val errorJson = objectMapper.writeValueAsString(errorResponse)
        val buffer: DataBuffer = response.bufferFactory().wrap(errorJson.toByteArray())

        return response.writeWith(Mono.just(buffer))
    }
}