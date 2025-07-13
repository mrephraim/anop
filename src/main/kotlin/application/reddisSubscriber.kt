package com.example.application

import com.example.logic.activeSessions
import io.ktor.websocket.*
import io.lettuce.core.pubsub.RedisPubSubAdapter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch


object RedisChatSubscriber {
    private val connection = RedisProvider.client.connectPubSub()
    private val commands = connection.async()

    init {
        connection.addListener(object : RedisPubSubAdapter<String, String>() {
            override fun message(channel: String?, message: String?) {
                if (channel == null || message == null) return

                val userId = channel.substringAfterLast(":").toIntOrNull() ?: return
                val session = activeSessions[userId]

                if (session != null && session.isActive) {
                    CoroutineScope(Dispatchers.IO).launch {
                        try {
                            session.send(Frame.Text(message))
                        } catch (e: Exception) {
                            e.printStackTrace()
                            activeSessions.remove(userId)
                        }
                    }
                }
            }
        })
    }

    fun subscribeToUserChannel(userId: Int) {
        val channel = "chat:user:$userId"
        println("🔔 Subscribing to Redis channel: $channel")
        commands.subscribe(channel) // This is now on the SAME connection with the listener
    }
}
