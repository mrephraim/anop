package com.example.data.classes_daos

import kotlinx.serialization.json.Json
import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.sql.jodatime.datetime
import org.joda.time.DateTime
import kotlinx.serialization.Serializable

object Notifications : IntIdTable("notifications") {
    val userId = integer("user_id") // recipient of the notification
    val actorId = integer("actor_id").nullable() // who triggered the action
    val type = varchar("type", 50) // e.g. "FOLLOW", "LIKE", "COMMENT", etc.
    val payload = text("payload") // store JSON string
    val isViewed = bool("is_viewed").default(false)
    val createdAt = datetime("created_at").clientDefault { DateTime.now() }
}


@Serializable
data class NotificationPayload(
    val postId: Int? = null,
    val commentId: Int? = null,
    val profileId: Int? = null,
    val customMessage: String? = null
)

@Serializable
data class NotificationResponse(
    val id: Int,
    val type: String,
    val actorId: Int?,
    val payload: NotificationPayload,
    val isViewed: Boolean,
    val createdAt: String
)
