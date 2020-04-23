package com.juliuskrah.dcs

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.ConstructorBinding

@ConstructorBinding
@ConfigurationProperties("dcs")
data class ApplicationProperties(
        val address: String,
        val port: Int,
        val datagram: Boolean
)