package com.example.data.db_operations

import com.example.data.classes_daos.*
import com.example.data.models.*
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction
import org.joda.time.DateTime


val now: DateTime = DateTime.now()

fun followUser(action: FollowAction): Boolean = transaction {
    if (action.userId == action.targetUserId) return@transaction false

    // Check if either blocked each other (status = 3 means blocked)
    val blockExists = Followers.selectAll().where {
        ((Followers.userId eq action.userId) and (Followers.followingUserId eq action.targetUserId) and (Followers.status eq 3)) or
                ((Followers.userId eq action.targetUserId) and (Followers.followingUserId eq action.userId) and (Followers.status eq 3))
    }.any()

    // If blocked, cancel follow
    if (blockExists) return@transaction false

    // Check if current user already follows the target
    val youFollowTarget = areYouFollowing(action.userId, action.targetUserId)
    if (youFollowTarget) {
        // Already following or mutual, no action needed
        return@transaction true
    }

    // No relation yet, insert new follow with status = 1 (one-way)
    Followers.insert {
        it[userId] = action.userId
        it[followingUserId] = action.targetUserId
        it[status] = 1
        it[createdAt] = now
        it[updatedAt] = now
    }

    true
}

fun unfollowUser(action: FollowAction): Boolean {
    return transaction {
        val row = Followers
            .selectAll().where { ((Followers.userId eq action.userId) and (Followers.followingUserId eq action.targetUserId)) or  ((Followers.userId eq action.targetUserId) and (Followers.followingUserId eq action.userId)) }
            .limit(1)
            .singleOrNull()


        if (row != null) {
            val followStatus = row[Followers.status]
            val dbFollowingUserId = row[Followers.followingUserId]

            if (followStatus == 2) {
                if (action.targetUserId == dbFollowingUserId) {
                    Followers.update(
                        {(Followers.userId eq action.userId) and (Followers.followingUserId eq action.targetUserId)}
                    ) {
                        it[status] = 1
                        it[updatedAt] = now
                    }
                } else {
                    Followers.deleteWhere {
                        (userId eq action.targetUserId) and (followingUserId eq action.userId)
                    }

                    Followers.insert {
                        it[userId] = action.userId
                        it[followingUserId] = action.targetUserId
                        it[status] = 1
                        it[createdAt] = now
                        it[updatedAt] = now
                    }
                    return@transaction true
                }
            } else {
                Followers.deleteWhere {
                    (userId eq action.targetUserId) and (followingUserId eq action.userId)
                }
            }
            true
        } else {
            false
        }
    }
}


fun followBackUser(action: FollowAction): Boolean = transaction {
    println("followBackUser → Attempting follow back: userId=${action.userId}, targetUserId=${action.targetUserId}")

    // Prevent user from following themselves
    if (action.userId == action.targetUserId) {
        println("followBackUser → Cannot follow back yourself.")
        return@transaction false
    }

    // Check if the user has been blocked by the target
    val isBlocked = Followers
        .selectAll()
        .where {
            (Followers.userId eq action.userId) and
                    (Followers.followingUserId eq action.targetUserId) and
                    (Followers.status eq 3)
        }
        .any()

    println("followBackUser → Is user blocked by target? $isBlocked")
    if (isBlocked) {
        println("followBackUser → Follow back blocked due to status 3 (blocked).")
        return@transaction false
    }

    // Check if target user follows the current user (1=one-sided, 2=mutual)
    val existingFollow = Followers
        .selectAll()
        .where {
            (Followers.userId eq action.userId) and
                    (Followers.followingUserId eq action.targetUserId) and
                    (Followers.status inList listOf(1, 2))
        }
        .singleOrNull()

    println("followBackUser → Existing follow found? ${existingFollow != null}")
    if (existingFollow == null) {
        println("followBackUser → No existing follow from target to user.")
        return@transaction false
    }

    println("followBackUser → Updating status to mutual (2)")
    Followers.update({
        (Followers.userId eq action.userId) and
                (Followers.followingUserId eq action.targetUserId)
    }) {
        it[status] = 2
        it[updatedAt] = now
    }

    println("followBackUser → Follow back successful.")
    return@transaction true
}

fun blockUser(action: FollowAction): Boolean = transaction {

    if (action.userId == action.targetUserId) return@transaction false

    // Insert block
    Followers.insert {
        it[userId] = action.userId
        it[followingUserId] = action.targetUserId
        it[status] = 3
        it[createdAt] = now
        it[updatedAt] = now
    }

    true
}

fun unblockUser(action: FollowAction): Boolean = transaction {
    Followers.deleteWhere {
        (Followers.userId eq action.userId) and (Followers.followingUserId eq action.targetUserId) and (Followers.status eq 3)
    } > 0
}

fun isFollowingYou(userId: Int, otherUserId: Int): Boolean = transaction {
    Followers.selectAll().where {
        (Followers.userId eq userId) and (Followers.followingUserId eq otherUserId) and (Followers.status inList listOf(1, 2))
    }.any()
}

fun areYouFollowing(userId: Int, otherUserId: Int): Boolean = transaction {
    // Direct one-way follow check
    val isDirectFollow = Followers.selectAll().where {
        (Followers.userId eq otherUserId) and
                (Followers.followingUserId eq userId) and
                (Followers.status eq 1)
    }.any()

    if (isDirectFollow) {
        true
    } else {
        val followingStatus = Followers.selectAll().where {
            (Followers.userId eq userId) and (Followers.followingUserId eq otherUserId)
        }.map { it[Followers.status] }.singleOrNull()

        val followerStatus = Followers.selectAll().where {
            (Followers.userId eq otherUserId) and (Followers.followingUserId eq userId)
        }.map { it[Followers.status] }.singleOrNull()

        (followingStatus == 2) || (followerStatus == 2)
    }
}


fun isMutualRelationship(userId: Int, otherUserId: Int): Boolean {
    val relationshipExists = isFollowingYou(userId, otherUserId) || areYouFollowing(userId, otherUserId)

    if (!relationshipExists) return false

    return transaction {
        val followingStatus = Followers.selectAll().where {
            (Followers.userId eq userId) and (Followers.followingUserId eq otherUserId)
        }.map { it[Followers.status] }.singleOrNull()

        val followerStatus = Followers.selectAll().where {
            (Followers.userId eq otherUserId) and (Followers.followingUserId eq userId)
        }.map { it[Followers.status] }.singleOrNull()

        (followingStatus == 2) || (followerStatus == 2)
    }
}

// 1️⃣ Get all followers of a user (people who follow the user)
// 1️⃣ Get all followers of a user (status 1 or 2)
fun getFollowersBasicInfo(userId: Int): List<UserProfileResult> = transaction {
    Followers
        .join(UserInitials, onColumn = Followers.userId, otherColumn = UserInitials.id, joinType = JoinType.INNER)
        .selectAll().where { (Followers.followingUserId eq userId) and (Followers.status inList listOf(1, 2)) }
        .mapNotNull {
            val followerId = it[Followers.userId]
            getUserProfileDetails(followerId)
        }
}

// 2️⃣ Get all people the user is following (status 1 or 2)
fun getFollowingBasicInfo(userId: Int): List<UserProfileResult> = transaction {
    Followers
        .join(UserInitials, onColumn = Followers.followingUserId, otherColumn = UserInitials.id, joinType = JoinType.INNER)
        .selectAll().where { (Followers.userId eq userId) and (Followers.status inList listOf(1, 2)) }
        .mapNotNull {
            val followingId = it[Followers.followingUserId]
            getUserProfileDetails(followingId)
        }
}

// 3️⃣ NEW: Get all *mutuals* → people that the user follows *and* who also follow the user back
fun getMutualFollowersBasicInfo(userId: Int): List<UserProfileResult2> = transaction {
    // Get all users the user is following with status == 2
    val followingIds = Followers
        .selectAll()
        .where { (Followers.userId eq userId) and (Followers.status eq 2) }
        .map { it[Followers.followingUserId] }

    // Get all users that are following the user with status == 2
    val followersIds = Followers
        .selectAll()
        .where { (Followers.followingUserId eq userId) and (Followers.status eq 2) }
        .map { it[Followers.userId] }

    // Combine both lists, remove duplicates
    val mutualUserIds = (followingIds + followersIds).toSet()

    if (mutualUserIds.isEmpty()) return@transaction emptyList()

    // Return user profile details for all mutuals
    mutualUserIds.mapNotNull { userId -> getUserProfileDetails2(userId) }
}




// 3️⃣ Get counts of followers and following
fun getFollowerFollowingCounts(userId: Int): FollowCountResponse = transaction {
    val followersCount = Followers
        .selectAll().where {
            ((Followers.followingUserId eq userId) and (Followers.status inList listOf(1, 2))) or
                    ((Followers.userId eq userId) and (Followers.status eq 2))
        }.count()

    val followingCount = Followers
        .selectAll().where {
            ((Followers.userId eq userId) and (Followers.status inList listOf(1, 2))) or
                    ((Followers.followingUserId eq userId) and (Followers.status eq 2))
        }.count()

    FollowCountResponse(followersCount.toInt(), followingCount.toInt())
}





