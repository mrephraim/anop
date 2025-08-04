package com.example.data.db_operations

import com.example.data.classes_daos.*
import com.example.data.models.BasicProfileResponse
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction

fun createCommunity(
    request: CreateCommunityRequest,
    profilePicPath: String?,
    coverPhotoPicPath: String?
): CreateCommunityResponse = transaction {
    val communityId = Communities.insert {
        it[name] = request.name
        it[description] = request.description
        it[rules] = Json.encodeToString(request.rules)
        it[category_tags] = Json.encodeToString(request.categoryTags)
        it[membershipType] = request.membershipType
        it[creatorUserId] = request.creatorUserId
        it[profilePicturePath] = profilePicPath
        it[coverPhotoPath] = coverPhotoPicPath
    } get Communities.id // explicitly get the id column

    CreateCommunityResponse(
        communityId = communityId,
        profilePicturePath = "https://grub-hardy-actively.ngrok-free.app/community_profile_photo/${profilePicPath}",
        communityName = request.name,
        status = "success"
    )
}


fun addCommunityMember(request: CommunityMemberRequest): CommunityResult {
    val isMutual = isMutualRelationship(request.addedByUserId, request.userId)


    val communityType = transaction {
        Communities
            .selectAll()
            .where { Communities.id eq request.communityId }
            .map { it[Communities.membershipType] }
            .singleOrNull()
    } ?: return CommunityResult(false, "error", "Community not found")

    return if (isMutual) {
        val status = if (communityType == 1) 2 else 1 // Direct add for public, invite for private/paid

        transaction {
            CommunityMembers.insert {
                it[communityId] = request.communityId
                it[userId] = request.userId
                it[CommunityMembers.status] = status
            }
        }

        CommunityResult(true, "success", if (status == 2) "User added to community" else "User invited to community")
    } else {
        // Not mutual - don't insert, just suggest sending invite
        CommunityResult(false, "info", "User is not a mutual. Send invite email or message instead.")
    }
}


fun joinCommunity(request: JoinCommunityRequest): JoinCommunityResult {
    // Step 1: Check if user is already an active member (status 2 or 3)
    if (isUserInCommunity(request.userId, request.communityId)) {
        return JoinCommunityResult("info", "You are already a member of this community")
    }

    // Step 2: Check if the user has been invited (status 1)
    val isInvited = transaction {
        CommunityMembers.selectAll()
            .where {
                (CommunityMembers.communityId eq request.communityId) and
                        (CommunityMembers.userId eq request.userId) and
                        (CommunityMembers.status eq 1)
            }
            .any()
    }

    if (isInvited) {
        // Step 3: Get community type
        val communityType = transaction {
            Communities
                .selectAll()
                .where { Communities.id eq request.communityId }
                .map { it[Communities.membershipType] }
                .singleOrNull()
        } ?: return JoinCommunityResult("error", "Community not found")

        if (communityType == 2) { // Private community
            transaction {
                CommunityMembers.update({
                    (CommunityMembers.communityId eq request.communityId) and
                            (CommunityMembers.userId eq request.userId)
                }) {
                    it[status] = 2 // Activate the invitation
                }
            }
            return JoinCommunityResult("success", "Successfully joined the private community via invitation")
        }
        // If not private, proceed to handle normally below
    }

    // Step 4: Get community type if not already retrieved
    val communityType = transaction {
        Communities
            .selectAll()
            .where { Communities.id eq request.communityId }
            .map { it[Communities.membershipType] }
            .singleOrNull()
    } ?: return JoinCommunityResult("error", "Community not found")

    return when (communityType) {
        1 -> { // Public
            transaction {
                CommunityMembers.insertIgnore {
                    it[communityId] = request.communityId
                    it[userId] = request.userId
                    it[status] = 2 // Active
                }
            }
            JoinCommunityResult("success", "Successfully joined the public community")
        }

        3 -> { // Paid
            JoinCommunityResult("pending", "This is a paid community. Payment required to join.", "Show payment options")
        }

        2 -> { // Private (but no invitation)
            JoinCommunityResult("error", "You have not been invited to this private community")
        }

        else -> JoinCommunityResult("error", "Unknown community type")
    }
}


fun isUserInCommunity(userId: Int, communityId: Int): Boolean {
    return transaction {
        CommunityMembers
            .selectAll()
            .where {
                (CommunityMembers.userId eq userId) and
                        (CommunityMembers.communityId eq communityId) and
                        (CommunityMembers.status eq 2 or (CommunityMembers.status eq 3))
            }
            .any()
    }
}



fun inviteUsersToCommunity(request: CommunityInvitesRequest): CommunityResult {
    return transaction {
        // Step 1: Validate session
        if (!isLoginSessionValid(request.invitingUserId, request.deviceId, request.refreshToken)) {
            return@transaction CommunityResult(false, "error", "Invalid session. Please log in again.")
        }

        // Step 2: Check if acting user is the creator or an admin of the community
        val isCreator = Communities
            .selectAll()
            .where { (Communities.id eq request.communityId) and (Communities.creatorUserId eq request.invitingUserId) }
            .any()

        val isAdmin = CommunityMembers
            .selectAll()
            .where {
                (CommunityMembers.communityId eq request.communityId) and
                        (CommunityMembers.userId eq request.invitingUserId) and
                        (CommunityMembers.status eq 3)
            }
            .any()

        if (!isCreator && !isAdmin) {
            return@transaction CommunityResult(false, "error", "You do not have permission to invite users to this community")
        }

        // Step 3: Process each invited user
        val alreadyMembers = mutableListOf<Int>()
        val newlyInvited = mutableListOf<Int>()

        request.invitedUserIds.forEach { invitedUserId ->
            if (isUserInCommunity(invitedUserId, request.communityId)) {
                alreadyMembers.add(invitedUserId)
            } else {
                CommunityMembers.insert {
                    it[communityId] = request.communityId
                    it[userId] = invitedUserId
                    it[status] = 1 // 1 = Invited
                }
                newlyInvited.add(invitedUserId)
            }
        }

        return@transaction when {
            newlyInvited.isEmpty() -> CommunityResult(true, "success", "All selected users are already members or have been invited.")
            alreadyMembers.isEmpty() -> CommunityResult(true, "success", "All selected users have been invited successfully.")
            else -> CommunityResult(
                true,
                "success",
                "Some users were already members or invited. Others have been invited successfully.",
            )
        }
    }
}


fun getCommunitiesCreatedByUser(userId: Int): List<CommunityInfo3> = transaction {
    Communities
        .selectAll()
        .where { Communities.creatorUserId eq userId }
        .map {
            CommunityInfo3(
                id = it[Communities.id],
                name = it[Communities.name],
                description = it[Communities.description],
                profilePicturePath = "https://grub-hardy-actively.ngrok-free.app/community_profile_photo/${it[Communities.profilePicturePath]}"
            )
        }
}


fun getCommunitiesUserIsMemberOf(userId: Int): List<CommunityInfo> {
    return transaction {
        CommunityMembers
            .selectAll()
            .where { (CommunityMembers.userId eq userId) and (CommunityMembers.status neq 4) } // Exclude left communities
            .mapNotNull { row ->
                Communities
                    .selectAll()
                    .where { Communities.id eq row[CommunityMembers.communityId] }
                    .singleOrNull()
                    ?.let {
                        CommunityInfo(
                            id = it[Communities.id],
                            name = it[Communities.name],
                            description = it[Communities.description],
                            membershipType = it[Communities.membershipType],
                            categoryTags = Json.decodeFromString<List<Int>>(row[Communities.category_tags]),
                            profilePicturePath = it[Communities.profilePicturePath],
                            coverPhotoPath = it[Communities.coverPhotoPath],
                            createdAt = it[Communities.createdAt].toString()
                        )
                    }
            }
    }
}

fun getInterestName(interestId: Int): String {
    return InterestCategoryTable
        .selectAll().where{ InterestCategoryTable.id eq interestId }
        .singleOrNull()
        ?.get(InterestCategoryTable.interest)
        ?: ""
}

fun getAllUserCommunities(userId: Int): List<CommunityInfo2> = transaction {
    val userInterestIds = getUserInterests(userId).interestIds.toSet()

    // Step 1: Communities created by user
    val createdCommunities = Communities
        .selectAll()
        .where { Communities.creatorUserId eq userId }
        .map {
            val categoryIds = Json.decodeFromString<List<Int>>(it[Communities.category_tags])
            val matchingInterestId = categoryIds.firstOrNull { id -> id in userInterestIds }
            val categoryTag = matchingInterestId?.let { getInterestName(it) }
                ?: categoryIds.firstOrNull()?.let { getInterestName(it) }
                ?: ""

            CommunityInfo2(
                id = it[Communities.id],
                name = it[Communities.name],
                description = it[Communities.description],
                membershipType = it[Communities.membershipType],
                membershipStatus = 0,
                categoryTag = categoryTag,
                profilePicturePath = "https://grub-hardy-actively.ngrok-free.app/community_profile_photo/${it[Communities.profilePicturePath]}",
                coverPhotoPath = "https://grub-hardy-actively.ngrok-free.app/community_cover_photo/${it[Communities.coverPhotoPath]}",
                createdAt = it[Communities.createdAt].toString(),
                creatorUserId = it[Communities.creatorUserId]
            )
        }
        .associateBy { it.id }

    // Step 2: Communities where user is a member (excluding left)
    val memberCommunities = CommunityMembers
        .selectAll()
        .where { (CommunityMembers.userId eq userId) and (CommunityMembers.status neq 4) }
        .mapNotNull { row ->
            val communityId = row[CommunityMembers.communityId]
            val membershipStatus = row[CommunityMembers.status]

            Communities
                .selectAll()
                .where { Communities.id eq communityId }
                .singleOrNull()
                ?.let {
                    val categoryIds = Json.decodeFromString<List<Int>>(it[Communities.category_tags])
                    val matchingInterestId = categoryIds.firstOrNull { id -> id in userInterestIds }
                    val categoryTag = matchingInterestId?.let { getInterestName(it) }
                        ?: categoryIds.firstOrNull()?.let { getInterestName(it) }
                        ?: ""

                    CommunityInfo2(
                        id = it[Communities.id],
                        name = it[Communities.name],
                        description = it[Communities.description],
                        membershipType = it[Communities.membershipType],
                        membershipStatus = membershipStatus, // 👈 Pass user's actual membership status here
                        categoryTag = categoryTag,
                        profilePicturePath = "https://grub-hardy-actively.ngrok-free.app/community_profile_photo/${it[Communities.profilePicturePath]}",
                        coverPhotoPath = "https://grub-hardy-actively.ngrok-free.app/community_cover_photo/${it[Communities.coverPhotoPath]}",
                        createdAt = it[Communities.createdAt].toString(),
                        creatorUserId = it[Communities.creatorUserId]
                    )
                }
        }
        .associateBy { it.id }

    // Step 3: Merge both maps to remove duplicates (createdCommunities take priority)
    (createdCommunities + memberCommunities).values.toList()
}



fun suggestCommunitiesForUser(userId: Int, limit: Int = 10, offset: Int = 0): List<SuggestedCommunity> {
    val userInterests = getUserInterests(userId).interestIds.toSet()
    val recommendedUsers = recommendUsersByInterest(userId).map { it.userId }.toSet()
    val mutualUserIds = getMutualFollowersBasicInfo(userId).map { it.userId }.toSet()

    val communityScores = mutableMapOf<Int, Double>()

    transaction {
        val allCommunities = Communities.selectAll().toList()

        allCommunities.forEach { community ->
            val communityId = community[Communities.id]
            val creatorId = community[Communities.creatorUserId]
            val communityTags = Json.decodeFromString<List<Int>>(community[Communities.category_tags])

            // Skip if already a member
            val isAlreadyMember = CommunityMembers.selectAll()
                .where {
                    (CommunityMembers.communityId eq communityId) and
                            (CommunityMembers.userId eq userId) and
                            (CommunityMembers.status inList listOf(2, 3))
                }.any()
            if (isAlreadyMember) return@forEach
            if(userId == creatorId){
                return@forEach
            }

            var score = 0.0

            // Interest match
            val tagMatches = communityTags.count { tag -> userInterests.contains(tag.hashCode()) }
            score += tagMatches * 2.0

            // Community members
            val communityMembers = CommunityMembers.selectAll()
                .where { (CommunityMembers.communityId eq communityId) and (CommunityMembers.status inList listOf(2, 3)) }
                .map { it[CommunityMembers.userId] }

            // Mutuals & Following presence in community
            val mutuals = communityMembers.count { isMutualRelationship(userId, it) || areYouFollowing(userId, it) }
            score += mutuals * 1.5

            // Popularity
            score += kotlin.math.ln(communityMembers.size + 1.0)

            // Creator connection
            if (isMutualRelationship(userId, creatorId) || areYouFollowing(userId, creatorId)) {
                score += 2.5
            }

            // Recommended user overlap
            val overlapCount = communityMembers.count { recommendedUsers.contains(it) }
            score += overlapCount * 1.2

            communityScores[communityId] = score
        }
    }

    // ⬇️ APPLY PAGINATION ONLY HERE
    val paginatedCommunityIds = communityScores.entries
        .sortedByDescending { it.value }
        .drop(offset)
        .take(limit)

    return transaction {
        paginatedCommunityIds.mapNotNull { (communityId, score) ->
            Communities.selectAll().where { Communities.id eq communityId }.singleOrNull()?.let { row ->
                val communityTags = Json.decodeFromString<List<Int>>(row[Communities.category_tags])
                val categoryName = communityTags.firstOrNull()?.let { tagId ->
                    InterestCategoryTable.selectAll().where { InterestCategoryTable.id eq tagId }.singleOrNull()?.get(InterestCategoryTable.interest)
                } ?: "General"

                val communityMembers = CommunityMembers.selectAll()
                    .where { (CommunityMembers.communityId eq communityId) and (CommunityMembers.status inList listOf(2, 3)) }
                    .map { it[CommunityMembers.userId] }

                val filteredMemberIds = communityMembers.filter {
                    mutualUserIds.contains(it) ||
                            mutualUserIds.any { mutual -> areYouFollowing(mutual, it) }
                }

                val profilePictures = filteredMemberIds.distinct().take(5).mapNotNull { memberId ->
                    getUserProfileDetails(memberId)?.profilePicturePath
                }

                SuggestedCommunity(
                    communityId = row[Communities.id],
                    name = row[Communities.name],
                    profilePicture = "https://grub-hardy-actively.ngrok-free.app/profile-picture/${row[Communities.profilePicturePath]}",
                    coverPhoto = "https://grub-hardy-actively.ngrok-free.app/community_cover_photo/${row[Communities.coverPhotoPath]}",
                    category = categoryName,
                    memberProfilePictures = profilePictures,
                    score = score
                )
            }
        }
    }
}



fun getCommunityInfoById(communityId: Int): CommunityInfo? = transaction {
    Communities
        .selectAll()
        .where { Communities.id eq communityId }
        .singleOrNull()
        ?.let { row ->
            CommunityInfo(
                id = row[Communities.id],
                name = row[Communities.name],
                description = row[Communities.description],
                membershipType = row[Communities.membershipType],
                categoryTags = Json.decodeFromString<List<Int>>(row[Communities.category_tags]),
                profilePicturePath = "https://grub-hardy-actively.ngrok-free.app/community_profile_photo/${row[Communities.profilePicturePath]}",
                coverPhotoPath = "https://grub-hardy-actively.ngrok-free.app/community_cover_photo/${row[Communities.coverPhotoPath]}",
                createdAt = row[Communities.createdAt].toString()
            )
        }
}


fun getCommunityInfo(communityId: Int, userId: Int): CommunityInfoResponse? = transaction {
    val community = Communities
        .selectAll().where { Communities.id eq communityId }
        .singleOrNull() ?: return@transaction null

    val creatorId = community[Communities.creatorUserId]
    val creatorProfile = getUserBasicProfile(creatorId)

    val allMembers = CommunityMembers
        .selectAll().where { CommunityMembers.communityId eq communityId }

    val totalMembers = allMembers.count()

    val adminIds = allMembers
        .filter { it[CommunityMembers.status] == 3 } // Status 3 = Admin
        .map { it[CommunityMembers.userId] }

    val membershipRow = CommunityMembers
        .selectAll()
        .where {
            (CommunityMembers.communityId eq communityId) and
                    (CommunityMembers.userId eq userId)
        }
        .singleOrNull()

    val membershipStatus = membershipRow?.get(CommunityMembers.status)

    val admins = adminIds.mapNotNull { getUserBasicProfile(it) }

    CommunityInfoResponse(
        communityId = communityId,
        name = community[Communities.name],
        description = community[Communities.description],
        rules = try {
            Json.decodeFromString(
                ListSerializer(String.serializer()),
                community[Communities.rules]
            )
        } catch (e: Exception) {
            emptyList()
        },
        coverPhotoUrl = community[Communities.coverPhotoPath]?.let {
            "https://grub-hardy-actively.ngrok-free.app/community_cover_photo/$it"
        },
        profilePictureUrl = community[Communities.profilePicturePath]?.let {
            "https://grub-hardy-actively.ngrok-free.app/community_profile_photo/$it"
        },
        createdAt = community[Communities.createdAt].toString(),
        totalMembers = totalMembers.toInt(),
        creator = creatorProfile ?: BasicProfileResponse(creatorId, "Unknown", null),
        admins = admins,
        membershipStatus = membershipStatus
    )
}

fun exitCommunity(userId: Int, communityId: Int): BasicResponse {
    return try {
        transaction {
            CommunityMembers.deleteWhere {
                (CommunityMembers.userId eq userId) and
                        (CommunityMembers.communityId eq communityId)
            }
        }
        BasicResponse(status = "success", message = "Successfully exited community.")
    } catch (e: Exception) {
        BasicResponse(status = "error", message = e.message ?: "Unknown error")
    }
}



fun reportCommunity(
    reporterUserId: Int,
    communityId: Int,
    category: String,
    details: String?
): BasicResponse {
    return try {
        transaction {
            CommunityReports.insert {
                it[CommunityReports.reporterUserId] = reporterUserId
                it[CommunityReports.communityId] = communityId
                it[CommunityReports.category] = category
                it[additionalDetails] = details
            }
        }
        BasicResponse(status = "success", message = "Report submitted successfully.")
    } catch (e: Exception) {
        BasicResponse(status = "error", message = e.message ?: "Failed to report community.")
    }
}



fun getCommunityMembersWithRelationship(
    communityId: Int,
    userId: Int
): List<CommunityMemberWithRelationship> = transaction {
    val memberRows = CommunityMembers
        .selectAll()
        .where {
            (CommunityMembers.communityId eq communityId) and
                    (CommunityMembers.status inList listOf(2, 3)) // Active or Admin
        }.toList()

    memberRows.mapNotNull { memberRow ->
        val memberUserId = memberRow[CommunityMembers.userId]

        if (memberUserId == userId) return@mapNotNull null // Exclude self

        val basicProfile = getUserProfileDetails2(memberUserId) ?: return@mapNotNull null

        CommunityMemberWithRelationship(
            userId = memberUserId,
            firstName = basicProfile.firstName,
            lastName = basicProfile.lastName,
            username = basicProfile.username,
            profilePicturePath = basicProfile.profilePicturePath,
            isFollowingYou = isFollowingYou(userId, memberUserId),
            areYouFollowing = areYouFollowing(userId, memberUserId),
            isMutual = isMutualRelationship(userId, memberUserId)
        )
    }
}




