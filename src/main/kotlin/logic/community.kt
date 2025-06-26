package com.example.logic

import com.example.data.classes_daos.*
import com.example.data.db_operations.*
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.server.request.*
import io.ktor.server.routing.*
import io.ktor.server.response.*
import io.ktor.utils.io.*
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.transactions.transaction
import java.io.File
import java.nio.file.Paths

fun Route.communityRoutes(){
    post("/createCommunity") {
        val multipart = call.receiveMultipart()
        var requestData: CreateCommunityRequest? = null
        var profileBytes: ByteArray? = null
        var coverBytes: ByteArray? = null
        var profileFileName = ""
        var coverFileName = ""

        multipart.forEachPart { part ->
            when (part) {
                is PartData.FormItem -> {
                    if (part.name == "data") {
                        requestData = Json.decodeFromString<CreateCommunityRequest>(part.value)
                    }
                }
                is PartData.FileItem -> {
                    if (part.name == "profilePicture") {
                        profileBytes = part.provider().toByteArray()
                        profileFileName = part.originalFileName ?: "profile.png"
                    } else if (part.name == "coverPhoto") {
                        coverBytes = part.provider().toByteArray()
                        coverFileName = part.originalFileName ?: "cover.png"
                    }
                }
                else -> Unit
            }
            part.dispose()
        }

        if (requestData == null) {
            call.respond(HttpStatusCode.BadRequest, mapOf("status" to "error", "message" to "Missing community data"))
            return@post
        }

        val uploadsPath = Paths.get("").toAbsolutePath().toString() + "/uploads"
        val profilePath = profileBytes?.let {
            val folder = File("$uploadsPath/community_profiles")
            if (!folder.exists()) folder.mkdirs()
            val uniqueName = "${requestData!!.creatorUserId}_${(100000..999999).random()}_$profileFileName"
            val file = File(folder, uniqueName)
            file.writeBytes(it)
            uniqueName
        }

        val coverPath = coverBytes?.let {
            val folder = File("$uploadsPath/community_covers")
            if (!folder.exists()) folder.mkdirs()
            val uniqueName = "${requestData!!.creatorUserId}_${(100000..999999).random()}_$coverFileName"
            val file = File(folder, uniqueName)
            file.writeBytes(it)
            uniqueName
        }

        val result = createCommunity(requestData!!, profilePath, coverPath)
        if (result.status == "success") {
            call.respond(HttpStatusCode.OK, result)
        } else {
            call.respond(HttpStatusCode.InternalServerError, mapOf("status" to "error"))
        }
    }

    post("/addCommunityMembers") {
        try {
            val request = call.receive<AddCommunityMembersRequest>()
            val results = mutableListOf<CommunityResult>()

            for (userId in request.userIds) {
                val memberRequest = CommunityMemberRequest(
                    communityId = request.communityId,
                    addedByUserId = request.addedByUserId,
                    userId = userId
                )
                val result = addCommunityMember(memberRequest)
                results.add(result.copy(message = "UserID $userId: ${result.message}"))
            }

            call.respond(HttpStatusCode.OK, AddCommunityMembersResponse(
                status = "completed",
                results = results
            )
            )
        } catch (e: Exception) {
            e.printStackTrace()
            call.respond(HttpStatusCode.BadRequest, AddCommunityMembersResponse(
                status = "error",
                results = listOf(CommunityResult(false, "error", "Failed to add community members: ${e.message}"))
            ))
        }
    }

    post("/joinCommunity") {
        try {
            val request = call.receive<JoinCommunityRequest>()
            val result = joinCommunity(request)
            call.respond(HttpStatusCode.OK, result)
        } catch (e: Exception) {
            e.printStackTrace()
            call.respond(HttpStatusCode.BadRequest, JoinCommunityResult("error", "Failed to join community: ${e.message}"))
        }
    }

    post("/community/invite") {
        val request = call.receive<CommunityInvitesRequest>()
        val result = inviteUsersToCommunity(request)
        call.respond(result)
    }

    post("/getUserCreatedCommunities") {
        val request = call.receive<UserCommunityListRequest>()
        val communities = getCommunitiesCreatedByUser(request.userId)
        call.respond(communities)
    }

    post("/getUserMemberCommunities") {
        val request = call.receive<UserCommunityListRequest>()
        val communities = getCommunitiesUserIsMemberOf(request.userId)
        call.respond(communities)
    }

    post("/getUserAllCommunities") {
        val request = call.receive<UserCommunityListRequest>()
        val communities = getAllUserCommunities(request.userId)
        call.respond(communities)
    }


    post("/suggest-communities") {
        try {
            val request = call.receive<SuggestCommunityRequest>()
            val suggestions = suggestCommunitiesForUser(request.userId, request.limit)

            val results = transaction {
                suggestions.mapNotNull { suggested ->
                    getCommunityInfoById(suggested.communityId)?.let { info ->
                        ScoredCommunity(community = info, score = suggested.score)
                    }
                }
            }

            call.respond(HttpStatusCode.OK, results)
        } catch (e: Exception) {
            e.printStackTrace()
            call.respond(HttpStatusCode.InternalServerError, mapOf("error" to "Failed to fetch suggestions"))
        }
    }

    get("/community_profile_photo/{fileName}") {
        val fileName = call.parameters["fileName"] ?: return@get call.respond(HttpStatusCode.BadRequest)
        val file = File("uploads/community_profiles/${fileName}")

        if (file.exists()) {
            call.respondFile(file)
        } else {
            call.respond(HttpStatusCode.NotFound)
        }
    }

    get("/community_cover_photo/{fileName}") {
        val fileName = call.parameters["fileName"] ?: return@get call.respond(HttpStatusCode.BadRequest)
        val file = File("uploads/community_covers/${fileName}")

        if (file.exists()) {
            call.respondFile(file)
        } else {
            call.respond(HttpStatusCode.NotFound)
        }
    }



}