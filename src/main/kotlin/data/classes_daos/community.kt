package com.example.data.classes_daos

import kotlinx.serialization.Serializable
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.jodatime.CurrentDateTime
import org.jetbrains.exposed.sql.jodatime.datetime

object Communities : Table("communities") {
    val id = integer("id").autoIncrement()
    val name = varchar("name", 255)
    val description = text("description")
    val rules = text("rules") // JSON-encoded list of strings
    val category_tags = text("category_tags") // JSON-encoded list of strings
    val membershipType = integer("membership_type") // 1: Public, 2: Private, 3: Paid
    val profilePicturePath = varchar("profile_picture_path", 512).nullable()
    val coverPhotoPath = varchar("cover_photo_path", 512).nullable()
    val creatorUserId = integer("creator_user_id")
    val createdAt = datetime("created_at").defaultExpression(CurrentDateTime)
    override val primaryKey = PrimaryKey(id)
}

object CommunityMembers : Table("community_members") {
    val id = integer("id").autoIncrement()
    val communityId = integer("community_id").references(Communities.id)
    val userId = integer("user_id")
    val status = integer("status") // 1: Invited, 2: Active Member, 3: Admin, 4: Left
    val addedAt = datetime("added_at").defaultExpression(CurrentDateTime)
    override val primaryKey = PrimaryKey(id)
}


@Serializable
data class CreateCommunityRequest(
    val name: String,
    val description: String,
    val rules: List<String>,
    val categoryTags: List<Int>,
    val membershipType: Int, // 1: Public, 2: Private, 3: Paid
    val creatorUserId: Int
)

@Serializable
data class CommunityMemberRequest(
    val communityId: Int,
    val userId: Int,
    val addedByUserId: Int
)

@Serializable
data class CommunityResult(
    val success: Boolean,
    val status: String,
    val message: String
)
@Serializable
data class CreateCommunityResponse(
    val status: String,
    val communityId: Int? = null,
    val profilePicturePath: String,
    val communityName: String? = null,
)


@Serializable
data class AddCommunityMembersResponse(
    val status: String,
    val results: List<CommunityResult>
)


@Serializable
data class AddCommunityMembersRequest(
    val communityId: Int,
    val addedByUserId: Int,
    val userIds: List<Int>
)

@Serializable
data class JoinCommunityRequest(
    val userId: Int,
    val communityId: Int
)

@Serializable
data class JoinCommunityResult(
    val status: String,
    val message: String,
    val nextAction: String? = null // For paid community next steps
)

@Serializable
data class CommunityInvitesRequest(
    val communityId: Int,
    val invitedUserIds: List<Int>,
    val invitingUserId: Int,
    val deviceId: String,
    val refreshToken: String
)


@Serializable
data class CommunityInfo(
    val id: Int,
    val name: String,
    val description: String,
    val membershipType: Int,
    val categoryTags: List<Int>,
    val profilePicturePath: String?,
    val coverPhotoPath: String?,
    val createdAt: String
)

@Serializable
data class CommunityInfo2(
    val id: Int,
    val name: String,
    val description: String,
    val membershipType: Int,
    val membershipStatus: Int,
    val creatorUserId: Int,
    val categoryTag: String,
    val profilePicturePath: String?,
    val coverPhotoPath: String?,
    val createdAt: String
)
@Serializable
data class CommunityInfo3(
    val id: Int,
    val name: String,
    val description: String,
    val profilePicturePath: String?,
)

@Serializable
data class UserCommunityListRequest(val userId: Int)

@Serializable
data class SuggestedCommunity(val communityId: Int, val name: String, val score: Double)

@Serializable
data class SuggestCommunityRequest(
    val userId: Int,
    val limit: Int = 10
)

@Serializable
data class ScoredCommunity(
    val community: CommunityInfo,
    val score: Double
)
