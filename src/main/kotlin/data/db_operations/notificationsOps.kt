package com.example.data.db_operations

import com.example.data.classes_daos.NotificationPayload
import com.example.data.classes_daos.NotificationResponse
import com.example.data.classes_daos.Notifications
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.greater
import org.jetbrains.exposed.sql.SqlExpressionBuilder.less
import org.jetbrains.exposed.sql.transactions.transaction
import org.joda.time.DateTime

fun insertNotification(
    userId: Int,
    actorId: Int?,
    type: String,
    payload: NotificationPayload
): Int? {
    return try {
        transaction {
            Notifications.insertAndGetId {
                it[Notifications.userId] = userId
                it[Notifications.actorId] = actorId
                it[Notifications.type] = type
                it[Notifications.payload] = Json.encodeToString(NotificationPayload.serializer(), payload)
                it[Notifications.isViewed] = false
            }.value
        }
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}

fun getNotificationsSince(userId: Int, lastSeenId: Int): List<NotificationResponse> {
    return transaction {
        Notifications
            .selectAll().where{ (Notifications.userId eq userId) and (Notifications.id greater lastSeenId) }
            .orderBy(Notifications.id to SortOrder.DESC)
            .map {
                NotificationResponse(
                    id = it[Notifications.id].value,
                    type = it[Notifications.type],
                    actorId = it[Notifications.actorId],
                    payload = Json.decodeFromString(it[Notifications.payload]),
                    isViewed = it[Notifications.isViewed],
                    createdAt = it[Notifications.createdAt].toString()
                )
            }
    }
}

fun markNotificationsAsViewed(userId: Int) {
    transaction {
        Notifications.update({ (Notifications.userId eq userId) and (Notifications.isViewed eq false) }) {
            it[isViewed] = true
        }
    }
}

fun clearOldNotifications(userId: Int, before: DateTime) {
    transaction {
        Notifications.deleteWhere {
            (Notifications.userId eq userId) and (Notifications.createdAt less before)
        }
    }
}





