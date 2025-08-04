package com.example.logic

import com.example.data.SecurityUtils
import com.example.data.db_operations.*
import com.example.data.models.*
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction


fun generateUniqueUsername(email: String): String {
    val baseUsername = email.substringBefore("@") // Extract name before @
    var username = baseUsername
    var counter = 1

    // Keep generating a new username if it already exists
    while (isUsernameExists(username)) {
        username = "$baseUsername$counter"
        counter++
    }

    return username
}




fun Route.userAuth() {
    post("/signup") {
        try {
            val request = call.receive<UserRequest>()
            val email = request.email
            val password = request.password

            // Check if email already exists
            if (isEmailExists(email)) {
                val existingUserId = getUserIdByEmail(email)
                val status = existingUserId?.let { getUserStatus(it) }

                val userId = isUserExists(email)
                val storedPassword = userId?.let { getUserPassword(it) }

                if (storedPassword?.let { SecurityUtils.decryptPassword(it) } != password) {
                        call.respond(
                            HttpStatusCode.Conflict,
                            mapOf("error" to "Account with email already exists, please log in")
                        )
                        return@post
                }


                if (status == 1) {
                    // Resend verification code
                    val authCode = (100000..999999).random().toString()
                    addAuthCode(existingUserId, 1, authCode)
                    val (serviceEmail, serviceEmailPassword) = getEmailCredentials()
                    sendSingleEmail(
                        email,
                        "Verify Your Email",
                        "email_confirmation",
                        mapOf("CODE" to authCode),
                        serviceEmail,
                        serviceEmailPassword
                    )

                    call.respond(
                        HttpStatusCode.Created,
                        SignupResponse(
                            message = "User already exists but not verified. Verification code resent.",
                            userId = existingUserId,
                            userStatus = null
                        )
                    )
                    return@post
                }else{
                    call.respond(
                        HttpStatusCode.Conflict,
                        mapOf("error" to "Email already exists")
                    )
                    return@post
                }
            }

            // Generate a unique username
            val baseUsername = email.substringBefore("@")
            val uniqueUsername = generateUniqueUsername(baseUsername)

            // Hash password before storing
            val hashedPassword = SecurityUtils.encryptPassword(password)

            // Add user to database
            val userId = addUser(email, uniqueUsername, hashedPassword)

            if (userId != null) {
                val authCode = (100000..999999).random().toString()
                addAuthCode(userId, 1, authCode)

                val (serviceEmail, serviceEmailPassword) = getEmailCredentials()
                sendSingleEmail(
                    email,
                    "Verify Your Email",
                    "email_confirmation",
                    mapOf("CODE" to authCode),
                    serviceEmail,
                    serviceEmailPassword
                )

                val userStatus = getUserStatus(userId)

                call.respond(
                    HttpStatusCode.Created,
                    SignupResponse(
                        message = "User registered successfully. Please verify your email.",
                        userId = userId,
                        userStatus = userStatus
                    )
                )
            } else {
                call.respond(
                    HttpStatusCode.InternalServerError,
                    mapOf("error" to "Something went wrong, try again or contact support")
                )
            }
        } catch (e: Exception) {
            call.respond(
                HttpStatusCode.BadRequest,
                mapOf("error" to "Invalid request: ${e.localizedMessage}")
            )
        }
    }



    post("/signup/verify") {
        try {
            val request = call.receive<VerificationRequest>()
            val userId = request.userId
            val code = request.code

            if (!isVerificationCodeValid(userId, code, 1)) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid or expired code"))
                return@post
            }

            val statusUpdated = updateUserStatus(userId, 2)
            if (statusUpdated) {
                deleteVerificationCode(userId, 1)
                call.respond(HttpStatusCode.OK, mapOf("message" to "User verified successfully"))
            } else {
                call.respond(HttpStatusCode.InternalServerError, mapOf("error" to "Failed to update user status"))
            }
        } catch (e: Exception) {
            call.respond(HttpStatusCode.InternalServerError, mapOf("error" to e.localizedMessage))
        }
    }
    post("/signup/resend-email-verification-code") {
        try {
            val request = call.receive<ResendEmailVerificationRequest>()
            val newCode = regenerateAuthCode(request.userId, request.type)

            // Fetch the user's email
            val userEmail = getUserEmailById(request.userId)
            if (userEmail == null) {
                call.respond(HttpStatusCode.NotFound, "User not found")
                return@post
            }

            val (serviceEmail, serviceEmailPassword) = getEmailCredentials()

            try {
                sendSingleEmail(
                    userEmail,
                    "Verify Your Email",
                    "email_confirmation",
                    mapOf("CODE" to newCode),
                    serviceEmail,
                    serviceEmailPassword
                )

                call.respond(HttpStatusCode.OK, "New verification code sent to $userEmail")
            } catch (e: Exception) {
                call.respond(HttpStatusCode.InternalServerError, "Failed to send email: ${e.localizedMessage}")
            }

        } catch (e: Exception) {
            call.respond(HttpStatusCode.BadRequest, "Invalid request: ${e.localizedMessage}")
        }
    }

    post("/google-auth") {
        try {
            val request = call.receive<GoogleLoginRequest>()
            val payload = verifyGoogleTokenAndGetPayload(request.idToken)

            if (payload == null) {
                call.respond(HttpStatusCode.BadRequest, mapOf("message" to "Invalid Google token."))
                return@post
            }

            val exists = transaction {
                UserInitials.selectAll().where { UserInitials.email eq (payload.email ?: "") }.count() > 0
            }

            val result = if (exists) {
                loginWithGoogle(payload, request.idToken, request.deviceInfo)
            } else {
                signupWithGoogle(payload, request.idToken, request.deviceInfo)
            }

            if (result.success) {
                call.respond(HttpStatusCode.OK, result)
            } else {
                call.respond(HttpStatusCode.BadRequest, result)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            call.respond(HttpStatusCode.InternalServerError, mapOf("message" to "Internal server error: ${e.message}"))
        }
    }

}


