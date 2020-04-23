package com.juliuskrah.dcs.protocol

import com.juliuskrah.dcs.server.DCServer
import io.netty.buffer.ByteBuf
import org.reactivestreams.Publisher
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import reactor.netty.NettyInbound
import reactor.netty.NettyOutbound

/**
 * Represents the
 */
interface Protocol {
    private val log: Logger
        get() = LoggerFactory.getLogger(Protocol::class.java)

    /**
     * Defines how the payload will be handled
     * @param inbound the incoming request
     * @param outbound the outgoing response
     */
    fun handle(inbound: NettyInbound, outbound: NettyOutbound): Publisher<Void> {
        return inbound.withConnection {
                    log.info("Client {} connected on port {}", it.address().hostString, it.address().port)
                }
                .receive()
                .map { buffer ->
                    process(buffer)
                }.flatMap { response ->
                    outbound.sendObject(response).then()
                }
                .then()
    }

    fun server(): DCServer

    fun process(byteBuf: ByteBuf): Any
}