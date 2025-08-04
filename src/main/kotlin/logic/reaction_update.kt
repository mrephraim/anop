package com.example.logic

import com.example.application.RedisPostReactionSubscriber
import com.example.application.RedisProvider
import com.example.application.activePostWatchers
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*

fun Route.reactionWebSocketRoute() {
    webSocket("/watchPost") {
        val postId = call.request.queryParameters["postId"]?.toIntOrNull() ?: return@webSocket close()
        RedisPostReactionSubscriber.subscribeToPost(postId)

        // Add this session to watchers
        val watchers = activePostWatchers.getOrPut(postId) { mutableSetOf() }
        watchers.add(this)

        try {
            for (frame in incoming) {

            }
        } finally {
            watchers.remove(this)
            if (watchers.isEmpty()) {
                activePostWatchers.remove(postId)
            }
        }
    }

}
