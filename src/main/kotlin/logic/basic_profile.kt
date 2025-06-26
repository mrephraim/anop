package com.example.logic

import io.ktor.server.routing.*
import com.example.data.db_operations.*
import com.example.data.models.*
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.utils.io.*
import java.io.File
import java.nio.file.Paths

fun Route.basicProfile(){
    post("/set_basic_profile") {
        try {
            val request = call.receive<UserProfileRequest>()

            // Sanitize and trim fields
            val sanitizedProfile = request.copy(
                firstName = request.firstName.trim(),
                lastName = request.lastName.trim(),
                shortBio = request.shortBio?.trim(),
                about = request.about?.trim(),
                username = request.username.trim()
            )

            // Update username in user_initials table
            val usernameUpdated = updateUsername(sanitizedProfile.userId, sanitizedProfile.username)
            if (!usernameUpdated) {
                call.respond(
                    HttpStatusCode.BadRequest,
                    SetBasicProfileResponseResult("Failed to update username", "error")
                )
                return@post
            }

            // Insert user profile (excluding username)
            val userProfile = UserProfile(
                userId = sanitizedProfile.userId,
                firstName = sanitizedProfile.firstName,
                lastName = sanitizedProfile.lastName,
                shortBio = sanitizedProfile.shortBio,
                about = sanitizedProfile.about,
                gender = sanitizedProfile.gender
            )
            val profileId = insertUserProfile(userProfile.userId, userProfile)

            if (profileId != null) {
                call.respond(
                    HttpStatusCode.Created,
                    SetBasicProfileResponseResult("Profile created successfully", "success")
                )
            } else {
                call.respond(
                    HttpStatusCode.Conflict,
                    SetBasicProfileResponseResult("Profile already exists", "error")
                )
            }
        } catch (e: Exception) {
            call.respond(
                HttpStatusCode.InternalServerError,
                SetBasicProfileResponseResult("Something went wrong, try again or contact support", "error")
            )
        }
    }

    post("/check_username") {
        try {
            val request = call.receive<CheckUsernameRequest>()
            val username = request.username.trim()

            if (username.isEmpty()) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Username must not be empty."))
                return@post
            }

            val exists = isUsernameExists(username)
            val response = UsernameCheckResponse(available = !exists)

            call.respond(HttpStatusCode.OK, response)
        } catch (e: Exception) {
            call.respond(
                HttpStatusCode.InternalServerError,
                mapOf("error" to "Something went wrong: ${e.message}")
            )
        }
    }


    post("/upload_profile_picture") {
        try {
            println("📥 Receiving multipart data...")
            val multipart = call.receiveMultipart()

            var userId: String? = null
            var fileBytes: ByteArray? = null
            var fileName = ""

            multipart.forEachPart { part ->
                when (part) {
                    is PartData.FormItem -> {
                        println("🧾 Form field received: ${part.name} = ${part.value}")
                        if (part.name == "userId") userId = part.value
                    }
                    is PartData.FileItem -> {
                        fileName = part.originalFileName ?: "profile_picture.png"
                        println("📁 File field received: name=${part.name}, originalFileName=$fileName")
                        fileBytes = part.provider().toByteArray()
                        println("📏 File size: ${fileBytes?.size} bytes")
                    }
                    else -> println("📦 Other part received: ${part.name}")
                }
                part.dispose()
            }

            if (userId == null || fileBytes == null) {
                println("🚫 Missing userId or fileBytes")
                call.respond(HttpStatusCode.BadRequest, mapOf("status" to "error", "message" to "Missing fields"))
                return@post
            }

            val uniqueCode = (100000..999999).random()
            val uniqueFileName = "${userId}_${uniqueCode}_$fileName"

            val rootPath = Paths.get("").toAbsolutePath().toString()
            val folder = File("$rootPath/uploads/profile_pictures")
            if (!folder.exists()) {
                println("📂 Uploads folder does not exist, creating one...")
                folder.mkdirs()
            }

            val file = File(folder, uniqueFileName)
            file.writeBytes(fileBytes!!)
            println("✅ File saved: ${file.absolutePath}")

            val success = saveProfilePictureToDb(userId!!.toInt(), uniqueFileName)

            if (success) {
                call.respond(HttpStatusCode.OK, mapOf("status" to "success", "message" to "Profile picture uploaded"))
            } else {
                call.respond(HttpStatusCode.InternalServerError, mapOf("status" to "error", "message" to "Failed to save to database"))
            }

        } catch (e: Exception) {
            e.printStackTrace()
            call.respond(HttpStatusCode.InternalServerError, mapOf("status" to "error", "message" to "Upload failed"))
        }
    }


    delete("/delete_profile_picture/{userId}") {
        try {
            val userId = call.parameters["userId"]?.toIntOrNull()
            if (userId == null) {
                call.respond(HttpStatusCode.BadRequest, mapOf("status" to "error", "message" to "Invalid user ID"))
                return@delete
            }

            val success = deleteProfilePicture(userId)
            if (success) {
                call.respond(HttpStatusCode.OK, mapOf("status" to "success", "message" to "Profile image deleted"))
            } else {
                call.respond(HttpStatusCode.NotFound, mapOf("status" to "error", "message" to "Image not found or deletion failed"))
            }
        } catch (e: Exception) {
            e.printStackTrace()
            call.respond(HttpStatusCode.InternalServerError, mapOf("status" to "error", "message" to "Deletion failed"))
        }
    }


    post("/get_profile_details") {
        try {
            val request = call.receive<ProfileRequest>()

            if (!isLoginSessionValid(request.userId, request.deviceId, request.refreshToken)) {
                call.respond(HttpStatusCode.Unauthorized, ProfileResponse(status = "error", message = "Invalid session"))
                return@post
            }

            val profileDetails = getUserProfileDetails(request.userId)
            if (profileDetails == null) {
                call.respond(HttpStatusCode.NotFound, ProfileResponse(status = "error", message = "Profile not found"))
                return@post
            }

            call.respond(HttpStatusCode.OK, ProfileResponse(status = "success", data = profileDetails))
        } catch (e: Exception) {
            e.printStackTrace()
            call.respond(HttpStatusCode.InternalServerError, ProfileResponse(status = "error", message = "Could not fetch profile"))
        }
    }

    get("/get_profile_details/{userId}") {
        try {
            val userId = call.parameters["userId"]?.toIntOrNull()

            if (userId == null) {
                call.respond(HttpStatusCode.BadRequest, mapOf("status" to "error", "message" to "Invalid user ID"))
                return@get
            }

            val profileDetails = getUserProfileDetails(userId)
            if (profileDetails == null) {
                call.respond(HttpStatusCode.NotFound, ProfileResponse(status = "error", message = "Profile not found"))
                return@get
            }

            call.respond(HttpStatusCode.OK, ProfileResponse(status = "success", data = profileDetails))
        } catch (e: Exception) {
            e.printStackTrace()
            call.respond(HttpStatusCode.InternalServerError, ProfileResponse(status = "error", message = "Could not fetch profile"))
        }
    }


    get("/profile-picture/{fileName}") {
        val fileName = call.parameters["fileName"] ?: return@get call.respond(HttpStatusCode.BadRequest)
        val file = File("uploads/profile_pictures/${fileName}")

        if (file.exists()) {
            call.respondFile(file)
        } else {
            call.respond(HttpStatusCode.NotFound)
        }
    }

    get("/user/basic-profile/{userId}") {
        val userId = call.parameters["userId"]?.toIntOrNull()
        if (userId == null) {
            call.respond(HttpStatusCode.BadRequest, "Invalid userId")
            return@get
        }

        val profile = getUserBasicProfile(userId)
        if (profile == null) {
            call.respond(HttpStatusCode.NotFound, "User profile not found")
        } else {
            call.respond(profile)
        }
    }









}