package com.sokdak.gateway

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.ConfigurationPropertiesScan
import org.springframework.boot.runApplication

@SpringBootApplication
@ConfigurationPropertiesScan
class SokdakGatewayApplication

fun main(args: Array<String>) {
	runApplication<SokdakGatewayApplication>(*args)
}
