package com.example

import com.example.application.RedisChatSubscriber
import com.example.application.internalRoute
import com.example.data.DatabaseFactory
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.websocket.*
import kotlinx.serialization.json.Json

fun main(args: Array<String>) {
    io.ktor.server.netty.EngineMain.main(args)
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
