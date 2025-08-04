package com.example.data.classes_daos

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.sql.kotlin.datetime.CurrentTimestamp
import org.jetbrains.exposed.sql.kotlin.datetime.timestamp

object Messages : IntIdTable() {
    val senderId = integer("sender_id")
    val receiverId = integer("receiver_id")
    val content = text("content")
    val mediaPath = text("media_path").nullable()
    val status = enumerationByName("status", 10, MessageStatus::class)
    val timeStamp = timestamp("timestamp").defaultExpression(CurrentTimestamp)
}

object OnlineStatus : IntIdTable() {
    val isOnline = bool("is_online")
    val lastSeen = timestamp("last_seen")
}

@Serializable
data class ChatMessageResponse(
    val id: Int,
    val fromUserId: Int,
    val toUserId: Int,
    val content: String,
    val mediaPath: String? = null,
    val status: String,
    val timeStamp: String // ISO 8601 format
)


@Serializable
data class ChatMessagePayload(
    val id: Int,
    val fromUserId: Int,
    val toUserId: Int,
    val content: String,
    val mediaPath: String? = null,
    val messageType: String?
)

@Serializable
data class MessageStatusUpdate(
    val messageId: Int,
    val status: MessageStatus
)

@Serializable
data class TypingIndicator(
    val fromUserId: Int,
    val toUserId: Int,
    val isTyping: Boolean
)

enum class MessageStatus { SENT, DELIVERED, READ }

@Serializable
sealed class IncomingWebSocketMessage {
    @Serializable @SerialName("chat")
    data class Chat(val data: ChatMessagePayload) : IncomingWebSocketMessage()

    @Serializable @SerialName("status")
    data class Status(val data: StatusPayload) : IncomingWebSocketMessage()

    @Serializable @SerialName("typing")
    data class Typing(val data: TypingPayload) : IncomingWebSocketMessage()
}
val chatSerializersModule = SerializersModule {
    polymorphic(IncomingWebSocketMessage::class) {
        subclass(IncomingWebSocketMessage.Chat::class, IncomingWebSocketMessage.Chat.serializer())
        subclass(IncomingWebSocketMessage.Status::class, IncomingWebSocketMessage.Status.serializer())
        subclass(IncomingWebSocketMessage.Typing::class, IncomingWebSocketMessage.Typing.serializer())
    }
}


@Serializable
data class StatusPayload(
    val messageId: Int,
    val status: MessageStatus
)

@Serializable
data class TypingPayload(
    val toUserId: Int
)