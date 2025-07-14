package com.example.application

import io.ktor.server.websocket.*
import io.ktor.websocket.*
import io.lettuce.core.pubsub.RedisPubSubAdapter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

val activePostWatchers = mutableMapOf<Int, MutableSet<DefaultWebSocketServerSession>>()

object RedisPostReactionSubscriber {
    private val connection = RedisProvider.client.connectPubSub()
    private val commands = connection.async()

    init {
        connection.addListener(object : RedisPubSubAdapter<String, String>() {
            override fun message(channel: String?, message: String?) {
                if (channel == null || message == null) return
                val postId = channel.substringAfter("post:").substringBefore(":reaction").toIntOrNull() ?: return

                // Send update to all sessions watching this post
                val watchers = activePostWatchers[postId] ?: return
                for (session in watchers) {
                    if (session.isActive) {
                        CoroutineScope(Dispatchers.IO).launch {
                            try {
                                session.send(Frame.Text(message))
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }
                    }
                }
            }
        })
    }

    fun subscribeToPost(postId: Int) {
        val channel = "post:$postId:reaction"
        commands.subscribe(channel)
    }
}

