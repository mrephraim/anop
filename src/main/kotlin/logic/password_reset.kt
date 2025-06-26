package com.example.logic

import com.example.data.db_operations.*
import com.example.data.models.*
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.routing.*
import io.ktor.server.response.*


fun Route.passwordResetRequestRoute() {
    post("/request-password-reset") {
        try {
            val request = call.receive<PasswordResetRequest>()
            val userId = isUserExists(request.emailOrUsername)

            if (userId == null) {
                call.respond(HttpStatusCode.OK, PasswordResetResponse(0, 0, "Account not found."))
                return@post
            }

            val email = getUserEmailById(userId) ?: run {
                call.respond(HttpStatusCode.OK, PasswordResetResponse(0, 0, "Email not found for user, contact support please"))
                return@post
            }

            // Generate and send reset code
            val resetCode = regenerateAuthCode(userId, 3) // Type 3 for password reset
            val (serviceEmail, serviceEmailPassword) = getEmailCredentials()
            sendSingleEmail(
                email,
                "Password Reset Request",
                "password_reset",
                mapOf("CODE" to resetCode),
                serviceEmail,
                serviceEmailPassword
            )

            call.respond(HttpStatusCode.OK, PasswordResetResponse( 1, userId, "Password reset email sent."))
        } catch (e: Exception) {
            call.respond(HttpStatusCode.InternalServerError, PasswordResetResponse( 0, null, "An error occurred: ${e.message}"))
            e.printStackTrace()
        }
    }

    post("/verify-reset-code") {
        try {
            val request = call.receive<PasswordResetVerificationRequest>()

            if (!isVerificationCodeValid(request.userId, request.code, 3)) {
                call.respond(HttpStatusCode.BadRequest, mapOf("message" to "Invalid or expired verification code."))
                return@post
            }

            val response = PasswordResetVerificationResponse(
                success = true,
                message = "Verification successful. You may now reset your password.",
                userId = request.userId,
                verificationCode = request.code
            )
            call.respond(HttpStatusCode.OK, response)
        } catch (e: Exception) {
            call.respond(HttpStatusCode.InternalServerError, mapOf("message" to "An error occurred: ${e.message}"))
            e.printStackTrace()
        }
    }


    post("/reset-password") {
        try {
            val request = call.receive<PasswordUpdateRequest>()

            if (!isLatestVerificationCode(request.userId, request.code, 3)) {
                call.respond(HttpStatusCode.Unauthorized, mapOf("message" to "Unauthorized access or expired code."))
                return@post
            }

            if (resetUserPassword(request.userId, request.newPassword)) {
                call.respond(HttpStatusCode.OK, mapOf("message" to "Password reset successful. You can now log in with your new password."))
            } else {
                call.respond(HttpStatusCode.BadRequest, mapOf("message" to "Failed to reset password. Please try again."))
            }
        } catch (e: Exception) {
            call.respond(HttpStatusCode.InternalServerError, mapOf("message" to "An error occurred: ${e.message}"))
            e.printStackTrace()
        }
    }


}
