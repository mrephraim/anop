package com.example.data.db_operations

import com.example.data.classes_daos.*
import kotlinx.datetime.Clock
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.greater
import org.jetbrains.exposed.sql.transactions.transaction

fun insertMessage(payload: ChatMessagePayload): Int = transaction {
    Messages.insertAndGetId {
        it[senderId] = payload.fromUserId
        it[receiverId] = payload.toUserId
        it[content] = sanitizeContent(payload.content)
        it[mediaPath] = payload.mediaPath
        it[status] = MessageStatus.SENT
        it[timeStamp] = Clock.System.now()
    }.value
}

fun updateMessageStatus(messageId: Int, status: MessageStatus) = transaction {
    Messages.update({ Messages.id eq messageId }) {
        it[Messages.status] = status
    }
}



fun sanitizeContent(input: String): String {
    return input.replace(Regex("<.*?>"), "").take(2048)
}


fun getAllMessagesForUser(userId: Int): List<ChatMessageResponse> = transaction {
    Messages
        .selectAll().where { (Messages.senderId eq userId) or (Messages.receiverId eq userId) }
        .orderBy(Messages.timeStamp, SortOrder.ASC)
        .map {
            ChatMessageResponse(
                id = it[Messages.id].value,
                fromUserId = it[Messages.senderId],
                toUserId = it[Messages.receiverId],
                content = it[Messages.content],
                mediaPath = it[Messages.mediaPath],
                status = it[Messages.status].name,
                timeStamp = it[Messages.timeStamp].toString() // Adjust to ISO if desired
            )
        }
}

fun getMessagesAfterMessageId(userId: Int, lastMessageId: Int): List<ChatMessageResponse> = transaction {
    Messages
        .selectAll().where {
            ((Messages.senderId eq userId) or (Messages.receiverId eq userId)) and
                    (Messages.id greater lastMessageId)
        }
        .orderBy(Messages.id, SortOrder.ASC)
        .map {
            ChatMessageResponse(
                id = it[Messages.id].value,
                fromUserId = it[Messages.senderId],
                toUserId = it[Messages.receiverId],
                content = it[Messages.content],
                mediaPath = it[Messages.mediaPath],
                status = it[Messages.status].name,
                timeStamp = it[Messages.timeStamp].toString()
            )
        }
}

