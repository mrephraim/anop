package com.example.logic

import com.example.data.classes_daos.FollowAction
import com.example.data.classes_daos.FollowCheckRequest
import com.example.data.db_operations.*
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.routing.*
import io.ktor.server.response.*

fun Route.followRoutes() {

    // Check if someone is following you
    post("/follow/isFollowingYou") {
        val request = call.receive<FollowCheckRequest>()
        val result = isFollowingYou(request.userId, request.otherUserId)
        call.respond(mapOf("isFollowingYou" to result))
    }

    post("/follow/areYouFollowing") {
        val request = call.receive<FollowCheckRequest>()
        val result = areYouFollowing(request.userId, request.otherUserId)
        call.respond(mapOf("areYouFollowing" to result))
    }


    // Follow a user

    post("/follow") {
        val action = call.receive<FollowAction>()
        val result = followUser(action)
        if (result) {
            call.respond(HttpStatusCode.OK)
        } else {
            call.respond(HttpStatusCode.InternalServerError)
        }
    }

    post("/unfollow_user") {
        try {
            val action = call.receive<FollowAction>()
            val success = unfollowUser(action)

            if (success) {
                call.respond(HttpStatusCode.OK, mapOf("status" to "success", "message" to "Unfollowed successfully"))
            } else {
                call.respond(HttpStatusCode.BadRequest, mapOf("status" to "error", "message" to "No follow relationship found"))
            }

        } catch (e: Exception) {
            e.printStackTrace()
            call.respond(HttpStatusCode.InternalServerError, mapOf("status" to "error", "message" to "Failed to unfollow"))
        }
    }


    post("/followBack") {
        val action = call.receive<FollowAction>()
        val result = followBackUser(action)
        if (result) {
            call.respond(HttpStatusCode.OK)
        } else {
            call.respond(HttpStatusCode.InternalServerError)
        }
    }



    // Block a user
    post("/block") {
        val action = call.receive<FollowAction>()
        val result = blockUser(action)
        if (result) {
            call.respond(mapOf("message" to "Blocked successfully"))
        } else {
            call.respond(mapOf("message" to "Failed to block user"))
        }
    }

    // Optional: Unblock user (if needed)
    post("/unblock") {
        val action = call.receive<FollowAction>()
        val result = unblockUser(action)
        if (result) {
            call.respond(mapOf("message" to "Unblocked successfully"))
        } else {
            call.respond(mapOf("message" to "Failed to unblock user"))
        }
    }

    // 1️⃣ Route: Get basic info on all people following you
    get("/followers/{userId}") {
        val userId = call.parameters["userId"]?.toIntOrNull()
        if (userId == null) {
            call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid userId"))
            return@get
        }
        val followers = getFollowersBasicInfo(userId)
        call.respond(HttpStatusCode.OK, followers)
    }

    // 2️⃣ Route: Get basic info on all people you are following
    get("/following/{userId}") {
        val userId = call.parameters["userId"]?.toIntOrNull()
        if (userId == null) {
            call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid userId"))
            return@get
        }
        val following = getFollowingBasicInfo(userId)
        call.respond(HttpStatusCode.OK, following)
    }

    // 3️⃣ Route: Get follower and following counts
    get("/followCount/{userId}") {
        val userId = call.parameters["userId"]?.toIntOrNull()
        if (userId == null) {
            call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid userId"))
            return@get
        }
        val count = getFollowerFollowingCounts(userId)
        call.respond(HttpStatusCode.OK, count)
    }

    get("/mutuals/{userId}") {
        val userId = call.parameters["userId"]?.toIntOrNull()
        if (userId == null) {
            call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid userId"))
            return@get
        }
        val mutuals = getMutualFollowersBasicInfo(userId)
        call.respond(HttpStatusCode.OK, mutuals)
    }

}
