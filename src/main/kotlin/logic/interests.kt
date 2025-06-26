package com.example.logic

import com.example.data.classes_daos.InterestCategoryInput
import com.example.data.classes_daos.UserInterestInput
import com.example.data.db_operations.*
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.interestsRoutes() {

    // Add new interest category
    post("/interests") {
        val input = call.receive<InterestCategoryInput>()
        val result = addInterestCategory(input)
        if (result != null)
            call.respond(HttpStatusCode.Created, result)
        else
            call.respond(HttpStatusCode.InternalServerError, mapOf("error" to "Failed to add interest."))
    }

    // Get all interest categories
    get("/interests") {
        val interests = getAllInterestCategories()
        call.respond(HttpStatusCode.OK, interests)
    }

    // User selects interests
    post("/userInterests") {
        val input = call.receive<UserInterestInput>()
        val result = addUserInterests(input)
        call.respond(HttpStatusCode.Created, result)
    }

    // Get user interests
    get("/userInterests/{userId}") {
        val userId = call.parameters["userId"]?.toIntOrNull()
        if (userId == null) {
            call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid userId."))
            return@get
        }
        val result = getUserInterests(userId)
        call.respond(HttpStatusCode.OK, result)
    }

    get("/suggestCategories") {
        val selected = call.request.queryParameters.getAll("selected")?.toList() ?: emptyList()
        val excluded = call.request.queryParameters.getAll("excluded")?.toList() ?: emptyList()
        val suggestions = suggestRelatedCategories(selected, excluded)
        call.respond(HttpStatusCode.OK, suggestions)
    }

    get("/recommendUsers/{userId}") {
        val userId = call.parameters["userId"]?.toIntOrNull()
        if (userId == null) {
            call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid userId"))
            return@get
        }
        val recommendations = recommendUsersByInterest(userId)
        call.respond(HttpStatusCode.OK, recommendations)
    }


    get("interests/search") {
        val query = call.request.queryParameters["query"] ?: ""
        try {
            val suggestions = getSuggestedInterestCategories(query)
            call.respond(HttpStatusCode.OK, suggestions)
        } catch (e: Exception) {
            call.respond(HttpStatusCode.InternalServerError, mapOf("error" to (e.message ?: "Unknown error")))
        }
    }

}
