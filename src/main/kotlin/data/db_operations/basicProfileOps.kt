package com.example.data.db_operations

import com.example.data.models.*
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction
import java.io.File

fun saveProfilePictureToDb(userId: Int, filePath: String): Boolean {
    return try {
        transaction {
            ProfilePictures.insert {
                it[ProfilePictures.userId] = userId
                it[ProfilePictures.filePath] = filePath
            }
        }
        true
    } catch (e: Exception) {
        e.printStackTrace()
        false
    }
}
fun insertUserProfile(userId: Int, userProfile: UserProfile): Int? {
    return transaction {
        // Check if the profile already exists by userId
        val existingProfile = BasicProfile
            .selectAll()
            .where { BasicProfile.userId eq userId }
            .singleOrNull()

        if (existingProfile != null) {
            // If the profile exists, return null
            return@transaction null
        }

        // Insert the user profile into the database
        val profileId = BasicProfile.insert {
            it[BasicProfile.userId] = userId
            it[firstName] = userProfile.firstName
            it[lastName] = userProfile.lastName
            it[shortBio] = userProfile.shortBio
            it[about] = userProfile.about
            it[gender] = userProfile.gender
        } get BasicProfile.id  // Fetch the inserted profile ID

        profileId
    }
}

fun updateUsername(userId: Int, username: String): Boolean {
    return transaction {
        val updatedRows = UserInitials.update({ UserInitials.id eq userId }) {
            it[UserInitials.username] = username
        }
        updatedRows > 0
    }
}

fun isUserProfileExists(userId: Int): Boolean {
    return transaction {
        BasicProfile
            .selectAll()
            .where { BasicProfile.userId eq userId }
            .limit(1)
            .any() // Returns true if at least one matching record is found
    }
}

fun deleteProfilePicture(userId: Int): Boolean {
    return try {
        val filePath = transaction {
            ProfilePictures
                .selectAll()
                .where { ProfilePictures.userId eq userId }
                .limit(1)
                .map { it[ProfilePictures.filePath] }
                .firstOrNull()
        } ?: return false

        val file = File(filePath)
        if (file.exists()) {
            file.delete()
            println("🗑️ File deleted: $filePath")
        }

        transaction {
            ProfilePictures.deleteWhere { ProfilePictures.userId eq userId }
        }

        true
    } catch (e: Exception) {
        e.printStackTrace()
        false
    }
}

fun isLoginSessionValid(userId: Int, deviceId: String, refreshToken: String): Boolean {
    return transaction {
        LoginSessions
            .selectAll()
            .where {
                (LoginSessions.userId eq userId) and
                        (LoginSessions.deviceInfo eq deviceId) and
                        (LoginSessions.refreshToken eq refreshToken)
            }
            .any()
    }
}


fun getUserProfileDetails(userId: Int): UserProfileResult? {
    return transaction {
        val userInitials = UserInitials
            .selectAll()
            .where { UserInitials.id eq userId }
            .singleOrNull()

        val basicProfile = BasicProfile
            .selectAll()
            .where { BasicProfile.userId eq userId }
            .singleOrNull()

        val profilePicture = ProfilePictures
            .selectAll()
            .where { ProfilePictures.userId eq userId }
            .singleOrNull()

        if (userInitials != null && basicProfile != null) {
            val filePath = profilePicture?.get(ProfilePictures.filePath)
            val absoluteFilePath = "https://grub-hardy-actively.ngrok-free.app/profile-picture/${filePath}"
            UserProfileResult(
                firstName = basicProfile[BasicProfile.firstName],
                lastName = basicProfile[BasicProfile.lastName],
                username = userInitials[UserInitials.username],
                email = userInitials[UserInitials.email],
                about = basicProfile[BasicProfile.about],
                shortBio = basicProfile[BasicProfile.shortBio],
                profilePicturePath = absoluteFilePath
            )
        } else null
    }
}

fun getUserProfileDetails2(userId: Int): UserProfileResult2? {
    return transaction {
        val userInitials = UserInitials
            .selectAll()
            .where { UserInitials.id eq userId }
            .singleOrNull()

        val basicProfile = BasicProfile
            .selectAll()
            .where { BasicProfile.userId eq userId }
            .singleOrNull()

        val profilePicture = ProfilePictures
            .selectAll()
            .where { ProfilePictures.userId eq userId }
            .singleOrNull()

        if (userInitials != null && basicProfile != null) {
            val filePath = profilePicture?.get(ProfilePictures.filePath)
            val absoluteFilePath = "https://grub-hardy-actively.ngrok-free.app/profile-picture/${filePath}"
            UserProfileResult2(
                firstName = basicProfile[BasicProfile.firstName],
                lastName = basicProfile[BasicProfile.lastName],
                username = userInitials[UserInitials.username],
                email = userInitials[UserInitials.email],
                about = basicProfile[BasicProfile.about],
                shortBio = basicProfile[BasicProfile.shortBio],
                profilePicturePath = absoluteFilePath,
                userId = userInitials[UserInitials.id]
            )
        } else null
    }
}

fun getUserBasicProfile(userId: Int): BasicProfileResponse? = transaction {
    val profileRow = BasicProfile
        .selectAll().where { BasicProfile.userId eq userId }
        .singleOrNull()

    profileRow?.let { row ->
        val profilePicturePath = ProfilePictures
            .selectAll().where { ProfilePictures.userId eq userId }
            .orderBy(ProfilePictures.id to SortOrder.DESC)
            .limit(1)
            .singleOrNull()
            ?.get(ProfilePictures.filePath)

        BasicProfileResponse(
            userId = userId,
            username = "${row[BasicProfile.firstName]} ${row[BasicProfile.lastName]}",
            profilePicturePath = "https://grub-hardy-actively.ngrok-free.app/profile-picture/${profilePicturePath}"
        )
    }
}


