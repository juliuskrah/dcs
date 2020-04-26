package com.juliuskrah.dcs.protocol

import com.juliuskrah.dcs.server.DCServer
import io.netty.buffer.ByteBuf
import org.reactivestreams.Publisher
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import reactor.core.publisher.Mono
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
                .flatMap { buffer ->
                    try {
                        process(buffer)
                    } catch (e: Exception) {
                        if(log.isDebugEnabled) {
                            log.error("An uncaught exception occurred", e)
                        } else
                            log.error("Unhandled error - {}", e.message)
                        Mono.error<Any>(e)
                    }
                }.flatMap { response ->
                    when (response) {
                        is ByteArray -> outbound.sendByteArray(Mono.just(response)).then()
                        is String -> outbound.sendString(Mono.just(response)).then()
                        else -> outbound.sendObject(Mono.just(response)).then()
                    }
                }
                .then()
    }

    fun server(): DCServer

    fun process(byteBuf: ByteBuf): Mono<Any>
}