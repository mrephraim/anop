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
import java.nio.file.Paths


fun Route.postRoutes(){
    post("/createPost") {
        val multipart = call.receiveMultipart()
        var requestData: CreatePostRequest? = null
        val imageBytesList = mutableListOf<Pair<ByteArray, String>>()
        val videoBytesList = mutableListOf<Pair<ByteArray, String>>() // Multiple videos

        multipart.forEachPart { part ->
            when (part) {
                is PartData.FormItem -> if (part.name == "data") {
                    requestData = Json.decodeFromString<CreatePostRequest>(part.value)
                }

                is PartData.FileItem -> {
                    val fileName = part.originalFileName ?: "file.bin"
                    val bytes = part.provider().toByteArray()

                    when {
                        part.name?.startsWith("image") == true -> imageBytesList.add(bytes to fileName)
                        part.name?.startsWith("video") == true -> videoBytesList.add(bytes to fileName)
                    }
                }

                else -> Unit
            }
            part.dispose()
        }

        if (requestData == null) {
            call.respond(HttpStatusCode.BadRequest, CreatePostResponse("error", "Missing post data"))
            return@post
        }

        val uploadsPath = Paths.get("").toAbsolutePath().toString() + "/uploads"
        val imagePaths = mutableListOf<String>()
        val videoPaths = mutableListOf<String>()

        // Validate total image size
        val totalImageSize = imageBytesList.sumOf { it.first.size.toLong() }
        if (totalImageSize > 30 * 1024 * 1024) {
            call.respond(HttpStatusCode.BadRequest, CreatePostResponse("error", "Images exceed 30MB limit"))
            return@post
        }

        if (imageBytesList.size > 5) {
            call.respond(HttpStatusCode.BadRequest, CreatePostResponse("error", "Maximum 5 images allowed"))
            return@post
        }

        // Save images
        imageBytesList.forEachIndexed { index, (bytes, name) ->
            val folder = File("$uploadsPath/posts/images")
            if (!folder.exists()) folder.mkdirs()
            val uniqueName = "${requestData!!.shareAs}_${System.currentTimeMillis()}_${index}_$name"
            val file = File(folder, uniqueName)
            file.writeBytes(bytes)
            imagePaths.add(uniqueName)
        }

        // Save videos
        videoBytesList.forEachIndexed { index, (bytes, name) ->
            if (bytes.size > 30 * 1024 * 1024) {
                call.respond(HttpStatusCode.BadRequest, CreatePostResponse("error", "One of the videos exceeds 30MB limit"))
                return@post
            }

            val folder = File("$uploadsPath/posts/videos")
            if (!folder.exists()) folder.mkdirs()
            val uniqueName = "${requestData!!.shareAs}_${System.currentTimeMillis()}_${index}_$name"
            val file = File(folder, uniqueName)
            file.writeBytes(bytes)
            videoPaths.add(uniqueName)
        }

        // Use shareAs (user or community), type, shareTo, visibility
        val postId = savePost(
            shareAsId = requestData!!.shareAs,            // 👈 Correct ID for user or community
            type = requestData!!.type,
            textContent = requestData!!.textContent,
            shareTo = requestData!!.shareTo,
            visibility = requestData!!.visibility,
            imagePaths = imagePaths,
            videoPaths = videoPaths
        )

        if (postId != null) {
            call.respond(HttpStatusCode.OK, CreatePostResponse("success", "Post created"))
        } else {
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
        } else {
            call.respond(HttpStatusCode.OK, LikePostResponse("error", "Post already liked or failed"))
        }
    }

    post("/addComment") {
        val request = call.receive<AddCommentRequest>()
        val success = addPostComment(request.postId, request.userId, request.commentText, request.parentCommentId)
        if (success) call.respond(HttpStatusCode.OK, BasicResponse("success", "Comment added"))
        else call.respond(HttpStatusCode.InternalServerError, BasicResponse("error", "Failed to add comment"))
    }

    get("/getComments/{postId}") {
        val postId = call.parameters["postId"]?.toIntOrNull()
        if (postId == null) {
            call.respond(HttpStatusCode.BadRequest, BasicResponse("error", "Invalid post ID"))
            return@get
        }

        val comments = getCommentsByPost(postId)
        call.respond(HttpStatusCode.OK, CommentsListResponse("success", comments))
    }

    post("/editComment") {
        val request = call.receive<EditCommentRequest>()
        val success = editComment(request.commentId, request.userId, request.newText)
        if (success) call.respond(HttpStatusCode.OK, BasicResponse("success", "Comment edited"))
        else call.respond(HttpStatusCode.InternalServerError, BasicResponse("error", "Failed to edit comment"))
    }

    post("/editComment") {
        val request = call.receive<EditCommentRequest>()
        val success = editComment(request.commentId, request.userId, request.newText)
        if (success) call.respond(HttpStatusCode.OK, BasicResponse("success", "Comment edited"))
        else call.respond(HttpStatusCode.InternalServerError, BasicResponse("error", "Failed to edit comment"))
    }

    post("/deleteComment") {
        val request = call.receive<DeleteCommentRequest>()
        val success = deleteComment(request.commentId, request.userId)
        if (success) call.respond(HttpStatusCode.OK, BasicResponse("success", "Comment deleted"))
        else call.respond(HttpStatusCode.InternalServerError, BasicResponse("error", "Failed to delete comment"))
    }


    get("/getNestedComments/{postId}") {
        val postId = call.parameters["postId"]?.toIntOrNull()
        if (postId == null) {
            call.respond(HttpStatusCode.BadRequest, BasicResponse("error", "Invalid post ID"))
            return@get
        }

        val comments = getNestedCommentsByPost(postId)
        call.respond(HttpStatusCode.OK, NestedCommentsListResponse("success", comments))
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










}