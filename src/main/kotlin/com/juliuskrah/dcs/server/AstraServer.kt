package com.juliuskrah.dcs.server

import com.juliuskrah.dcs.protocol.AstraProtocol
import com.juliuskrah.dcs.protocol.Protocol
import org.springframework.beans.factory.config.BeanDefinition.SCOPE_PROTOTYPE
import org.springframework.context.annotation.Scope
import org.springframework.stereotype.Component

/**
 * Prototype bean to create instances of an Astra Server
 */
@Scope(SCOPE_PROTOTYPE)
@Component
class AstraServer : DCServer() {
    override var address: String = "127.0.0.1"
    override var port: Int = 31090
    override var datagram: Boolean = false
    override val protocol: Protocol = AstraProtocol()
    override val name: String
        get() = "Astra"
}