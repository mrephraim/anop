package com.example

import com.example.application.RedisChatSubscriber
import com.example.application.internalRoute
import com.example.data.DatabaseFactory
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.websocket.*
import kotlinx.serialization.json.Json

fun main() {
    embeddedServer(Netty, port = System.getenv("PORT")?.toInt() ?: 8080, host = "0.0.0.0") {
        module() // or whatever your Ktor module is named
    }.start(wait = true)
}


fun Application.module() {
    install(ContentNegotiation) {
        json(Json {
            ignoreUnknownKeys = true // Allows extra JSON fields
            isLenient = true // Allows lenient parsing
        })
    }

    install(WebSockets)
    DatabaseFactory.init()
    internalRoute()
}
