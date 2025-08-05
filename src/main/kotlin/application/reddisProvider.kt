package com.example.application

import io.lettuce.core.RedisClient
import io.lettuce.core.RedisURI
import io.lettuce.core.api.StatefulRedisConnection
import io.lettuce.core.api.sync.RedisCommands
import io.lettuce.core.pubsub.StatefulRedisPubSubConnection
import io.lettuce.core.pubsub.api.async.RedisPubSubAsyncCommands
import java.time.Duration


object RedisProvider {
    private val redisHost = System.getenv("REDIS_HOST") ?: "localhost"
    private val redisPort = System.getenv("REDIS_PORT")?.toIntOrNull() ?: 6379
    private val redisPassword = System.getenv("REDIS_KEY")

    private val redisUri = RedisURI.builder()
        .withHost(redisHost)
        .withPort(redisPort)
        .withSsl(true)
        .withPassword(redisPassword?.toCharArray())
        .withTimeout(Duration.ofSeconds(10))
        .build()

    val client: RedisClient = RedisClient.create(redisUri)
    val connection: StatefulRedisConnection<String, String> = client.connect()
    val commands: RedisCommands<String, String> = connection.sync()

    val pubSubConnection: StatefulRedisPubSubConnection<String, String> = client.connectPubSub()
    val pubSub: RedisPubSubAsyncCommands<String, String> = pubSubConnection.async()

    fun close() {
        pubSubConnection.close()
        connection.close()
        client.shutdown()
    }
}



