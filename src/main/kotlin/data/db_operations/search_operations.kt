package com.example.data.db_operations

import com.example.data.classes_daos.*
import com.example.data.models.BasicProfile
import com.example.data.models.ProfilePictures
import com.example.data.models.UserInitials
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.inList
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.lowerCase
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction

suspend fun searchPosts(query: String, limit: Int = 20, userId: Int): List<FullPostResponse> {
    return transaction {
        val posts = Posts.selectAll().map { it[Posts.id].value to it }.toMap()

        // Fetch hashtags
        val hashtags = PostHashtags.selectAll().where{ PostHashtags.tag.lowerCase() like "%${query.lowercase()}%" }
            .map { it[PostHashtags.postId].value }

        // Fetch category matches
        val matchingCategoryIds = InterestCategoryTable
            .selectAll().where { InterestCategoryTable.interest.lowerCase() like "%${query.lowercase()}%" }
            .map { it[InterestCategoryTable.id] }

        val categoryMatches = PostCategoryMatches
            .selectAll().where { PostCategoryMatches.categoryId inList matchingCategoryIds }
            .map { it[PostCategoryMatches.postId].value }

        // Score posts
        val scoredPosts = posts.mapNotNull { (postId, row) ->
            val text = row[Posts.textContent]
            val textScore = if (text.contains(query, ignoreCase = true)) 5 else 0
            val hashtagScore = if (postId in hashtags) 3 else 0
            val categoryScore = if (postId in categoryMatches) 2 else 0
            val totalScore = textScore + hashtagScore + categoryScore

            if (totalScore > 0) {
                val response = getFullPostResponse(postId, userId)
                response?.let { it to totalScore }
            } else null
        }

        scoredPosts.sortedByDescending { it.second }.take(limit).map { it.first }
    }
}


suspend fun searchUsers(query: String, userId: Int, limit: Int = 20): List<SuggestedUser> {
    return transaction {
        val results = (UserInitials innerJoin BasicProfile)
            .selectAll()
            .mapNotNull { row ->
                val targetUserId = row[UserInitials.id]
                if (targetUserId == userId) return@mapNotNull null // Exclude self from results

                val username = row[UserInitials.username]
                val fullName = "${row[BasicProfile.firstName]} ${row[BasicProfile.lastName]}"
                val usernameScore = if (username.contains(query, ignoreCase = true)) 5 else 0
                val nameScore = if (fullName.contains(query, ignoreCase = true)) 3 else 0
                val score = usernameScore + nameScore

                if (score == 0) return@mapNotNull null

                // Get profile picture
                val profilePicture = ProfilePictures
                    .selectAll().where{ ProfilePictures.userId eq targetUserId }
                    .singleOrNull()
                val filePath = profilePicture?.get(ProfilePictures.filePath)
                val profileImageUrl = if (filePath != null) {
                    "https://grub-hardy-actively.ngrok-free.app/profile-picture/$filePath"
                } else {
                    "" // Or use a default image URL
                }

                // Check follow status
                val isFollowing = areYouFollowing(userId, targetUserId)
                val isFollower = isFollowingYou(userId, targetUserId)

                SuggestedUser(
                    id = targetUserId,
                    isFollower = isFollower,
                    name = fullName,
                    username = username,
                    imageUrl = profileImageUrl,
                    isFollowing = isFollowing
                )
            }

        results.sortedByDescending {
            // Prioritize by score (username match > name match)
            val usernameMatch = it.username.contains(query, ignoreCase = true)
            val nameMatch = it.name.contains(query, ignoreCase = true)
            when {
                usernameMatch -> 3
                nameMatch -> 2
                else -> 1
            }
        }.take(limit)
    }
}


suspend fun searchCommunities(query: String, limit: Int = 20, userId: Int): List<CommunitySearchResult> {
    return transaction {
        Communities.selectAll().mapNotNull { row ->
            val communityId = row[Communities.id]
            val name = row[Communities.name]
            val description = row[Communities.description]
            val tagsJson = row[Communities.category_tags]

            val categoryIds = try {
                Json.decodeFromString<List<Int>>(tagsJson)
            } catch (e: Exception) {
                emptyList()
            }

            // Interest Matching
            val interestMatches = InterestCategoryTable
                .selectAll().where{ InterestCategoryTable.id inList categoryIds }
                .map {
                    val interest = it[InterestCategoryTable.interest]
                    val related = try {
                        Json.decodeFromString<List<String>>(it[InterestCategoryTable.relatedInterests])
                    } catch (_: Exception) {
                        emptyList()
                    }
                    interest to related
                }

            val bestCategory = interestMatches.firstOrNull {
                it.first.contains(query, ignoreCase = true) || it.second.any { related ->
                    related.contains(query, ignoreCase = true)
                }
            }?.first ?: interestMatches.firstOrNull()?.first ?: "General"

            // Score calculation
            val nameScore = if (name.contains(query, ignoreCase = true)) 5 else 0
            val descScore = if (description.contains(query, ignoreCase = true)) 3 else 0
            val tagScore = if (interestMatches.any {
                    it.first.contains(query, ignoreCase = true) ||
                            it.second.any { rel -> rel.contains(query, ignoreCase = true) }
                }) 2 else 0

            val totalScore = nameScore + descScore + tagScore
            if (totalScore == 0) return@mapNotNull null

            // Member count (status: Active, Admin)
            val memberCount = CommunityMembers
                .selectAll().where{ CommunityMembers.communityId eq communityId and (CommunityMembers.status inList listOf(2, 3)) }
                .count()
                .toInt()

            val coverPhotoPath = row[Communities.coverPhotoPath]?.let {
                "https://grub-hardy-actively.ngrok-free.app/community_cover_photo/$it"
            }

            val isAlreadyMember = CommunityMembers
                .selectAll().where {
                    CommunityMembers.communityId eq communityId and
                            (CommunityMembers.userId eq userId) and
                            (CommunityMembers.status inList listOf(1, 2, 3)) // joined or invited or admin
                }.limit(1).any()

            CommunitySearchResult(
                id = communityId,
                name = name,
                description = description,
                profilePictureUrl = row[Communities.profilePicturePath],
                coverPhotoUrl = coverPhotoPath,
                score = totalScore,
                category = bestCategory,
                userCount = memberCount,
                isAlreadyMember = isAlreadyMember
            )
        }.sortedByDescending { it.score }
            .take(limit)
    }
}


