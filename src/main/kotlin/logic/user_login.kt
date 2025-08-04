package com.example.logic

import com.example.data.SecurityUtils
import com.example.data.TokenUtils
import com.example.data.db_operations.*
import com.example.data.models.*
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*


fun loginUser(usernameOrEmail: String, password: String, deviceInfo: DeviceInfo): LoginResult {
    val userId = isUserExists(usernameOrEmail) ?: return LoginResult(false, "User does not exist.")
    val storedPassword = getUserPassword(userId)

    if (storedPassword.isNullOrBlank() || storedPassword == "null" || storedPassword == "") {
        return LoginResult(false, "Please use reset password to set a new password to be able to login.")
    }


    if (SecurityUtils.decryptPassword(storedPassword) != password) {
        return LoginResult(false, "Invalid password.")
    }

    val userEmail = getUserEmailById(userId) ?: return LoginResult(false, "User email not found.")
    val (serviceEmail, serviceEmailPassword) = getEmailCredentials()

    val newUser = if (isUserProfileExists(userId)){
        if (hasUserSetInterests(userId)){
            0 //full settings done
        }else{
            3 //still remaining setting interests
        }
    }else{
        1 // no settings done at all
    }

    if (isNewDevice(userId, deviceInfo.deviceId)) {
        val newCode = regenerateAuthCode(userId, 2)
        sendSingleEmail(
            userEmail,
            "New Device Detected, Confirm it's you",
            "new_device",
            mapOf("CODE" to newCode, "OS_NAME" to deviceInfo.osName, "OS_VERSION" to deviceInfo.osVersion),
            serviceEmail,
            serviceEmailPassword
        )
        return LoginResult(
            success = true,
            message = "Login successful, please verify the new device with the code sent to your email.",
            userId = userId,
            userStatus = 2,
            newUser = newUser,
            newDeviceStatus = 1
        )
    }

    // No new device detected, generate a refresh token and store the session
    val refreshToken = TokenUtils.generateSecureToken()
    storeLoginSession(userId, deviceInfo.deviceId, refreshToken)

    return LoginResult(
        success = true,
        message = "Login successful, no new device detected.",
        userId = userId,
        userStatus = 2,
        newUser = newUser,
        refreshToken = refreshToken
    )
}

fun verifyNewDevice(userId: Int, code: String, deviceInfo: DeviceInfo): LoginResult {
    if (!isVerificationCodeValid(userId, code, 2)) {
        return LoginResult(false, "Invalid or expired verification code.")
    }

    val refreshToken = TokenUtils.generateSecureToken()
    storeLoginSession(userId, deviceInfo.deviceId, refreshToken)
    deleteVerificationCode(userId, 2)

    val newUser = if (isUserProfileExists(userId)) 0 else 1

    return LoginResult(
        success = true,
        message = "Device verification successful. You are now logged in.",
        userId = userId,
        userStatus = 2,
        newUser = newUser,
        refreshToken = refreshToken
    )
}


fun resendDeviceVerificationCode(userId: Int, deviceInfo: DeviceInfo): Boolean {
    val userEmail = getUserEmailById(userId) ?: return false
    val (serviceEmail, serviceEmailPassword) = getEmailCredentials()

    val newCode = regenerateAuthCode(userId, 2) // Generate a new authentication code

    sendSingleEmail(
        userEmail,
        "New Device Detected, Confirm it's you",
        "new_device",
        mapOf("CODE" to newCode, "OS_NAME" to deviceInfo.osName, "OS_VERSION" to deviceInfo.osVersion),
        serviceEmail,
        serviceEmailPassword
    )

    return true
}


fun Route.loginRoute() {
    post("/login") {
        try {
            val request = call.receive<LoginRequest>()
            val result = loginUser(request.usernameOrEmail, request.password, request.deviceInfo)

            if (result.success) {
                val userId = result.userId

                // Get status
                val status = userId?.let { getUserStatus(it) }

                if (status == 1) {
                    // Generate verification code
                    val authCode = (100000..999999).random().toString()
                    addAuthCode(userId, 1, authCode)

                    val email = getUserEmailById(userId)
                    val (serviceEmail, serviceEmailPassword) = getEmailCredentials()

                    if (email != null) {
                        sendSingleEmail(
                            email,
                            "Verify Your Email",
                            "email_confirmation",
                            mapOf("CODE" to authCode),
                            serviceEmail,
                            serviceEmailPassword
                        )
                    }
                }

                // Respond with login result including user status
                call.respond(HttpStatusCode.OK, result.copy(userStatus = status))
            } else {
                call.respond(HttpStatusCode.Unauthorized, mapOf("message" to result.message))
            }
        } catch (e: Exception) {
            call.respond(HttpStatusCode.InternalServerError, mapOf("message" to "An error occurred: ${e.message}"))
            e.printStackTrace()
        }
    }

    post("/verify-device") {
        try {
            // Get the request body containing verification code and device info
            val request = call.receive<DeviceVerificationRequest>()

            // Assuming the userId is stored in the session or received from the request
            // In this case, you can pass the userId as part of the request or from the session cookie
            val userId = request.userId  // Ensure you retrieve this from session or JWT token

            // Call the function to verify the device
            val result = verifyNewDevice(userId, request.code, request.deviceInfo)

            if (result.success) {
                // Return successful login result
                call.respond(HttpStatusCode.OK, result)
            } else {
                // Return failure message if the code verification failed
                call.respond(HttpStatusCode.BadRequest, mapOf("message" to result.message))
            }
        } catch (e: Exception) {
            // Handle any errors during the process
            call.respond(HttpStatusCode.InternalServerError, mapOf("message" to "An error occurred: ${e.message}"))
            e.printStackTrace()
        }
    }

    post("/resend-device-verification") {
        try {
            val request = call.receive<ResendDeviceVerificationRequest>()

            val success = resendDeviceVerificationCode(request.userId, request.deviceInfo)

            if (success) {
                call.respond(HttpStatusCode.OK, mapOf("message" to "New verification code sent successfully."))
            } else {
                call.respond(HttpStatusCode.BadRequest, mapOf("message" to "Failed to send verification code."))
            }
        } catch (e: Exception) {
            call.respond(HttpStatusCode.InternalServerError, mapOf("message" to "An error occurred: ${e.message}"))
            e.printStackTrace()
        }
    }

}



