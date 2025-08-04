package com.example.logic

import com.example.data.db_operations.clearOldNotifications
import com.example.data.db_operations.getNotificationsSince
import com.example.data.db_operations.markNotificationsAsViewed
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.routing.*
import io.ktor.server.response.*
import org.joda.time.DateTime

fun Route.notificationRoutes() {

    get("/notifications") {
        val userId = call.request.queryParameters["userId"]?.toIntOrNull()
            ?: return@get call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Missing userId"))

        val lastId = call.request.queryParameters["afterId"]?.toIntOrNull() ?: 0
        val result = getNotificationsSince(userId, lastId)

        call.respond(HttpStatusCode.OK, mapOf("notifications" to result))
    }

    post("/notifications/markViewed") {
        val userId = call.receiveParameters()["userId"]?.toIntOrNull()
            ?: return@post call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Missing userId"))
        markNotificationsAsViewed(userId)
        call.respond(HttpStatusCode.OK, mapOf("success" to true))
    }

    delete("/notifications/clear") {
        val userId = call.request.queryParameters["userId"]?.toIntOrNull()
            ?: return@delete call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Missing userId"))
        val beforeMillis = call.request.queryParameters["before"]?.toLongOrNull()
            ?: return@delete call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Missing timestamp"))

        val beforeDate = DateTime(beforeMillis)
        clearOldNotifications(userId, beforeDate)
        call.respond(HttpStatusCode.OK, mapOf("success" to true))
    }
}
