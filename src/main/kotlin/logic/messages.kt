package com.example.logic

import com.example.application.RedisChatSubscriber
import com.example.application.RedisProvider
import com.example.data.classes_daos.IncomingWebSocketMessage
import com.example.data.classes_daos.MessageStatus
import com.example.data.classes_daos.chatSerializersModule
import com.example.data.db_operations.getAllMessagesForUser
import com.example.data.db_operations.getMessagesAfterMessageId
import com.example.data.db_operations.insertMessage
import com.example.data.db_operations.updateMessageStatus
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.server.request.*
import io.ktor.server.routing.*
import io.ktor.server.response.*
import io.ktor.server.websocket.*
import io.ktor.utils.io.*
import io.ktor.websocket.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import java.io.File
import java.nio.file.Paths

val activeSessions = mutableMapOf<Int, DefaultWebSocketServerSession>()

fun Route.chatWebSocketRoute() {
    webSocket("/chat") {
        val userId = call.request.queryParameters["userId"]?.toIntOrNull() ?: return@webSocket close()
        RedisChatSubscriber.subscribeToUserChannel(userId)
        RedisProvider.commands.setex("user:$userId:online", 60, "1")
        activeSessions[userId] = this

        val renewTTLJob = launch {
            while (true) {
                delay(30_000)
                RedisProvider.commands.expire("user:$userId:online", 60)
            }
        }

        try {
            for (frame in incoming) {
                if (frame is Frame.Text) {
                    val text = frame.readText()
                    val json = Json {
                        serializersModule = chatSerializersModule
                        ignoreUnknownKeys = true
                        classDiscriminator = "type" // This is the key expected in JSON (e.g., "type": "chat")
                    }

                    val parsed = json.decodeFromString<IncomingWebSocketMessage>(text)

                    when (parsed) {
                        is IncomingWebSocketMessage.Chat -> {
                            val msg = parsed.data
                            val messageId = insertMessage(msg)

                            val msgWithId = msg.copy(id = messageId)

                            // Create a new parsed message with updated data
                            val updatedParsed = IncomingWebSocketMessage.Chat(data = msgWithId)


                            val channel = "chat:user:${msg.toUserId}"
                            val encoded = json.encodeToString(IncomingWebSocketMessage.serializer(), updatedParsed)
                            RedisProvider.commands.publish(channel, encoded)

                            updateMessageStatus(messageId, MessageStatus.SENT)
                        }

                        is IncomingWebSocketMessage.Status -> {
                            updateMessageStatus(parsed.data.messageId, parsed.data.status)
                        }

                        is IncomingWebSocketMessage.Typing -> {
                            val channel = "chat:user:${parsed.data.toUserId}"
                            RedisProvider.commands.publish(channel, Json.encodeToString(parsed))
                        }
                    }
                }
            }
        } finally {
            renewTTLJob.cancel()
            RedisProvider.commands.del("user:$userId:online")
            activeSessions.remove(userId)
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

    get("/getMessagesSince") {
        val userId = call.request.queryParameters["userId"]?.toIntOrNull()
        val lastMessageId = call.request.queryParameters["lastMessageId"]?.toIntOrNull()

        if (userId == null || lastMessageId == null) {
            call.respond(HttpStatusCode.BadRequest, "Missing parameters")
            return@get
        }

        try {
            val messages = getMessagesAfterMessageId(userId, lastMessageId)
            call.respond(messages)
        } catch (e: Exception) {
            call.respond(HttpStatusCode.InternalServerError, "Failed to fetch messages")
        }
    }


}





