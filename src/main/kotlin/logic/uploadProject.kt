package com.example.logic

import com.example.data.classes_daos.UploadProjectStageRequest
import com.example.data.db_operations.isLoginSessionValid
import com.example.data.db_operations.uploadProjectStage
import com.example.data.models.ProfileResponse
import io.ktor.server.routing.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.http.*

fun Route.projectRoutes() {
    post("/project/upload-stage") {
        try {
            // Parse the incoming request body as UploadProjectRequestWrapper (wrapper contains userId, projectId, stage, data)
            val request = call.receive<UploadProjectStageRequest>()

            if (!isLoginSessionValid(request.userId, request.deviceId, request.refreshToken)) {
                call.respond(HttpStatusCode.Unauthorized, ProfileResponse(status = "error", message = "Invalid session"))
                return@post
            }

            val userId = request.userId
            val projectId = request.projectId
            val stage = request.stage
            val data = request.data

            // Call your upload function
            val resultMessage = uploadProjectStage(userId, projectId, stage, data)

            call.respond(HttpStatusCode.OK, mapOf("message" to resultMessage))
        } catch (e: Exception) {
            call.respond(HttpStatusCode.BadRequest, mapOf("error" to (e.message ?: "Unknown error")))
        }
    }
}
