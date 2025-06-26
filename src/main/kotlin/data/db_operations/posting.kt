package com.example.data.db_operations

import com.example.data.classes_daos.*
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction
import org.joda.time.DateTime

fun savePost(
    shareAsId: Int,               // 👈 userId or communityId (depending on 'type')
    type: Int,                    // 1 = Normal Post, 2 = Community Post
    textContent: String,
    shareTo: Int?,                // nullable → ID of specific community or null
    visibility: Int,              // 1 = Everyone, 2 = Connections only, 3 = Community only
    imagePaths: List<String> = emptyList(),
    videoPaths: List<String> = emptyList()
): Int? {
    return try {
        transaction {
            val postId = Posts.insertAndGetId {
                it[Posts.userId] = shareAsId
                it[Posts.type] = type
                it[Posts.textContent] = textContent
                it[Posts.shareAs] = shareAsId
                it[Posts.shareTo] = shareTo
                it[Posts.visibility] = visibility
                it[createdAt] = DateTime.now()
            }.value

            // Insert image media
            imagePaths.forEach { imagePath ->
                PostMedia.insert {
                    it[PostMedia.postId] = postId
                    it[mediaType] = "image"
                    it[filePath] = imagePath
                }
            }

            // Insert video media
            videoPaths.forEach { path ->
                PostMedia.insert {
                    it[PostMedia.postId] = postId
                    it[mediaType] = "video"
                    it[filePath] = path
                }
            }

            // Insert hashtags
            textContent?.let {
                extractHashtags(it).forEach { tag ->
                    PostHashtags.insert {
                        it[PostHashtags.postId] = postId
                        it[PostHashtags.tag] = tag
                    }
                }

                // Insert matched categories
                matchCategories(it).forEach { catId ->
                    PostCategoryMatches.insert {
                        it[PostCategoryMatches.postId] = postId
                        it[categoryId] = catId
                    }
                }
            }

            postId
        }
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}



fun extractHashtags(text: String): List<String> =
    Regex("#\\w+").findAll(text).map { it.value.lowercase() }.toList()

fun matchCategories(text: String): List<Int> {
    val words = text.lowercase().split(" ", "\n").map { it.trim().filter { c -> c.isLetterOrDigit() } }
    val matches = mutableListOf<Int>()

    transaction {
        InterestCategoryTable.selectAll().forEach { row ->
            val interest = row[InterestCategoryTable.interest].lowercase()
            val related = Json.decodeFromString<List<String>>(row[InterestCategoryTable.relatedInterests])
                .map { it.lowercase() }
            if (interest in words || related.any { it in words }) {
                matches.add(row[InterestCategoryTable.id])
            }
        }
    }

    return matches.distinct()
}


fun getPostMetrics(postId: Int): PostMetricsResponse {
    return transaction {
        val likes = PostLikes.selectAll().where { PostLikes.postId eq postId }.count().toInt()
        val comments = PostComments.selectAll().where { PostComments.postId eq postId }.count().toInt()
        val reposts = PostReposts.selectAll().where { PostReposts.originalPostId eq postId }.count().toInt()
        val views = PostViews.selectAll().where { PostViews.postId eq postId }.count().toInt()
        PostMetricsResponse(likes, comments, reposts, views)
    }
}


fun addPostLike(postId: Int, userId: Int): Boolean {
    return try {
        transaction {
            val alreadyLiked = PostLikes.selectAll().where {
                (PostLikes.postId eq postId) and (PostLikes.userId eq userId)
            }.any()

            if (!alreadyLiked) {
                PostLikes.insert {
                    it[PostLikes.postId] = postId
                    it[PostLikes.userId] = userId
                    it[PostLikes.createdAt] = org.joda.time.DateTime.now()
                }
                true
            } else {
                false // Already liked
            }
        }
    } catch (e: Exception) {
        e.printStackTrace()
        false
    }
}


fun addPostComment(postId: Int, userId: Int, commentText: String, parentCommentId: Int? = null): Boolean {
    return try {
        transaction {
            PostComments.insert {
                it[PostComments.postId] = postId
                it[PostComments.userId] = userId
                it[PostComments.commentText] = commentText
                it[PostComments.parentCommentId] = parentCommentId
                it[createdAt] = org.joda.time.DateTime.now()
            }
        }
        true
    } catch (e: Exception) {
        e.printStackTrace()
        false
    }
}


fun getCommentsByPost(postId: Int): List<CommentResponse> {
    return transaction {
        PostComments.selectAll().where { PostComments.postId eq postId }
            .orderBy(PostComments.createdAt to SortOrder.ASC)
            .map {
                CommentResponse(
                    commentId = it[PostComments.id],
                    userId = it[PostComments.userId],
                    commentText = it[PostComments.commentText],
                    createdAt = it[PostComments.createdAt].toString("yyyy-MM-dd HH:mm:ss"),
                    parentCommentId = it[PostComments.parentCommentId]
                )
            }
    }
}

fun editComment(commentId: Int, userId: Int, newText: String): Boolean {
    return try {
        transaction {
            PostComments.update({
                PostComments.id eq commentId and (PostComments.userId eq userId)
            }) {
                it[commentText] = newText
            }
        } > 0
    } catch (e: Exception) {
        e.printStackTrace()
        false
    }
}

fun deleteComment(commentId: Int, userId: Int): Boolean {
    return try {
        transaction {
            // First delete replies
            PostComments.deleteWhere {
                parentCommentId eq commentId
            }
            // Then delete main comment
            PostComments.deleteWhere {
                PostComments.id eq commentId and (PostComments.userId eq userId)
            }
        } > 0
    } catch (e: Exception) {
        e.printStackTrace()
        false
    }
}


fun getNestedCommentsByPost(postId: Int): List<NestedCommentResponse> {
    val allComments = transaction {
        PostComments.selectAll().where { PostComments.postId eq postId }
            .orderBy(PostComments.createdAt to SortOrder.ASC)
            .map {
                NestedCommentResponse(
                    commentId = it[PostComments.id],
                    userId = it[PostComments.userId],
                    commentText = it[PostComments.commentText],
                    createdAt = it[PostComments.createdAt].toString("yyyy-MM-dd HH:mm:ss"),
                    parentCommentId = it[PostComments.parentCommentId]
                )
            }
    }

    val commentMap = allComments.associateBy { it.commentId }.toMutableMap()
    val rootComments = mutableListOf<NestedCommentResponse>()

    allComments.forEach { comment ->
        val parentId = comment.parentCommentId
        if (parentId == null) {
            rootComments.add(comment)
        } else {
            val parent = commentMap[parentId]
            parent?.replies?.add(comment)
        }
    }

    return rootComments
}

fun saveQuoteRepost(userId: Int, postId: Int, comment: String?): Boolean {
    return try {
        transaction {
            // Allow multiple reposts if comment is different (optional rule)
            val alreadyReposted = PostReposts
                .selectAll().where {
                    (PostReposts.userId eq userId) and
                            (PostReposts.originalPostId eq postId) and
                            (PostReposts.comment eq comment)
                }.count() > 0

            if (alreadyReposted) return@transaction false

            PostReposts.insert {
                it[PostReposts.userId] = userId
                it[originalPostId] = postId
                it[PostReposts.comment] = comment
                it[repostedAt] = org.joda.time.DateTime.now()
            }
            true
        }
    } catch (e: Exception) {
        e.printStackTrace()
        false
    }
}

fun addPostView(postId: Int, userId: Int?): Boolean {
    return transaction {
        val now = DateTime.now()
        val todayStart = now.withTimeAtStartOfDay()
        val todayEnd = todayStart.plusDays(1)

        if (userId != null) {
            // Check if user already viewed this post today
            val alreadyViewed = PostViews.selectAll().where {
                (PostViews.postId eq postId) and
                        (PostViews.userId eq userId) and
                        (PostViews.viewedAt.between(todayStart, todayEnd))
            }.count() > 0

            if (alreadyViewed) {
                // User already viewed post today, skip insertion
                return@transaction false
            }
        }
        // Insert new view record
        PostViews.insert {
            it[PostViews.postId] = postId
            it[PostViews.userId] = userId
            it[PostViews.viewedAt] = now
        }
        true
    }
}


fun addBookmark(postId: Int, userId: Int): Boolean {
    return transaction {
        // Check if bookmark exists to avoid duplicates
        val exists = PostBookmarks.selectAll().where {
            (PostBookmarks.postId eq postId) and (PostBookmarks.userId eq userId)
        }.count() > 0

        if (exists) return@transaction false

        PostBookmarks.insert {
            it[PostBookmarks.postId] = postId
            it[PostBookmarks.userId] = userId
            it[bookmarkedAt] = org.joda.time.DateTime.now()
        }
        true
    }
}

fun removeBookmark(postId: Int, userId: Int): Boolean {
    return transaction {
        val deletedRows = PostBookmarks.deleteWhere {
            (PostBookmarks.postId eq postId) and (PostBookmarks.userId eq userId)
        }
        deletedRows > 0
    }
}

fun getBookmarksByUser(userId: Int): List<Int> {
    return transaction {
        PostBookmarks.selectAll().where { PostBookmarks.userId eq userId }
            .map { it[PostBookmarks.postId].value }
    }
}


fun computePostScore(
    userId: Int,
    post: RawPost,
    interestKeywords: Set<String>,
    userPostKeywords: Set<String>
): Double {
    var score = 0.0

    // Engagement
    score += post.metrics.likes * 1.5
    score += post.metrics.comments * 2.0
    score += post.metrics.reposts * 2.0
    score += post.metrics.views * 0.5

    // Match on hashtags or text content
    val wordsInPost = post.content.lowercase().split("\\s+".toRegex()).map { it.trim() }
    val keywordMatches = wordsInPost.count { it in interestKeywords || it in userPostKeywords }
    score += keywordMatches * 3.0

    // Hashtag relevance
    val hashtagMatches = post.hashtags.count { it in interestKeywords }
    score += hashtagMatches * 2.5

    // Relationship bonus
    if (areYouFollowing(userId, post.authorId)) score += 10
    else if (isMutualRelationship(userId, post.authorId)) score += 7

    // Author popularity
    score += getFollowerCount(post.authorId) / 500.0

    // Recency (light decay)
    val daysOld = (DateTime.now().millis - post.createdAt.millis) / (1000 * 60 * 60 * 24)
    score -= minOf(daysOld * 0.5, 10.0)

    return score
}

fun getInterestKeywordsFromIds(interestIds: Set<Int>): Set<String> = transaction {
    InterestCategoryTable.selectAll().where { InterestCategoryTable.id inList interestIds.toList() }
        .flatMap {
            val main = it[InterestCategoryTable.interest].lowercase()
            val related = Json.decodeFromString<List<String>>(it[InterestCategoryTable.relatedInterests])
                .map { rel -> rel.lowercase() }
            listOf(main) + related
        }.toSet()
}

fun getUserPostKeywords(userId: Int): Set<String> = transaction {
    Posts.selectAll().where { Posts.userId eq userId }
        .flatMap { it[Posts.textContent].lowercase().split("\\s+".toRegex()) }
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .toSet()
}

fun getFollowerCount(userId: Int): Int = transaction {
    Followers.selectAll().where { Followers.followingUserId eq userId and (Followers.status inList listOf(1, 2)) }.count().toInt()
}

fun getPostMedia(postId: Int): List<PostMediaItem> = transaction {
    PostMedia.selectAll()
        .where { PostMedia.postId eq postId }
        .map {
            val filePath = it[PostMedia.filePath]
            val mediaType = it[PostMedia.mediaType]
            if(mediaType == "image"){
                val absoluteUrl = "http://10.0.2.2:8080/feed_image/$filePath"
                PostMediaItem(mediaType, absoluteUrl)
            }else{
                val absoluteUrl = "http://10.0.2.2:8080/feed_video/$filePath"
                PostMediaItem(mediaType, absoluteUrl)
            }
        }
}

fun getRecommendedPosts(userId: Int, limit: Int = 30): List<FullPostResponse> {
    val userInterestIds = getUserInterests(userId).interestIds.toSet()
    val interestKeywords = getInterestKeywordsFromIds(userInterestIds)
    val userPostKeywords = getUserPostKeywords(userId)

    val candidatePosts = transaction {
        Posts
            .selectAll().where { Posts.userId neq userId } // ✅ Filter out current user's posts in DB query
            .orderBy(Posts.createdAt, SortOrder.DESC)
            .limit(100)
            .mapNotNull {
                val authorId = it[Posts.userId]
                if (authorId == userId) return@mapNotNull null // ✅ Redundant but safe double-check

                val postId = it[Posts.id].value
                val content = it[Posts.textContent]

                val hashtags = PostHashtags
                    .selectAll().where { PostHashtags.postId eq postId }
                    .map { tag -> tag[PostHashtags.tag].lowercase() }

                val metrics = getPostMetrics(postId)

                RawPost(
                    id = postId,
                    authorId = authorId,
                    content = content,
                    hashtags = hashtags,
                    createdAt = it[Posts.createdAt],
                    metrics = metrics
                )
            }
    }

    val scored = candidatePosts
        .map { post ->
            val score = computePostScore(userId, post, interestKeywords, userPostKeywords)
            post to score
        }
        .sortedByDescending { it.second }
        .take(limit)

    return scored.map { (post, _) ->
        val media = getPostMedia(post.id)
        val userProfile = getUserProfileDetails(post.authorId)

        FullPostResponse(
            postId = post.id,
            postWriteup = post.content,
            media = media,
            metrics = post.metrics,
            authorId = post.authorId,
            authorUsername = userProfile?.username ?: "",
            authorFullName = "${userProfile?.firstName.orEmpty()} ${userProfile?.lastName.orEmpty()}",
            authorProfilePictureUrl = userProfile?.profilePicturePath,
            createdAt = post.createdAt.toString()
        )
    }
}


fun getCommunityPosts(
    communityId: Int,
    offset: Long = 0,
    limit: Int = 30
): List<FullPostResponse> {
    return transaction {
        Posts
            .selectAll().where { (Posts.type eq 2) and (Posts.shareTo eq communityId) }
            .orderBy(Posts.createdAt, SortOrder.DESC)
            .limit(count = limit).offset(start = offset)
            .map { postRow ->
                val postId = postRow[Posts.id].value
                val shareAs = postRow[Posts.shareAs]

                val authorInfo = if (shareAs == communityId) {
                    val community = getCommunityInfoById(communityId)
                    AuthorInfo(
                        id = community?.id ?: communityId,
                        username = "admin",
                        fullName = community?.name ?: "",
                        profilePictureUrl = community?.profilePicturePath
                    )
                } else {
                    val userProfile = getUserProfileDetails2(postRow[Posts.userId])
                    AuthorInfo(
                        id = userProfile?.userId ?: postRow[Posts.userId],
                        username = userProfile?.username ?: "",
                        fullName = "${userProfile?.firstName.orEmpty()} ${userProfile?.lastName.orEmpty()}",
                        profilePictureUrl = userProfile?.profilePicturePath
                    )
                }

                FullPostResponse(
                    postId = postId,
                    postWriteup = postRow[Posts.textContent],
                    media = getPostMedia(postId),
                    metrics = getPostMetrics(postId),
                    authorId = authorInfo.id,
                    authorUsername = authorInfo.username,
                    authorFullName = authorInfo.fullName,
                    authorProfilePictureUrl = authorInfo.profilePictureUrl,
                    createdAt = postRow[Posts.createdAt].toString()
                )
            }
    }
}

fun getUserPosts(userId: Int, limit: Int = 20, offset: Long = 0L): List<FullPostResponse> {
    return transaction {
        Posts
            .selectAll().where { Posts.userId eq userId }
            .orderBy(Posts.createdAt, SortOrder.DESC)
            .limit(limit)
            .offset(offset)
            .map { row ->
                val postId = row[Posts.id].value
                val content = row[Posts.textContent]
                val createdAt = row[Posts.createdAt]

                val hashtags = PostHashtags
                    .selectAll().where { PostHashtags.postId eq postId }
                    .map { it[PostHashtags.tag] }

                val media = getPostMedia(postId)
                val metrics = getPostMetrics(postId)
                val userProfile = getUserProfileDetails(userId)

                FullPostResponse(
                    postId = postId,
                    postWriteup = content,
                    media = media,
                    metrics = metrics,
                    authorId = userId,
                    authorUsername = userProfile?.username ?: "",
                    authorFullName = "${userProfile?.firstName.orEmpty()} ${userProfile?.lastName.orEmpty()}",
                    authorProfilePictureUrl = userProfile?.profilePicturePath,
                    createdAt = createdAt.toString()
                )
            }
    }
}


