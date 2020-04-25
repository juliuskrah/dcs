package com.juliuskrah.dcs.protocol

import com.juliuskrah.dcs.server.AstraServer
import com.juliuskrah.dcs.server.DCServer
import io.netty.buffer.ByteBuf
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import reactor.core.publisher.Mono
import java.lang.String.format
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets.UTF_8
import java.time.Duration
import java.time.Instant.ofEpochSecond
import java.time.ZoneOffset.UTC

/**
 * The Astra protocol
 */
open class AstraProtocol : Protocol {
    private val log: Logger = LoggerFactory.getLogger(AstraProtocol::class.java)
    private val protocolM: Byte = 0x4D
    private val protocolX: Byte = 0x58
    private val commandStart: Byte = 0x24

    /**
     * Returns the DC Server implementation
     */
    override fun server(): DCServer {
        return AstraServer()
    }

    /**
     * Gets the protocol identifier from the first byte and delegates handling to the correct
     * handler.
     *
     * @throws Exception when there's no handler for the protocol handler
     */
    override fun process(byteBuf: ByteBuf): Mono<Any> {
        val protocolIdentifier: Byte = byteBuf.getByte(0)
        log.info("Process handler for Astra Protocol {}", protocolIdentifier.toChar())
        var protocol: AstraProtocol? = null
        when (protocolIdentifier) {
            protocolM -> protocol = BinaryProtocol.MProtocol()
            protocolX -> protocol = BinaryProtocol.XProtocol()
            commandStart -> protocol = StringProtocol.AstraCommand()
        }
        protocol = protocol ?: throw Exception("Unknown protocol")
        return Mono.just(protocol.processing(byteBuf))
    }

    /**
     * The handler for actual implementation of the payload from the device
     */
    open fun processing(byteBuf: ByteBuf): Any {
        throw UnsupportedOperationException("The implementation should be provided by the subclass")
    }

    /**
     * Handles binary implementation for the astra protocol
     */
    abstract class BinaryProtocol : AstraProtocol() {
        protected var statusReportsToFollow = 0x10
        protected var statusExtraData = 0x100
        private var dbmMask = 0xF0
        protected var satMask = 0x0F
        protected var reasonJourneyStart = 0x40
        protected var reasonJourneyStop = 0x80

        /**
         *  CRC16 lookup table required for checksum generation
         */
        private val laCrc16LookUpTable = intArrayOf(0x0000, 0xC0C1, 0xC181, 0x0140, 0xC301, 0x03C0, 0x0280, 0xC241,
                0xC601, 0x06C0, 0x0780, 0xC741, 0x0500, 0xC5C1, 0xC481, 0x0440, 0xCC01, 0x0CC0, 0x0D80, 0xCD41, 0x0F00,
                0xCFC1, 0xCE81, 0x0E40, 0x0A00, 0xCAC1, 0xCB81, 0x0B40, 0xC901, 0x09C0, 0x0880, 0xC841, 0xD801, 0x18C0,
                0x1980, 0xD941, 0x1B00, 0xDBC1, 0xDA81, 0x1A40, 0x1E00, 0xDEC1, 0xDF81, 0x1F40, 0xDD01, 0x1DC0, 0x1C80,
                0xDC41, 0x1400, 0xD4C1, 0xD581, 0x1540, 0xD701, 0x17C0, 0x1680, 0xD641, 0xD201, 0x12C0, 0x1380, 0xD341,
                0x1100, 0xD1C1, 0xD081, 0x1040, 0xF001, 0x30C0, 0x3180, 0xF141, 0x3300, 0xF3C1, 0xF281, 0x3240, 0x3600,
                0xF6C1, 0xF781, 0x3740, 0xF501, 0x35C0, 0x3480, 0xF441, 0x3C00, 0xFCC1, 0xFD81, 0x3D40, 0xFF01, 0x3FC0,
                0x3E80, 0xFE41, 0xFA01, 0x3AC0, 0x3B80, 0xFB41, 0x3900, 0xF9C1, 0xF881, 0x3840, 0x2800, 0xE8C1, 0xE981,
                0x2940, 0xEB01, 0x2BC0, 0x2A80, 0xEA41, 0xEE01, 0x2EC0, 0x2F80, 0xEF41, 0x2D00, 0xEDC1, 0xEC81, 0x2C40,
                0xE401, 0x24C0, 0x2580, 0xE541, 0x2700, 0xE7C1, 0xE681, 0x2640, 0x2200, 0xE2C1, 0xE381, 0x2340, 0xE101,
                0x21C0, 0x2080, 0xE041, 0xA001, 0x60C0, 0x6180, 0xA141, 0x6300, 0xA3C1, 0xA281, 0x6240, 0x6600, 0xA6C1,
                0xA781, 0x6740, 0xA501, 0x65C0, 0x6480, 0xA441, 0x6C00, 0xACC1, 0xAD81, 0x6D40, 0xAF01, 0x6FC0, 0x6E80,
                0xAE41, 0xAA01, 0x6AC0, 0x6B80, 0xAB41, 0x6900, 0xA9C1, 0xA881, 0x6840, 0x7800, 0xB8C1, 0xB981, 0x7940,
                0xBB01, 0x7BC0, 0x7A80, 0xBA41, 0xBE01, 0x7EC0, 0x7F80, 0xBF41, 0x7D00, 0xBDC1, 0xBC81, 0x7C40, 0xB401,
                0x74C0, 0x7580, 0xB541, 0x7700, 0xB7C1, 0xB681, 0x7640, 0x7200, 0xB2C1, 0xB381, 0x7340, 0xB101, 0x71C0,
                0x7080, 0xB041, 0x5000, 0x90C1, 0x9181, 0x5140, 0x9301, 0x53C0, 0x5280, 0x9241, 0x9601, 0x56C0, 0x5780,
                0x9741, 0x5500, 0x95C1, 0x9481, 0x5440, 0x9C01, 0x5CC0, 0x5D80, 0x9D41, 0x5F00, 0x9FC1, 0x9E81, 0x5E40,
                0x5A00, 0x9AC1, 0x9B81, 0x5B40, 0x9901, 0x59C0, 0x5880, 0x9841, 0x8801, 0x48C0, 0x4980, 0x8941, 0x4B00,
                0x8BC1, 0x8A81, 0x4A40, 0x4E00, 0x8EC1, 0x8F81, 0x4F40, 0x8D01, 0x4DC0, 0x4C80, 0x8C41, 0x4400, 0x84C1,
                0x8581, 0x4540, 0x8701, 0x47C0, 0x4680, 0x8641, 0x8201, 0x42C0, 0x4380, 0x8341, 0x4100, 0x81C1, 0x8081,
                0x4040)

        /**
         * Generates a checksum for the received payload
         */
        private fun @ExtensionFunctionType ByteArray.generateCheckSum(): Int {
            val packetLength = this.size
            val bytePacketDatum = this
            // Cyclic Redundancy Check
            var llcrc = 0xFFFF
            var llChar: Int

            var lnPos = 0
            while (lnPos < packetLength - 2) {
                llChar = (llcrc xor bytePacketDatum[lnPos].toInt()) and 0x00ff
                llChar = laCrc16LookUpTable[llChar]
                llcrc = (llcrc shr 8) xor llChar
                lnPos++
            }

            return llcrc
        }

        /**
         * Determine signal strength from signal quality
         */
        protected fun @ExtensionFunctionType Int.determineSignalStrength(): Int {
            val signalQuality = this
            val highNibble = (signalQuality and dbmMask) shr 4

            // Possible dbm values according to protocols
            val dbm = intArrayOf(-111, -109, -105, -101, -97, -93, -89, -85, -81, -77, -73, -69, -65, -61, -57, -63)
            //If the reported signal level is in range, returns dBm info
            return if (highNibble in 0..15) {
                dbm[highNibble]
            } else {
                1
            }
        }

        fun @ExtensionFunctionType Int.toHexString(bitLength: Int): String {
            return toHexString(this.toLong() and 0xFFFFFFFFL, bitLength)
        }

        protected fun toHexString(`val`: Long, bitLength: Int): String {
            // bounds check 'bitLen'
            var bitLen = bitLength
            if (bitLen <= 0) {
                bitLen = 64 - java.lang.Long.numberOfLeadingZeros(`val`)
                if (bitLen <= 0) {
                    bitLen = 8
                }
            } else if (bitLen > 64) {
                bitLen = 64
            }

            // format and return hex value
            val nybbleLen = (bitLen + 7) / 8 * 2
            val hex = StringBuffer(java.lang.Long.toHexString(`val`).toUpperCase())
            if (nybbleLen <= 16 && nybbleLen > hex.length) {
                val mask = "0000000000000000" // 64 bit (16 nybbles)
                hex.insert(0, mask.substring(0, nybbleLen - hex.length))
            }
            return hex.toString()
        }

        override fun processing(byteBuf: ByteBuf): Any {
            val bytes: ByteArray
            val length = byteBuf.readableBytes()
            if (byteBuf.hasArray())
                bytes = byteBuf.array()
            else {
                bytes = ByteArray(length)
                byteBuf.getBytes(byteBuf.readerIndex(), bytes)
            }
            val packetChecksum = byteBuf.getUnsignedShort(bytes.size - 2)
            val checkSum: Int = bytes.generateCheckSum()
            if (checkSum != packetChecksum)
                throw Exception("Calculated checksum: $checkSum does not match packet checksum: $packetChecksum, aborting...")
            byteBuf.readByte() // protocol
            return parseProtocol(byteBuf)
        }

        abstract fun parseProtocol(byteBuf: ByteBuf): ByteArray

        class MProtocol : BinaryProtocol() {
            private val log: Logger = LoggerFactory.getLogger(MProtocol::class.java)
            private val statusIgnitionOn = 0x01
            private val protocolMBasicLength = 41
            private val protocolMStartStopLength = 53
            private val commands = mapOf(
                    1 to "\$TEST,1\r\n",
                    2 to "\$PARA,1\r\n",
                    3 to "\$POLL\r\n",
                    4 to "\$POSN,k,20\r\n",
                    // 5 to "\$IMEI\n",
                    5 to "\$PORT,31080\r\n"
            )

            override fun parseProtocol(byteBuf: ByteBuf): ByteArray {
                log.info("Parsing protocol M...")
                byteBuf.readUnsignedShort() // packet length
                val imei = format("%08d", byteBuf.readUnsignedInt()) + // tac
                        format("%07d", byteBuf.readUnsignedMedium())  // msn
                var reportsToFollow: Boolean
                // May contain more than one report after parsing the headers
                do {
                    byteBuf.readUnsignedByte() // sequence number
                    val latitude = (byteBuf.readInt() / 1000000f).toDouble()
                    val longitude = (byteBuf.readInt() / 1000000f).toDouble()
                    val fixTime: Long = byteBuf.readUnsignedInt() + 315964800L
                    val instant = ofEpochSecond(fixTime)
                    // Read Julian time - time and date in GPS seconds and convert to
                    // seconds from Unix epoch by adding the difference between
                    // 00:00:00 6 January 1980 and 00:00:00 1 January 1970
                    // The stored time will be GMT
                    val time = instant.atZone(UTC)
                    // Convert speed stored as km/h divided by 2 to km/h
                    val speed = (byteBuf.readUnsignedByte() * 2F).toDouble()
                    // Convert heading stored as degrees divided by 2 to degrees
                    val heading = (byteBuf.readUnsignedByte() * 2F).toDouble()
                    val reportReason = byteBuf.readUnsignedMedium()
                    val reportStatus = byteBuf.readUnsignedShort()
                    // Digital input/output status and state changes
                    // Read as little-endian so that Digitals #1 is the least significant byte
                    val digitalInputs = byteBuf.readMediumLE().toLong()
                    // ADC1 0-5V - convert to voltage with resolution of 0.02V
                    val adc1 = (byteBuf.readUnsignedByte() * 20).toDouble() / 1000F
                    // ADC2 0-15V - convert to voltage with resolution of 0.059V
                    val adc2 = (byteBuf.readUnsignedByte() * 59).toDouble() / 1000F
                    // Battery level as a percentage
                    val batteryLevel = byteBuf.readUnsignedByte().toInt()
                    // External input voltage to 0.2V resolution - convert to actual voltage
                    val externalPower = byteBuf.readUnsignedByte().toDouble() / 5f
                    // Convert max speed stored as km/h divided by 2 to km/h
                    // During a journey this is max speed since last report
                    // At the end of a journey it is max speed during the entire journey
                    val maxSpeed = byteBuf.readUnsignedByte() * 2
                    val accelerometerData = IntArray(6)
                    for (i in 0..5) {
                        accelerometerData[i] = byteBuf.readUnsignedByte().toInt()
                    }
                    // Journey distance to 0.1km resolution - convert to actual distance
                    val journeyDistance = byteBuf.readUnsignedShort().toDouble() / 10f
                    // Time in seconds that the vehicle is stationary with the ignition on
                    val journeyIdleTime = byteBuf.readUnsignedShort()
                    // Altitude in metres divided by 20 - convert to meters
                    val altitude = byteBuf.readUnsignedByte().toDouble() * 20f
                    // Most significant nibble is GSM signal strength: scale 0-15
                    // Least significant nibble is the number of GPS satellites in use
                    val signalQuality = byteBuf.readUnsignedByte().toInt()
                    val signalStrength = signalQuality.determineSignalStrength()
                    val satelliteCount = signalQuality and satMask
                    // Geofence: Bit 7=1 for entry, Bit 7=0 for exit. Bits 6-0=geofence index
                    val geoFence: Int = byteBuf.readUnsignedByte().toInt()
                    var odometer: Double
                    var engineOnHours: Long
                    // Read Driver ID
                    var driverUniqueId = "N/A"
                    if (reportStatus and statusExtraData > 0) {
                        val iButtonFN: Int = byteBuf.readUnsignedByte().toInt()
                        val iButtonSN: String = byteBuf.readCharSequence(6, UTF_8).toString()
                        driverUniqueId = StringBuilder() //
                                .append(iButtonFN.toHexString(8)).append('-') //
                                .append(iButtonSN).toString()
                    }

                    //If there is a journey start or stop, the device's odometer is read
                    if (reportReason and reasonJourneyStart > 0 || reportReason and reasonJourneyStop > 0) {
                        odometer = byteBuf.readUnsignedMedium().toDouble()
                        engineOnHours = byteBuf.readUnsignedShort().toLong()
                    } else {
                        val lastDistanceKM = 0.0
                        val lastOdometer = 0.0
                        val lastEngineOnHours: Long = 0
                        if (reportStatus and statusIgnitionOn > 0) {
                            // Calculates odometer using the device's data
                            odometer = lastOdometer - lastDistanceKM + journeyDistance
                            // check if there is some way to calculate the difference
                            // if not put the last value.
                            engineOnHours = lastEngineOnHours
                        } else { // If the device is not a journey or start/stop event, odometer doesn't change
                            odometer = lastOdometer
                            engineOnHours = lastEngineOnHours
                        }
                    }
                    var reportLength: Int
                    reportLength = if (reportStatus and statusExtraData > 0)
                        protocolMStartStopLength
                    else protocolMBasicLength
                    // TODO status code and hex
                    val journeyStart = if (reportReason and reasonJourneyStart > 0) 1 else 0
                    val journeyStop = if (reportReason and reasonJourneyStop > 0) 1 else 0

                    val sb = StringBuilder()
                    val rawData: String = sb.append("R=").append(reportReason.toHexString(24)) //
                            .append(";S=").append(reportStatus.toHexString(16)) //
                            .append(";P=").append(externalPower).append('V') //
                            .append(";B=").append(batteryLevel).append('%') //
                            .append(";D=").append(toHexString(digitalInputs, 24)) //
                            .append(";A1=").append(adc1).append('V') //
                            .append(";A2=").append(adc2).append('V') //
                            .append(";M=").append(maxSpeed).append("km/h") //
                            .append(";X=").append(accelerometerData[0]).append(',').append(accelerometerData[1]) //
                            .append(";Y=").append(accelerometerData[2]).append(',').append(accelerometerData[3]) //
                            .append(";Z=").append(accelerometerData[4]).append(',').append(accelerometerData[5]) //
                            .append(";I=").append(journeyIdleTime).append('s') //
                            .append(";Q=").append(signalQuality.toHexString(8)) //
                            .append(";G=").append(geoFence.toHexString(8)) //
                            .toString()

                    log.info("IMEI number: \t\t{}", imei)
                    log.info("Report length: \t\t{}", reportLength)
                    log.info("Latitude: \t\t\t{}", latitude)
                    log.info("Longitude: \t\t\t{}", longitude)
                    log.info("Event time: \t\t{}", time)
                    log.info("Speed: \t\t\t{}km/h", speed)
                    log.info("Max speed: \t\t\t{}km/h", maxSpeed)
                    log.info("Heading: \t\t\t{}°", heading)
                    log.info("ADC 1: \t\t\t{}V", adc1)
                    log.info("ADC 2: \t\t\t{}V", adc2)
                    log.info("Battery level: \t\t{}%", batteryLevel)
                    log.info("External power: \t\t{}V", externalPower)
                    log.info("Idle time: \t\t\t{}", Duration.ofSeconds(journeyIdleTime.toLong()))
                    log.info("Altitude: \t\t\t{}m", altitude)
                    log.info("Signal quality: \t\t{}", signalQuality)
                    log.info("Signal strength: \t\t{}", signalStrength)
                    log.info("Satellite count: \t\t{}", satelliteCount)
                    log.info("Geofence: \t\t\t{}", geoFence)
                    log.info("Odometer: \t\t\t{}km", odometer)
                    log.info("Engine hours: \t\t{}h", engineOnHours)
                    log.info("Driver ID: \t\t\t{}", driverUniqueId)
                    log.info("Journey start: \t\t{}", journeyStart)
                    log.info("Journey stop: \t\t{}", journeyStop)
                    log.info("Raw data: \t\t\t{}", rawData)

                    // Check for more reports in the packet
                    reportsToFollow = reportStatus and statusReportsToFollow > 0
                } while (reportsToFollow && byteBuf.readableBytes() > 2)
                val random = (1..5).random()
                val command = commands[random] ?: throw Exception("No command available")
                log.info("{}\n----------------------------------------------------", command)
                return command.toByteArray()
                // return byteArrayOf(0x07) // TODO return 0x06
            }
        }

        class XProtocol : BinaryProtocol() {
            private val log: Logger = LoggerFactory.getLogger(XProtocol::class.java)
            override fun parseProtocol(byteBuf: ByteBuf): ByteArray {
                log.info("Parsing protocol X...")
                return byteArrayOf(0x06)
            }
        }
    }

    /**
     * Handles string implementations for the
     */
    abstract class StringProtocol : AstraProtocol() {
        override fun processing(byteBuf: ByteBuf): Any {
            val command = byteBuf.toString(Charset.defaultCharset())
            return parseProtocol(command)
        }

        abstract fun parseProtocol(command: String): String

        class AstraCommand : StringProtocol() {
            private val commands = mapOf(
                    1 to "\$TEST,1\r\n",
                    2 to "\$PARA,1\r\n",
                    3 to "\$POLL\r\n",
                    4 to "\$POSN,k,20\r\n",
                    5 to "\$IMEI\r\n"
            )
            private val log: Logger = LoggerFactory.getLogger(AstraCommand::class.java)
            override fun parseProtocol(command: String): String {
                log.info("Command: {}", command)
                val random = (1..5).random()
                val commander = commands[random] ?: throw Exception("No command available")
                log.info(commander)
                log.info("\n----------")
                return "\$DRID,APPROVE,01,00000000125408C9\r\n" // TODO return drid command
            }
        }
    }
}
