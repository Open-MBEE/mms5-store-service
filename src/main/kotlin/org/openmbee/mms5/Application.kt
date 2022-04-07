package org.openmbee.mms5

import io.ktor.application.*
import org.openmbee.mms5.plugins.*

fun main(args: Array<String>): Unit =
    io.ktor.server.cio.EngineMain.main(args)

@Suppress("unused") // application.conf references the main function. This annotation prevents the IDE from marking it as unused.
fun Application.module() {
    configureRouting()
    configureSecurity()
    configureHTTP()
    configureAuthentication()
    configureMonitoring()
    configureSerialization()
    configureStorage()
}
