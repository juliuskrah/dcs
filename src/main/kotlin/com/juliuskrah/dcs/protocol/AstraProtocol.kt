package com.juliuskrah.dcs.protocol

import com.juliuskrah.dcs.server.AstraServer
import com.juliuskrah.dcs.server.DCServer
import io.netty.buffer.ByteBuf
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.nio.charset.Charset

open class AstraProtocol : Protocol {
    private val log: Logger = LoggerFactory.getLogger(AstraProtocol::class.java)
    private val protocolM: Byte = 0x4D
    private val protocolX: Byte = 0x58
    private val commandStart: Byte = 0x24

    override fun server(): DCServer {
        return AstraServer()
    }

    override fun process(byteBuf: ByteBuf): Any {
        val protocolIdentifier: Byte = byteBuf.getByte(0)
        log.info("Process handler for Astra Protocol {}", protocolIdentifier.toChar())
        var protocol: AstraProtocol? = null
        when (protocolIdentifier) {
            protocolM -> protocol = BinaryProtocol.MProtocol()
            protocolX -> protocol = BinaryProtocol.XProtocol()
            commandStart -> protocol = StringProtocol.AstraCommand()
        }
        protocol = protocol ?: throw Exception("Unknown protocol")
        return protocol.processing(byteBuf)
    }

    open fun processing(byteBuf: ByteBuf): Any {
        throw UnsupportedOperationException("The implementation should be provided by the subclass")
    }

    abstract class BinaryProtocol : AstraProtocol() {
        override fun processing(byteBuf: ByteBuf): Any {
            return parseProtocol(byteBuf)
        }

        abstract fun parseProtocol(byteBuf: ByteBuf): ByteArray

        class MProtocol : BinaryProtocol() {
            private val log: Logger = LoggerFactory.getLogger(MProtocol::class.java)
            override fun parseProtocol(byteBuf: ByteBuf): ByteArray {
                log.info("M Protocol parse")
                return ByteArray(0x06)
            }

        }

        class XProtocol : BinaryProtocol() {
            private val log: Logger = LoggerFactory.getLogger(XProtocol::class.java)
            override fun parseProtocol(byteBuf: ByteBuf): ByteArray {
                log.info("X Protocol parse")
                return ByteArray(0x06)
            }
        }
    }

    abstract class StringProtocol : AstraProtocol() {
        override fun processing(byteBuf: ByteBuf): Any {
            val command = byteBuf.toString(Charset.defaultCharset())
            return parseProtocol(command)
        }

        abstract fun parseProtocol(command: String): String

        class AstraCommand : StringProtocol() {
            private val log: Logger = LoggerFactory.getLogger(AstraCommand::class.java)
            override fun parseProtocol(command: String): String {
                log.info("Astra command parse: {}", command)
                return "ByteArray(0x06)"
            }
        }
    }
}
