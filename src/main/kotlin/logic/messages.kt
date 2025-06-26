package com.example.logic

import com.example.data.classes_daos.IncomingWebSocketMessage
import com.example.data.classes_daos.MessageStatus
import com.example.data.db_operations.getAllMessagesForUser
import com.example.data.db_operations.insertMessage
import com.example.data.db_operations.updateMessageStatus
import com.example.data.db_operations.updateOnlineStatus
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.server.request.*
import io.ktor.server.routing.*
import io.ktor.server.response.*
import io.ktor.server.websocket.*
import io.ktor.utils.io.*
import io.ktor.websocket.*
import kotlinx.serialization.json.Json
import java.io.File
import java.nio.file.Paths

val userSessions = mutableMapOf<Int, DefaultWebSocketServerSession>()

fun Route.chatWebSocketRoute() {
    webSocket("/chat") {
        val userIdParam = call.request.queryParameters["userId"] ?: return@webSocket close()
        val userId = userIdParam.toIntOrNull() ?: return@webSocket close()

        userSessions[userId] = this
        updateOnlineStatus(userId, true)

        try {
            for (frame in incoming) {
                if (frame is Frame.Text) {
                    val text = frame.readText()
                    when (val parsed = Json.decodeFromString<IncomingWebSocketMessage>(text)) {
                        is IncomingWebSocketMessage.Chat -> {
                            val msg = parsed.data
                            val messageId = insertMessage(msg)

                            userSessions[msg.toUserId]?.let { receiverSession ->
                                receiverSession.send(
                                    Frame.Text(Json.encodeToString(IncomingWebSocketMessage.serializer(), IncomingWebSocketMessage.Chat(msg)))
                                )
                                updateMessageStatus(messageId, MessageStatus.DELIVERED)
                            }
                        }
                        is IncomingWebSocketMessage.Status -> {
                            updateMessageStatus(parsed.data.messageId, parsed.data.status)
                        }
                        is IncomingWebSocketMessage.Typing -> {
                            userSessions[parsed.data.toUserId]?.send(
                                Frame.Text(Json.encodeToString(parsed)) // ✅ This adds the required "type" discriminator
                            )
                        }
                    }
                }
            }
        } finally {
            userSessions.remove(userId)
            updateOnlineStatus(userId, false)
        }
    }

    get("/getMessages/{userId}") {
        val userId = call.parameters["userId"]?.toIntOrNull()
        if (userId == null) {
            call.respond(HttpStatusCode.BadRequest, "Invalid userId")
            return@get
        }

        try {
            val messages = getAllMessagesForUser(userId)
            call.respond(messages)
        } catch (e: Exception) {
            e.printStackTrace()
            call.respond(HttpStatusCode.InternalServerError, "Failed to fetch messages")
        }
    }

    post("/uploadChatMedia") {
        val multipart = call.receiveMultipart()
        var fromUserId: Int? = null
        var mediaType: String? = null
        var mediaFile: Pair<ByteArray, String>? = null

        multipart.forEachPart { part ->
            when (part) {
                is PartData.FormItem -> {
                    when (part.name) {
                        "fromUserId" -> fromUserId = part.value.toIntOrNull()
                        "mediaType" -> mediaType = part.value // "IMAGE" or "VIDEO"
                    }
                }
                is PartData.FileItem -> {
                    val fileName = part.originalFileName ?: "file.bin"
                    val bytes = part.provider().toByteArray()
                    mediaFile = bytes to fileName
                }
                else -> Unit
            }
            part.dispose()
        }

        if (fromUserId == null || mediaType == null || mediaFile == null) {
            call.respond(HttpStatusCode.BadRequest, mapOf("status" to "error", "message" to "Missing required fields"))
            return@post
        }

        val (bytes, name) = mediaFile!!

        if (bytes.size > 30 * 1024 * 1024) {
            call.respond(HttpStatusCode.BadRequest, mapOf("status" to "error", "message" to "File exceeds 30MB limit"))
            return@post
        }

        val uploadsPath = Paths.get("").toAbsolutePath().toString()
        val folder = when (mediaType) {
            "IMAGE" -> File("$uploadsPath/uploads/messaging/images")
            "VIDEO" -> File("$uploadsPath/uploads/messaging/videos")
            else -> {
                call.respond(HttpStatusCode.BadRequest, mapOf("status" to "error", "message" to "Invalid mediaType"))
                return@post
            }
        }

        if (!folder.exists()) folder.mkdirs()

        val uniqueName = "${fromUserId}_${System.currentTimeMillis()}_$name"
        val file = File(folder, uniqueName)
        file.writeBytes(bytes)

        call.respond(HttpStatusCode.OK, mapOf(
            "status" to "success",
            "fileName" to uniqueName,
            "relativePath" to "/uploads/messaging/${if (mediaType == "IMAGE") "images" else "videos"}/$uniqueName"
        ))
    }


}
