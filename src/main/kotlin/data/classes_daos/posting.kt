package com.example.data.classes_daos

import com.example.data.models.UserInitials
import org.jetbrains.exposed.sql.Table
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.sql.jodatime.datetime
import org.joda.time.DateTime

object Posts : IntIdTable("posts") {
    val userId = integer("user_id")
    val textContent = text("text_content")
    val type = integer("type") // 1 = Normal Post, 2 = Community Post

    val shareTo = integer("share_to").nullable() // Community ID if specifically shared to a community
    val shareAs = integer("share_as").nullable()
    val visibility = integer("visibility") // 1 = Everyone, 2 = Connections Only, 3 = Community Only

    val createdAt = datetime("created_at")
}


object PostMedia : Table("post_media") {
    val id = integer("id").autoIncrement()
    val postId = reference("post_id", Posts)
    val mediaType = varchar("media_type", 10)
    val filePath = varchar("file_path", 255)
    override val primaryKey = PrimaryKey(id)
}

object PostHashtags : Table("hashtags") {
    val id = integer("id").autoIncrement()
    val postId = reference("post_id", Posts)
    val tag = varchar("tag", 100)
    override val primaryKey = PrimaryKey(id)
}

object PostCategoryMatches : Table("post_category_matches") {
    val id = integer("id").autoIncrement()
    val postId = reference("post_id", Posts)
    val categoryId = integer("category_id") references InterestCategoryTable.id
    override val primaryKey = PrimaryKey(id)
}

object PostLikes : Table("post_likes") {
    val id = integer("id").autoIncrement()
    val postId = reference("post_id", Posts)
    val userId = integer("user_id")
    val createdAt = datetime("created_at")
    override val primaryKey = PrimaryKey(id)
}

object PostComments : Table("post_comments") {
    val id = integer("id").autoIncrement()
    val postId = reference("post_id", Posts)
    val userId = integer("user_id") references UserInitials.id
    val commentText = text("comment_text")
    val parentCommentId = integer("parent_comment_id").nullable() // null = top-level comment
    val createdAt = datetime("created_at")
    override val primaryKey = PrimaryKey(id)
}



object PostReposts : Table("post_reposts") {
    val id = integer("id").autoIncrement()
    val userId = integer("user_id")
    val originalPostId = reference("original_post_id", Posts)
    val comment = varchar("comment", 500).nullable() // Optional quote/comment
    val repostedAt = datetime("reposted_at")
    override val primaryKey = PrimaryKey(id)
}



object PostViews : Table("post_views") {
    val id = integer("id").autoIncrement()
    val postId = reference("post_id", Posts)
    val userId = integer("user_id").nullable() // Optional if tracking anonymous views
    val viewedAt = datetime("viewed_at")
    override val primaryKey = PrimaryKey(id)
}

object PostBookmarks : Table("post_bookmarks") {
    val id = integer("id").autoIncrement()
    val postId = reference("post_id", Posts)
    val userId = integer("user_id") references UserInitials.id
    val bookmarkedAt = datetime("bookmarked_at")
    override val primaryKey = PrimaryKey(id)
}


@Serializable
data class AddViewRequest(val postId: Int, val userId: Int?)



@Serializable
data class CreatePostRequest(
    val shareAs: Int,
    val type: Int,
    val textContent: String,
    val shareTo: Int?,
    val visibility: Int
)

@Serializable
data class CreatePostResponse(
    val status: String,
    val message: String
)

@Serializable
data class Hashtag(val postId: Int, val tag: String)

@Serializable
data class PostCategoryMatch(val postId: Int, val categoryId: Int)

@Serializable
data class PostMetricsResponse(
    val likes: Int,
    val comments: Int,
    val reposts: Int,
    val views: Int
)

@Serializable
data class LikePostRequest(
    val postId: Int,
    val userId: Int
)

@Serializable
data class LikePostResponse(
    val status: String,
    val message: String
)


@Serializable
data class AddCommentRequest(
    val postId: Int,
    val userId: Int,
    val commentText: String,
    val parentCommentId: Int? = null
)

@Serializable
data class CommentResponse(
    val commentId: Int,
    val userId: Int,
    val commentText: String,
    val createdAt: String,
    val parentCommentId: Int? = null
)

@Serializable
data class CommentsListResponse(
    val status: String,
    val comments: List<CommentResponse>
)

@Serializable
data class BasicResponse(val status: String, val message: String)

@Serializable
data class EditCommentRequest(val commentId: Int, val userId: Int, val newText: String)

@Serializable
data class DeleteCommentRequest(val commentId: Int, val userId: Int)


@Serializable
data class NestedCommentResponse(
    val commentId: Int,
    val userId: Int,
    val commentText: String,
    val createdAt: String,
    val parentCommentId: Int? = null,
    val replies: MutableList<NestedCommentResponse> = mutableListOf()
)

@Serializable
data class NestedCommentsListResponse(
    val status: String,
    val comments: List<NestedCommentResponse>
)


@Serializable
data class QuoteRepostRequest(
    val userId: Int,
    val postId: Int,
    val comment: String? = null // Optional
)

@Serializable
data class QuoteRepostResponse(val status: String, val message: String)


@Serializable
data class BookmarkRequest(val postId: Int, val userId: Int)

@Serializable
data class RecommendedPost(
    val postId: Int,
    val authorId: Int,
    val score: Double
)

data class RawPost(
    val id: Int,
    val authorId: Int,
    val content: String,
    val hashtags: List<String>,
    val createdAt: DateTime,
    val metrics: PostMetricsResponse
)


@Serializable
data class PostMediaItem(
    val mediaType: String,
    val mediaUrl: String
)

@Serializable
data class FullPostResponse(
    val postId: Int,
    val postWriteup: String,
    val media: List<PostMediaItem>,
    val metrics: PostMetricsResponse,
    val authorId: Int,
    val authorUsername: String,
    val authorFullName: String,
    val authorProfilePictureUrl: String?,
    val createdAt: String
)

data class AuthorInfo(
    val id: Int,
    val username: String,
    val fullName: String,
    val profilePictureUrl: String?
)

@Serializable
data class CommunityPostsRequest(
    val communityId: Int,
    val offset: Long = 0,
    val limit: Int = 30
)
