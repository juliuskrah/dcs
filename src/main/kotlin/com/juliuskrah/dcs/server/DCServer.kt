package com.juliuskrah.dcs.server

import com.juliuskrah.dcs.protocol.Protocol
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import reactor.core.publisher.Mono
import reactor.netty.DisposableChannel
import reactor.netty.tcp.TcpServer
import reactor.netty.udp.UdpServer

/**
 * Base class for all Device Communication Servers. Implementations of this class will
 * bootstrap a server that understands how to process the payload of a device
 *
 * @author Julius Krah
 */
abstract class DCServer {
    private val log: Logger = LoggerFactory.getLogger(DCServer::class.java)
    abstract var address: String
    abstract var port: Int
    abstract var datagram: Boolean

    // TODO change to Class<?> for dependency injection
    abstract val protocol: Protocol

    /**
     * name of the DCS implementation
     */
    abstract val name: String

    /**
     * Bootstraps the Netty server and waits for connection
     */
    fun start(): Mono<Void> {
        return server().flatMap {
            log.info("$name Server started on {}:{}", it.address().hostString, it.address().port)
            it.onDispose()
        }
    }

    /**
     * Sets up a UDP Server when 'datagram' is {@literal true}, atherwise sets up a TCP Server
     */
    private fun server(): Mono<out DisposableChannel> {
        if (datagram)
            return UdpServer.create()
                    .handle(protocol::handle)
                    .host(address)
                    .port(port)
                    .bind()
        return TcpServer.create()
                .handle(protocol::handle)
                .host(address)
                .port(port)
                .bind()
    }

    /**
     * Builder to create instances of a DCServer
     */
    data class Builder(
            var address: String? = null,
            var port: Int? = null,
            var datagram: Boolean? = null,
            var protocol: Protocol? = null
    ) {
        fun address(address: String) = apply { this.address = address }
        fun port(port: Int) = apply { this.port = port }
        fun datagram(datagram: Boolean) = apply { this.datagram = datagram }
        fun protocol(protocol: Protocol) = apply { this.protocol = protocol }
        fun build(): DCServer {
            val server = protocol!!.server()
            server.address = address!!
            server.port = port!!
            server.datagram = datagram!!
            return server
        }
    }
}