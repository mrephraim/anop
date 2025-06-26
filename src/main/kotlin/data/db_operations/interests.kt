package com.example.data.db_operations

import com.example.data.classes_daos.*
import com.example.data.classes_daos.InterestCategoryTable.interest
import com.example.data.classes_daos.InterestCategoryTable.relatedInterests
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.insertIgnore
import org.jetbrains.exposed.sql.lowerCase
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction

fun addInterestCategory(input: InterestCategoryInput): InterestCategoryOutput = transaction {
    // Insert the record and get the resulting row
    val insertedId = InterestCategoryTable.insert {
        it[interest] = input.interest
        it[relatedInterests] = Json.encodeToString(input.relatedInterests)
    } get InterestCategoryTable.id // 🔥 Get the generated ID directly

    // Return the output
    InterestCategoryOutput(insertedId, input.interest, input.relatedInterests)
}

// Get All Interests
fun getAllInterestCategories(): List<InterestCategoryResponse> = transaction {
    InterestCategoryTable.selectAll().map {
        InterestCategoryResponse(
            id = it[InterestCategoryTable.id],
            name = it[interest],
            relatedInterests = Json.decodeFromString(it[relatedInterests])
        )
    }
}

fun addUserInterests(input: UserInterestInput): UserInterestOutput = transaction {
    input.interestIds.forEach { interestId ->
        UserInterestsTable.insertIgnore {
            it[userId] = input.userId
            it[UserInterestsTable.interestId] = interestId
        }
    }
    // Return the correct output type
    UserInterestOutput(
        userId = input.userId,
        interestIds = input.interestIds
    )
}

fun getUserInterests(userId: Int): UserInterestOutput = transaction {
    val interestIds = UserInterestsTable
        .selectAll()
        .where { UserInterestsTable.userId eq userId }
        .map { it[UserInterestsTable.interestId] }
    UserInterestOutput(userId, interestIds)
}


fun suggestRelatedCategories(selectedCategories: List<String>, alreadySuggested: List<String>): List<InterestCategoryOutput> = transaction {
    // Normalize inputs (lowercase and trim)
    val normalizedSelected = selectedCategories.map { it.lowercase().trim() }
    val normalizedExcluded = alreadySuggested.map { it.lowercase().trim() }

    // Fetch categories with relatedInterests containing any of the selected
    InterestCategoryTable
        .selectAll()
        .mapNotNull { row ->
            val category = row[InterestCategoryTable.interest].lowercase().trim()
            val related = Json.decodeFromString<List<String>>(row[InterestCategoryTable.relatedInterests])
                .map { it.lowercase().trim() }

            // Check overlap with normalized selected categories
            val matches = related.any { normalizedSelected.contains(it) }

            // Exclude already suggested categories and exact matches
            if (matches && !normalizedExcluded.contains(category) && !normalizedSelected.contains(category)) {
                InterestCategoryOutput(row[InterestCategoryTable.id], row[InterestCategoryTable.interest], related)
            } else null
        }
        .distinctBy { it.id } // Remove duplicates
}


fun recommendUsersByInterest(userId: Int, limit: Int = 20): List<RecommendedUserProfile> = transaction {
    val userInterestsOutput = getUserInterests(userId)
    val userInterestIds = userInterestsOutput.interestIds.toSet()

    if (userInterestIds.isEmpty()) return@transaction emptyList()

    val allOtherUsersInterests = UserInterestsTable
        .selectAll()
        .where { UserInterestsTable.userId neq userId }
        .map { it[UserInterestsTable.userId] to it[UserInterestsTable.interestId] }
        .groupBy({ it.first }, { it.second })

    val similarityScores = mutableListOf<Pair<Int, Int>>() // Pair of (userId, commonInterestCount)

    for ((otherUserId, otherInterests) in allOtherUsersInterests) {
        if (areYouFollowing(userId, otherUserId)) continue
        val commonCount = otherInterests.count { it in userInterestIds }
        if (commonCount > 0) {
            similarityScores.add(otherUserId to commonCount)
        }
    }

    similarityScores
        .sortedByDescending { it.second }
        .take(limit)
        .mapNotNull { (recommendedUserId, _) ->
            getUserProfileDetails2(recommendedUserId)?.let {
                RecommendedUserProfile(
                    userId = it.userId,
                    username = it.username,
                    fullName = "${it.firstName} ${it.lastName}",
                    profilePicturePath = it.profilePicturePath
                )
            }
        }
}

fun searchInterestCategories(query: String, limit: Int = 15): List<InterestCategory> {
    return transaction {
        InterestCategoryTable
            .selectAll().where{ interest.lowerCase() like "%${query.lowercase()}%" }
            .orderBy(interest, SortOrder.ASC)
            .limit(limit)
            .map {
                InterestCategory(
                    id = it[InterestCategoryTable.id],
                    name = it[interest],
                    relatedInterests = it[relatedInterests]
                )
            }
    }
}

suspend fun getSuggestedInterestCategories(query: String): List<InterestCategory> {
    if (query.isBlank()) return emptyList()
    return searchInterestCategories(query)
}

fun hasUserSetInterests(userId: Int): Boolean = transaction {
    UserInterestsTable.selectAll().where {
        UserInterestsTable.userId eq userId
    }.limit(1).any() // true if at least one interest is set
}


