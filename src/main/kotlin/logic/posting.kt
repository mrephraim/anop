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
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Paths


fun Route.postRoutes(){

    post("/uploadChunk") {
        val multipart = call.receiveMultipart()
        var fileId: String? = null
        var fileName: String? = null
        var chunkIndex: Int? = null
        var totalChunks: Int? = null
        var type: String? = null
        var retries: Int? = null
        var chunkData: ByteArray? = null

        multipart.forEachPart { part ->
            println("🔍 Received part: name=${part.name}, type=${part::class.simpleName}")

            when (part) {
                is PartData.FormItem -> {
                    println("📄 Form field: ${part.name} = ${part.value}")
                    when (part.name) {
                        "fileId" -> fileId = part.value
                        "fileName" -> fileName = part.value
                        "chunkIndex" -> chunkIndex = part.value.toIntOrNull()
                        "totalChunks" -> totalChunks = part.value.toIntOrNull()
                        "retries" -> retries = part.value.toIntOrNull()
                        "type" -> type = part.value
                    }
                }

                is PartData.FileItem -> {
                    println("📁 File field: name=${part.name}, originalFileName=${part.originalFileName}")
                    chunkData = part.provider().toByteArray()
                }

                else -> {
                    println("⚠️ Unknown part type: ${part::class.simpleName}")
                }
            }

            part.dispose()
        }


        if (fileId == null || fileName == null || chunkIndex == null || totalChunks == null || chunkData == null || type == null) {
            call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Missing required fields"))
            return@post
        }



        val folder = File("uploads/chunks/$fileId")
        if (!folder.exists()) folder.mkdirs()


        if (retries != null && retries!! >= 15) {
            folder.deleteRecursively()
            call.respond(HttpStatusCode.Gone, mapOf("error" to "Too many retries"))
            return@post
        }


        val chunkFile = File(folder, "chunk_$chunkIndex")
        chunkFile.writeBytes(chunkData!!)

        call.respond(HttpStatusCode.OK, mapOf("status" to "chunk received"))
    }

    post("/finalizeUpload") {
        val params = call.receiveParameters()
        val fileId = params["fileId"]
        val fileName = params["fileName"]
        val totalChunks = params["totalChunks"]?.toIntOrNull()
        val type = params["type"] // "image" or "video"

        if (fileId == null || fileName == null || totalChunks == null || type == null) {
            call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Missing parameters"))
            return@post
        }

        val chunkDir = File("uploads/chunks/$fileId")
        val outputDir = File("uploads/${type}s")
        if (!outputDir.exists()) outputDir.mkdirs()

        val outputFile = File(outputDir, "${fileId}_${fileName}")
        FileOutputStream(outputFile).use { outputStream ->
            for (i in 0 until totalChunks) {
                val chunkFile = File(chunkDir, "chunk_$i")
                if (!chunkFile.exists()) {
                    call.respond(HttpStatusCode.Conflict, mapOf("error" to "Missing chunk $i"))
                    return@post
                }
                outputStream.write(chunkFile.readBytes())
            }
        }

        // Cleanup chunks
        chunkDir.deleteRecursively()

        call.respond(HttpStatusCode.OK, mapOf("status" to "upload complete", "path" to outputFile.name))
    }

    post("/createPost") {
        val multipart = call.receiveMultipart()
        var requestData: CreatePostRequest? = null
        val imageFileNames = mutableListOf<String>()
        val videoFileNames = mutableListOf<String>()

        println("🔄 Receiving multipart data...")

        multipart.forEachPart { part ->
            when (part) {
                is PartData.FormItem -> {
                    println("📄 FormItem - name=${part.name}, value=${part.value}")
                    when (part.name) {
                        "data" -> {
                            try {
                                requestData = Json.decodeFromString<CreatePostRequest>(part.value)
                                println("✅ Parsed CreatePostRequest: $requestData")
                            } catch (e: Exception) {
                                println("❌ Failed to parse CreatePostRequest: ${e.message}")
                            }
                        }
                        "imageFile" -> {
                            println("🖼️ Received image file name: ${part.value}")
                            imageFileNames.add(part.value)
                        }
                        "videoFile" -> {
                            println("🎥 Received video file name: ${part.value}")
                            videoFileNames.add(part.value)
                        }
                    }
                }
                else -> println("⚠️ Skipped non-form item part: ${part::class.simpleName}")
            }
            part.dispose()
        }

        if (requestData == null) {
            println("❌ Missing post data")
            call.respond(HttpStatusCode.BadRequest, CreatePostResponse("error", "Missing post data"))
            return@post
        }

        val uploadsPath = Paths.get("").toAbsolutePath().toString() + "/uploads"
        println("📂 Uploads base path: $uploadsPath")

        val imagePaths = mutableListOf<String>()
        val videoPaths = mutableListOf<String>()

        for ((index, name) in imageFileNames.withIndex()) {
            val srcFile = File("$uploadsPath/images/$name")
            println("🔍 Looking for image: ${srcFile.absolutePath}")
            if (!srcFile.exists()) {
                println("❌ Image file not found: $name")
                call.respond(HttpStatusCode.BadRequest, CreatePostResponse("error", "Missing image file: $name"))
                return@post
            }

            val dstFolder = File("$uploadsPath/posts/images")
            if (!dstFolder.exists()) dstFolder.mkdirs()

            val uniqueName = "${requestData!!.shareAs}_${System.currentTimeMillis()}_${index}_$name"
            val dstFile = File(dstFolder, uniqueName)
            srcFile.copyTo(dstFile, overwrite = true)
            imagePaths.add(uniqueName)

            println("✅ Moved image to: ${dstFile.absolutePath}")

            srcFile.delete()
        }

        for ((index, name) in videoFileNames.withIndex()) {
            val srcFile = File("$uploadsPath/videos/$name")
            println("🔍 Looking for video: ${srcFile.absolutePath}")
            if (!srcFile.exists()) {
                println("❌ Video file not found: $name")
                call.respond(HttpStatusCode.BadRequest, CreatePostResponse("error", "Missing video file: $name"))
                return@post
            }

            val dstFolder = File("$uploadsPath/posts/videos")
            if (!dstFolder.exists()) dstFolder.mkdirs()

            val uniqueName = "${requestData!!.shareAs}_${System.currentTimeMillis()}_${index}_$name"
            val dstFile = File(dstFolder, uniqueName)
            srcFile.copyTo(dstFile, overwrite = true)
            videoPaths.add(uniqueName)

            println("✅ Moved video to: ${dstFile.absolutePath}")

            srcFile.delete()
        }

        println("📥 Saving post with: shareAs=${requestData!!.shareAs}, type=${requestData!!.type}, shareTo=${requestData!!.shareTo}, visibility=${requestData!!.visibility}")
        println("🖼️ Images: $imagePaths")
        println("🎥 Videos: $videoPaths")

        val postId = savePost(
            shareAsId = requestData!!.shareAs,
            type = requestData!!.type,
            textContent = requestData!!.textContent,
            shareTo = requestData!!.shareTo,
            visibility = requestData!!.visibility,
            imagePaths = imagePaths,
            videoPaths = videoPaths
        )

        if (postId != null) {
            println("✅ Post saved successfully with ID: $postId")
            call.respond(HttpStatusCode.OK, CreatePostResponse("success", "Post created"))
        } else {
            println("❌ Failed to save post")
            call.respond(HttpStatusCode.InternalServerError, CreatePostResponse("error", "Failed to create post"))
        }
    }




    get("/postMetrics/{postId}") {
        val postId = call.parameters["postId"]?.toIntOrNull()
        if (postId == null) {
            call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid post ID"))
            return@get
        }

        val metrics = getPostMetrics(postId)
        call.respond(HttpStatusCode.OK, metrics)
    }

    post("/likePost") {
        val request = call.receive<LikePostRequest>()

        val success = addPostLike(request.postId, request.userId)

        if (success) {
            call.respond(HttpStatusCode.OK, LikePostResponse("success", "Post liked"))
            println("Like Post Working")
        } else {
            call.respond(HttpStatusCode.OK, LikePostResponse("error", "Post already liked or failed"))
            println("Like Post Not Working")
        }
    }
    post("/unlikePost") {
        val request = call.receive<LikePostRequest>()
        val success = removePostLike(request.postId, request.userId)
        if (success) {
            call.respond(HttpStatusCode.OK, LikePostResponse("success", "Post unliked"))
        } else {
            call.respond(HttpStatusCode.OK, LikePostResponse("error", "Post was not liked"))
        }
    }


    post("/quote-repost") {
        val request = try {
            call.receive<QuoteRepostRequest>()
        } catch (e: Exception) {
            call.respond(HttpStatusCode.BadRequest, QuoteRepostResponse("error", "Invalid request"))
            return@post
        }

        val success = saveQuoteRepost(request.userId, request.postId, request.comment)
        if (success) {
            call.respond(HttpStatusCode.OK, QuoteRepostResponse("success", "Post quote-reposted"))
        } else {
            call.respond(HttpStatusCode.OK, QuoteRepostResponse("exists", "You already quote-reposted this post"))
        }
    }
    post("/repostPost") {
        val request = call.receive<QuoteRepostRequest>()
        val success = saveQuoteRepost(request.userId, request.postId, null)
        if (success) {
            call.respond(HttpStatusCode.OK, QuoteRepostResponse("success", "Post reposted"))
        } else {
            call.respond(HttpStatusCode.OK, QuoteRepostResponse("exists", "Already reposted"))
        }
    }

    post("/removeRepost") {
        val request = call.receive<QuoteRepostRequest>()
        val success = removeRepost(request.userId, request.postId)
        if (success) {
            call.respond(HttpStatusCode.OK, QuoteRepostResponse("success", "Repost removed"))
        } else {
            call.respond(HttpStatusCode.OK, QuoteRepostResponse("error", "Repost not found"))
        }
    }



    post("/addView") {
        val request = try {
            call.receive<AddViewRequest>()
        } catch (e: Exception) {
            call.respond(HttpStatusCode.BadRequest, mapOf("status" to "error", "message" to "Invalid request"))
            return@post
        }

        val success = addPostView(request.postId, request.userId)

        if (success) {
            call.respond(HttpStatusCode.OK, mapOf("status" to "success", "message" to "View recorded"))
        } else {
            call.respond(HttpStatusCode.InternalServerError, mapOf("status" to "error", "message" to "Failed to record view"))
        }
    }



    post("/addBookmark") {
        val request = call.receive<BookmarkRequest>()
        val success = addBookmark(request.postId, request.userId)
        if (success) call.respond(HttpStatusCode.OK, BasicResponse("success", "Bookmarked"))
        else call.respond(HttpStatusCode.OK, BasicResponse("exists", "Already bookmarked"))
    }

    post("/removeBookmark") {
        val request = call.receive<BookmarkRequest>()
        val success = removeBookmark(request.postId, request.userId)
        if (success) call.respond(HttpStatusCode.OK, BasicResponse("success", "Bookmark removed"))
        else call.respond(HttpStatusCode.OK, BasicResponse("error", "Bookmark not found"))
    }

    get("/getBookmarks/{userId}") {
        val userId = call.parameters["userId"]?.toIntOrNull()
        if (userId == null) {
            call.respond(HttpStatusCode.BadRequest, BasicResponse("error", "Invalid user ID"))
            return@get
        }
        val bookmarks = getBookmarksByUser(userId)
        call.respond(HttpStatusCode.OK, bookmarks)
    }

    get("/recommendPostsFypFeed/{userId}") {
        val userId = call.parameters["userId"]?.toIntOrNull()
        if (userId == null) {
            call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid user ID"))
            return@get
        }

        val recommended = getRecommendedPosts(userId)
        call.respond(HttpStatusCode.OK, recommended)
    }

    get("/feed_image/{fileName}") {
        val fileName = call.parameters["fileName"] ?: return@get call.respond(HttpStatusCode.BadRequest)
        val file = File("uploads/posts/images/${fileName}")

        if (file.exists()) {
            call.respondFile(file)
        } else {
            call.respond(HttpStatusCode.NotFound)
        }
    }
    get("/feed_video/{fileName}") {
        val fileName = call.parameters["fileName"] ?: return@get call.respond(HttpStatusCode.BadRequest)
        val file = File("uploads/posts/videos/${fileName}")

        if (file.exists()) {
            call.respondFile(file)
        } else {
            call.respond(HttpStatusCode.NotFound)
        }
    }

    post("/community/posts") {
        val request = call.receive<CommunityPostsRequest>()

        val posts = getCommunityPosts(request.communityId, request.offset, request.limit)
        call.respond(posts)
    }
    get("/user_posts/{userId}") {
        val userId = call.parameters["userId"]?.toIntOrNull()
        if (userId == null) {
            call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid userId"))
            return@get
        }

        val page = call.request.queryParameters["page"]?.toIntOrNull() ?: 0
        val limit = call.request.queryParameters["limit"]?.toIntOrNull() ?: 20
        val offset = ((page - 1) * limit).toLong()

        val posts = getUserPosts(userId, limit = limit, offset = offset)
        call.respond(HttpStatusCode.OK, posts)
    }

    get("/getReplies/{postId}") {
        val postId = call.parameters["postId"]?.toIntOrNull()
        val userId = call.parameters["userId"]?.toIntOrNull() ?: 0
        val page = call.request.queryParameters["page"]?.toIntOrNull() ?: 0
        val limit = call.request.queryParameters["limit"]?.toIntOrNull() ?: 20
        val offset = ((page - 1) * limit).toLong()

        if (postId == null) {
            call.respond(HttpStatusCode.BadRequest, BasicResponse("error", "Invalid post ID"))
            return@get
        }

        val replies = getPostReplies(postId, userId, limit, offset)
        call.respond(HttpStatusCode.OK,  replies)
    }

    post("/addPostComment") {
        try {
            val request = call.receive<AddCommentRequest>()

            val success = addPostComment(
                postId = request.postId,
                userId = request.userId,
                commentText = request.commentText
            )

            if (success) {
                call.respond(HttpStatusCode.OK, StandardResponse("success", "Comment added successfully."))
            } else {
                call.respond(HttpStatusCode.BadRequest, StandardResponse("error", "Failed to add comment."))
            }
        } catch (e: Exception) {
            e.printStackTrace()
            call.respond(HttpStatusCode.InternalServerError, StandardResponse("error", "Something went wrong."))
        }
    }

    post("/bookmarks") {
        try {
            val request = call.receive<Map<String, Int>>()
            val userId = request["userId"] ?: return@post call.respond(HttpStatusCode.BadRequest)
            val bookmarks = getBookmarkedPosts(userId)
            call.respond(mapOf("bookmarks" to bookmarks))
        } catch (e: Exception) {
            e.printStackTrace()
            call.respond(HttpStatusCode.InternalServerError, "Failed to fetch bookmarks")
        }
    }












}