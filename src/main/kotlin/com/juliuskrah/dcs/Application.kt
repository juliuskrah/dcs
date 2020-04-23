package com.juliuskrah.dcs

import com.juliuskrah.dcs.protocol.AstraProtocol
import com.juliuskrah.dcs.server.DCServer
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.boot.CommandLineRunner
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.runApplication
import org.springframework.context.support.beans

/**
 * @author Julius Krah
 */
@SpringBootApplication
@EnableConfigurationProperties(ApplicationProperties::class)
class Application

private val log: Logger = LoggerFactory.getLogger(Application::class.java)

fun main(args: Array<String>) {
    runApplication<Application>(*args) {
        addInitializers(
                beans {
                    // bean<ServerInitializer>()
                    bean {
                        CommandLineRunner {
                            log.info("Starting servers and waiting...")
                            val properties = ref<ApplicationProperties>()
                            // Using bean reference
                            val astraTcp = ref<DCServer>()
                            // Using a builder
                            val astraUdp = DCServer.Builder()
                                    .address(properties.address)
                                    .port(properties.port)
                                    .datagram(true)
                                    .protocol(AstraProtocol())
                                    .build()
                            astraTcp.port = properties.port
                            astraTcp.address = properties.address
                            astraTcp.datagram = properties.datagram
                            astraTcp.start()
                                    .zipWith(astraUdp.start())
                                    .block()
                        }
                    }
                }
        )
    }
}
