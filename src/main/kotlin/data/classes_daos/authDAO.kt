package com.example.data.models


import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.kotlin.datetime.datetime
import kotlinx.datetime.TimeZone
import org.jetbrains.exposed.sql.ReferenceOption


/*
Data classes and objects for everything authentication
*/
@Serializable
data class UserRequest(val email: String, val password: String)

@Serializable
data class SignupResponse(
    val message: String,
    val userId: Int,
    val userStatus: Int?,
)

@Serializable
data class VerificationRequest(
    val userId: Int,
    val code: String
)

// Data class for login request payload
@Serializable
data class LoginRequest(
    val usernameOrEmail: String,
    val password: String,
    val deviceInfo: DeviceInfo
)

@Serializable
data class DeviceInfo(
    val osName: String,
    val osVersion: String,
    val deviceId: String
)

@Serializable
data class LoginResult(
    val success: Boolean,
    val message: String,
    val userId: Int? = null,
    val userStatus: Int? = null,
    val newUser: Int? = null,
    val newDeviceStatus: Int? = null, // 1 for new device verification required, 2 for no new device
    val refreshToken: String? = null // Secure refresh token for authentication
)


@Serializable
data class DeviceVerificationRequest(
    val userId: Int,
    val code: String,
    val deviceInfo: DeviceInfo
)

@Serializable
data class ResendDeviceVerificationRequest(
    val userId: Int,
    val deviceInfo: DeviceInfo
)

@Serializable
data class ResendEmailVerificationRequest(val userId: Int, val type: Int)

@Serializable
data class PasswordResetRequest(val emailOrUsername: String)

@Serializable
data class PasswordResetResponse(
    val success: Int,
    val userId: Int? = null,
    val message: String
)

@Serializable
data class PasswordResetVerificationRequest(
    val userId: Int,
    val code: String
)

@Serializable
data class PasswordUpdateRequest(
    val userId: Int,
    val code: String,
    val newPassword: String
)

    @Serializable
    data class PasswordResetVerificationResponse(
        val success: Boolean,
        val message: String,
        val userId: Int? = null,
        val verificationCode: String? = null
    )


// Table definition
object UserInitials : Table("user_initials") {
    val id = integer("id").autoIncrement()
    val email = varchar("email", 255).uniqueIndex()
    val passKey = varchar("pass_key", 255)
    val status = integer("status")
    val username = varchar("username", 255).uniqueIndex()

    override val primaryKey = PrimaryKey(id)
}

object AuthenticationCodes : Table("authentication_codes") {
    val id = integer("id").autoIncrement()
    val userId = reference("user_id", UserInitials.id) // Assuming Users is your user table
    val type = integer("type") // Represents different operations using the auth code
    val code = varchar("code", 6) // 6-digit authentication code
    val lastUpdated = datetime("created_at").default(kotlinx.datetime.Clock.System.now().toLocalDateTime(TimeZone.UTC))// Optional for expiry tracking

    override val primaryKey = PrimaryKey(id)
}

object LoginSessions : Table() {
    val id = integer("id").autoIncrement()
    val userId = integer("user_id").references(UserInitials.id, onDelete = ReferenceOption.CASCADE)
    val deviceInfo = varchar("device_info", 255) // Stores device details
    val refreshToken = varchar("refresh_token", 512) // Secure refresh token
    val lastLogin = datetime("last_login").default(kotlinx.datetime.Clock.System.now().toLocalDateTime(TimeZone.UTC))

    override val primaryKey = PrimaryKey(id)
}











