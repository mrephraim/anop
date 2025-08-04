package com.example.data.classes_daos

import com.example.data.models.BasicProfileResponse
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

object CommunityReports : Table("community_reports") {
    val id = integer("id").autoIncrement()
    val reporterUserId = integer("reporter_user_id") // who is reporting
    val communityId = integer("community_id").references(Communities.id)
    val category = varchar("category", 128) // e.g., Nudity, Hate Speech, etc.
    val additionalDetails = text("additional_details").nullable() // optional
    val reportedAt = datetime("reported_at").defaultExpression(CurrentDateTime)

    override val primaryKey = PrimaryKey(id)
}


@Serializable
data class ExitCommunityRequest(
    val userId: Int,
    val communityId: Int
)

@Serializable
data class ReportCommunityRequest(
    val reporterUserId: Int,
    val communityId: Int,
    val category: String,
    val details: String? = null
)



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
data class SuggestedCommunity(
    val communityId: Int,
    val name: String,
    val profilePicture: String?,
    val coverPhoto: String?,
    val category: String,
    val memberProfilePictures: List<String>, // Up to 5
    val score: Double
)

@Serializable
data class SuggestedCommunity2(val communityId: Int, val name: String, val category: String, val coverImage: String, val score: Double)

@Serializable
data class SuggestCommunityRequest(
    val userId: Int,
    val limit: Int = 10,
    val offset: Int = 0 // NEW
)


@Serializable
data class ScoredCommunity(
    val community: CommunityInfo,
    val score: Double
)

@Serializable
data class CommunityInfoResponse(
    val communityId: Int,
    val name: String,
    val description: String,
    val rules: List<String>,
    val coverPhotoUrl: String?,
    val profilePictureUrl: String?,
    val createdAt: String,
    val totalMembers: Int,
    val creator: BasicProfileResponse,
    val membershipStatus: Int?,  // New field: 1 = Invited, 2 = Member, 3 = Admin, 4 = Left, null = not a member
    val admins: List<BasicProfileResponse>
)

@Serializable
data class MemberWithRelationship(
    val userId: Int,
    val isMutual: Boolean,
    val isFollowingYou: Boolean,
    val areYouFollowing: Boolean
)

@Serializable
data class CommunityMemberWithRelationship(
    val userId: Int,
    val firstName: String,
    val lastName: String,
    val username: String,
    val profilePicturePath: String?,
    val isFollowingYou: Boolean,
    val areYouFollowing: Boolean,
    val isMutual: Boolean
)

