package com.example.application

import io.lettuce.core.RedisClient


object RedisProvider {
    val client = RedisClient.create("redis://localhost:6379") // or use your Docker or cloud URI
    val connection = client.connect()
    val commands = connection.sync()
    val pubSub = client.connectPubSub().async()

    fun close() {
        connection.close()
        client.shutdown()
    }
}



