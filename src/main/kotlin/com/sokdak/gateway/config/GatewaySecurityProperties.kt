package com.sokdak.gateway.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.stereotype.Component

@Component
@ConfigurationProperties(prefix = "gateway.security")
data class GatewaySecurityProperties(
    var token: String = ""
)