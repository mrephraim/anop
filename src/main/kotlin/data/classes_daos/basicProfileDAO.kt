package com.example.data.models

import kotlinx.serialization.Serializable
import org.jetbrains.exposed.sql.Table

object BasicProfile : Table("basic_profile") {
    val id = integer("id").autoIncrement() // Unique ID for the user profile
    val userId = integer("user_id").references(UserInitials.id) // Foreign key to the user_initials table
    val firstName = varchar("first_name", 100)
    val lastName = varchar("last_name", 100)
    val shortBio = text("short_bio").nullable()
    val about = text("about").nullable()
    val gender = enumerationByName("gender", 6, Gender::class)

    override val primaryKey = PrimaryKey(id)
}


object ProfilePictures : Table("profile_pictures") {
    val id = integer("id").autoIncrement()
    val userId = integer("user_id").references(UserInitials.id)
    val filePath = varchar("file_path", 255)
    override val primaryKey = PrimaryKey(id)
}



// Enum for Gender
enum class Gender {
    MALE, FEMALE, NULL
}

data class UserProfile(
    val userId: Int,  // Foreign key to the user_initials table
    val firstName: String,
    val lastName: String,
    val shortBio: String?,
    val about: String?,
    val gender: Gender
)

@Serializable
data class UserProfileRequest(
    val userId: Int,
    val firstName: String,
    val lastName: String,
    val username: String,
    val shortBio: String?,
    val about: String?,
    val gender: Gender
)

@Serializable
data class UpdateProfileRequest(
    val userId: Int,
    val firstName: String,
    val lastName: String,
    val username: String,
    val shortBio: String?,
    val about: String?
)



@Serializable
data class SetBasicProfileResponseResult(
    val message: String,
    val status: String = "success",  // default is "success", can be changed to "error" for failures
    val data: String? = null  // Optional field for any additional data to be returned
)

@Serializable
data class CheckUsernameRequest(val username: String)

@Serializable
data class UsernameCheckResponse(val available: Boolean)

@Serializable
data class UserProfileResult(
    val firstName: String,
    val lastName: String,
    val username: String,
    val email: String,
    val about: String?,
    val shortBio: String?,
    val profilePicturePath: String?
)

@Serializable
data class UserProfileResult2(
    val userId: Int,
    val firstName: String,
    val lastName: String,
    val username: String,
    val email: String,
    val about: String?,
    val shortBio: String?,
    val profilePicturePath: String?
)


@Serializable
data class ProfileRequest(
    val userId: Int,
    val deviceId: String,
    val refreshToken: String
)

@Serializable
data class ProfileResponse(
    val status: String,
    val data: UserProfileResult? = null,
    val message: String? = null
)


@Serializable
data class BasicProfileResponse(
    val userId: Int,
    val username: String,
    val profilePicturePath: String? = null
)
