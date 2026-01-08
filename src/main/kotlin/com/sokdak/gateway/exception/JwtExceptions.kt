package com.sokdak.gateway.exception

class TokenExpiredException(message: String) : RuntimeException(message)

class InvalidTokenException(message: String) : RuntimeException(message)