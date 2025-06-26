package com.example.data.classes_daos

import com.example.data.models.UserInitials
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.jodatime.datetime
import org.jetbrains.exposed.sql.jodatime.CurrentDateTime

object Followers : Table("followers") {
    val id = integer("id").autoIncrement().uniqueIndex() // Unique row ID
    val userId = integer("user_id").references(UserInitials.id) // Follower
    val followingUserId = integer("following_user_id").references(UserInitials.id) // Being followed
    val status = integer("status").check { it greaterEq 1 and (it lessEq 3) } // 1=active, 2=pending, 3=blocked

    // Timestamps with default current date-time
    val createdAt = datetime("created_at").defaultExpression(CurrentDateTime)
    val updatedAt = datetime("updated_at").defaultExpression(CurrentDateTime)

    // Primary key on id
    override val primaryKey = PrimaryKey(id, name = "PK_Followers")

    // Additional indexes
    init {
        uniqueIndex("UX_Follower_Target", userId, followingUserId) // Prevent duplicate connections
        index(false, userId)                   // index on userId
        index(false, followingUserId)         // index on followingUserId
    }
}


@Serializable
data class FollowAction(
    val userId: Int,
    val targetUserId: Int
)

@Serializable
data class FollowCheckRequest(val userId: Int, val otherUserId: Int)

// Result type
sealed class FollowBackResult {
    data class Success(val message: String) : FollowBackResult()
    data class Failure(val reason: String) : FollowBackResult()
}


@Serializable
data class FollowCountResponse(
    val followersCount: Int,
    val followingCount: Int
)

@Serializable
data class FollowResponse(val message: String)