package com.example.data.classes_daos

import org.jetbrains.exposed.sql.Table
import kotlinx.serialization.Serializable

// InterestCategoryTable.kt
object InterestCategoryTable : Table("interest_category") {
    val id = integer("id").autoIncrement()
    val interest = varchar("interest", 255)
    val relatedInterests = text("related_interests") // JSON array as String
    override val primaryKey = PrimaryKey(id)
}

// UserInterestsTable.kt
object UserInterestsTable : Table("user_interests") {
    val id = integer("id").autoIncrement()
    val userId = integer("user_id")
    val interestId = integer("interest_id").references(InterestCategoryTable.id)
    override val primaryKey = PrimaryKey(id)
}

@Serializable data class InterestCategoryInput(val interest: String, val relatedInterests: List<String>)
@Serializable data class InterestCategoryOutput(val id: Int, val interest: String, val relatedInterests: List<String>)
@Serializable data class UserInterestInput(val userId: Int, val interestIds: List<Int>)
@Serializable data class UserInterestOutput(val userId: Int, val interestIds: List<Int>)


@Serializable
data class InterestCategory(
    val id: Int,
    val name: String,
    val relatedInterests: String
)

@Serializable
data class RecommendedUserProfile(
    val userId: Int,
    val username: String,
    val fullName: String,
    val profilePicturePath: String?
)


@Serializable
data class InterestCategoryResponse(
    val id: Int,
    val name: String,
    val relatedInterests: List<String>
)

