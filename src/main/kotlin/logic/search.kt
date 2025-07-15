package com.example.logic

import com.example.data.db_operations.searchCommunities
import com.example.data.db_operations.searchPosts
import com.example.data.db_operations.searchUsers
import io.ktor.http.*
import io.ktor.server.routing.*
import io.ktor.server.response.*

fun Route.searchRoute(){
    get("/search") {
        val q = call.request.queryParameters["q"]?.trim()?.takeIf { it.isNotEmpty() }
            ?: return@get call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Missing query"))

        val type = call.request.queryParameters["type"]?.toIntOrNull()
            ?: return@get call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Missing type"))

        val limit = call.request.queryParameters["limit"]?.toIntOrNull() ?: 20

        val userId = call.request.queryParameters["userId"]?.toIntOrNull()

        val results = when (type) {
            1 -> {
                if (userId == null) {
                    return@get call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Missing userId for post search"))
                }
                searchPosts(q, userId, limit)
            }
            2 -> searchUsers(q, limit)
            3 -> searchCommunities(q, limit)
            else -> return@get call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid type"))
        }

        call.respond(mapOf("results" to results))
    }

}

