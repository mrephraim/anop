package com.example.data.db_operations


import com.example.data.SecurityUtils
import com.example.data.models.*
import kotlinx.datetime.*
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq


fun isEmailExists(email: String): Boolean {
    return transaction {
        UserInitials
            .selectAll().where { UserInitials.email eq email }
            .limit(1)
            .any() // Returns true if at least one matching record is found
    }
}

fun isUsernameExists(username: String): Boolean {
    return transaction {
        UserInitials
            .selectAll().where { UserInitials.username eq username }
            .limit(1)
            .any() // Returns true if at least one matching record is found
    }
}



fun addUser(email: String, username: String, password: String): Int? {
    return transaction {
        if (isEmailExists(email)) {
            return@transaction null
        }

        val userId = UserInitials.insert {
            it[UserInitials.email] = email
            it[passKey] = password
            it[UserInitials.username] = username
            it[status] = 1
        } get UserInitials.id // Fetch the inserted ID

        userId
    }
}

// Store authentication code in the database
fun addAuthCode(userId: Int, type: Int, code: String) {
    transaction {
        AuthenticationCodes.insert {
            it[AuthenticationCodes.userId] = userId
            it[AuthenticationCodes.type] = type
            it[AuthenticationCodes.code] = code
            it[lastUpdated] = Clock.System.now().toLocalDateTime(TimeZone.UTC)
        }
    }
}

fun isVerificationCodeValid(userId: Int, code: String, type: Int): Boolean {
    return transaction {
        val oneMinuteAgo = Clock.System.now()
            .minus(2, DateTimeUnit.MINUTE)
            .toLocalDateTime(TimeZone.UTC)

        val codes = AuthenticationCodes
            .selectAll()
            .where { (AuthenticationCodes.userId eq userId) and (AuthenticationCodes.type eq type) }
            .orderBy(AuthenticationCodes.lastUpdated, SortOrder.DESC)
            .map { it[AuthenticationCodes.code] to it[AuthenticationCodes.lastUpdated] }

        println("🔍 Stored Codes: $codes | two minute ago: $oneMinuteAgo")

        codes.any { it.first == code && it.second >= oneMinuteAgo }
    }
}




fun updateUserStatus(userId: Int, newStatus: Int): Boolean {
    return transaction {
        val updatedRows = UserInitials.update({ UserInitials.id eq userId }) {
            it[status] = newStatus
        }
        updatedRows > 0
    }
}

fun deleteVerificationCode(userId: Int, type: Int) {
    transaction {
        AuthenticationCodes.deleteWhere {
            (AuthenticationCodes.userId eq userId) and (AuthenticationCodes.type eq type)
        }
    }
}

fun regenerateAuthCode(userId: Int, type: Int): String {
    return transaction {
        // Delete previous authentication codes for this user and type
        deleteVerificationCode(userId, type)

        // Generate a new 6-digit authentication code
        val newCode = (100000..999999).random().toString()

        // Insert the new authentication code
        AuthenticationCodes.insert {
            it[AuthenticationCodes.userId] = userId
            it[AuthenticationCodes.type] = type
            it[code] = newCode
            it[lastUpdated] = Clock.System.now().toLocalDateTime(TimeZone.UTC)
        }

        return@transaction newCode
    }
}

fun getUserEmailById(userId: Int): String? {
    return transaction {
        UserInitials
            .selectAll().where { UserInitials.id eq userId }
            .limit(1)
            .map { it[UserInitials.email] }
            .firstOrNull()
    }
}

fun isUserExists(usernameOrEmail: String): Int? {
    return transaction {
        UserInitials
            .select(UserInitials.id)
            .where { (UserInitials.username eq usernameOrEmail) or (UserInitials.email eq usernameOrEmail) }
            .limit(1)
            .map { it[UserInitials.id] }
            .firstOrNull()
    }
}

fun getUserPassword(userId: Int): String? {
    return transaction {
        UserInitials
            .select(UserInitials.passKey)
            .where { UserInitials.id eq userId }
            .map { it[UserInitials.passKey] }
            .firstOrNull()
    }
}

fun isNewDevice(userId: Int, deviceInfo: String): Boolean {
    return transaction {
        !LoginSessions
            .selectAll()
            .where { (LoginSessions.userId eq userId) and (LoginSessions.deviceInfo eq deviceInfo) }
            .limit(1)
            .any()
    }
}

fun resetUserPassword(userId: Int, newPassword: String): Boolean {
    return transaction {
        val hashedPassword = SecurityUtils.encryptPassword(newPassword)

        val updatedRows = UserInitials.update({ UserInitials.id eq userId }) {
            it[passKey] = hashedPassword
        }
        deleteVerificationCode(userId, 3)

        return@transaction updatedRows > 0
    }
}



fun isLatestVerificationCode(userId: Int, code: String, type: Int): Boolean {
    return transaction {
        AuthenticationCodes
            .selectAll()
            .where {
                (AuthenticationCodes.userId eq userId) and
                        (AuthenticationCodes.type eq type)
            }
            .orderBy(AuthenticationCodes.lastUpdated, SortOrder.DESC)
            .limit(1)
            .firstOrNull()?.get(AuthenticationCodes.code) == code
    }
}


fun storeLoginSession(userId: Int, deviceInfo: String, refreshToken: String) {
    transaction {
        LoginSessions.insert {
            it[this.userId] = userId
            it[this.deviceInfo] = deviceInfo
            it[this.refreshToken] = refreshToken
            it[this.lastLogin] = Clock.System.now().toLocalDateTime(TimeZone.UTC)
        }
    }
}


fun getUserStatus(userId: Int): Int? {
    return try {
        transaction {
            UserInitials
                .selectAll()
                .where { UserInitials.id eq userId }
                .limit(1)
                .map { it[UserInitials.status] }
                .firstOrNull()
        }
    } catch (e: Exception) {
        null
    }
}

fun getUserIdByEmail(email: String): Int? {
    return transaction {
        UserInitials
            .selectAll()
            .where { UserInitials.email eq email }
            .limit(1)
            .map { it[UserInitials.id] }
            .firstOrNull()
    }
}







