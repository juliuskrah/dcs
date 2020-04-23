package com.juliuskrah.dcs

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import javax.annotation.PostConstruct

class ServerInitializer {
    private val log: Logger = LoggerFactory.getLogger(ServerInitializer::class.java)

    @PostConstruct
    fun startServers() {
        log.info("PostConstruct")
    }
}