package com.example.data.db_operations

import com.example.application.RedisProvider
import com.example.data.classes_daos.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.isNull
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
            textContent.let {
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
        val comments = Posts.selectAll().where { Posts.reply_to eq postId }.count().toInt()
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
                    it[PostLikes.createdAt] = DateTime.now()
                }
                val metrics = getPostMetrics(postId)
                // Prepare JSON payload
                val jsonPayload = buildJsonObject {
                    put("postId", postId)
                    put("likes", metrics.likes)
                    put("comments", metrics.comments)
                    put("reposts", metrics.reposts)
                    put("views", metrics.views)
                }.toString()

                // Publish to Redis
                RedisProvider.commands.publish("post:$postId:reaction", jsonPayload)
                println("📢 Published updated metrics to Redis for post $postId: $jsonPayload")
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

fun removePostLike(postId: Int, userId: Int): Boolean = try {
    transaction {
        val deleted = PostLikes.deleteWhere {
            (PostLikes.postId eq postId) and (PostLikes.userId eq userId)
        }
        val metrics = getPostMetrics(postId)
        // Prepare JSON payload
        val jsonPayload = buildJsonObject {
            put("postId", postId)
            put("likes", metrics.likes)
            put("comments", metrics.comments)
            put("reposts", metrics.reposts)
            put("views", metrics.views)
        }.toString()

        // Publish to Redis
        RedisProvider.commands.publish("post:$postId:reaction", jsonPayload)
        println("📢 Published updated metrics to Redis for post $postId: $jsonPayload")
        deleted > 0
    }
} catch (e: Exception) {
    e.printStackTrace()
    false
}


fun removeRepost(userId: Int, postId: Int): Boolean = try {
    transaction {
        val deleted = PostReposts.deleteWhere {
            (PostReposts.userId eq userId) and (originalPostId eq postId) and (comment.isNull())
        }
        val metrics = getPostMetrics(postId)
        // Prepare JSON payload
        val jsonPayload = buildJsonObject {
            put("postId", postId)
            put("likes", metrics.likes)
            put("comments", metrics.comments)
            put("reposts", metrics.reposts)
            put("views", metrics.views)
        }.toString()

        // Publish to Redis
        RedisProvider.commands.publish("post:$postId:reaction", jsonPayload)
        println("📢 Published updated metrics to Redis for post $postId: $jsonPayload")

        deleted > 0
    }
} catch (e: Exception) {
    e.printStackTrace()
    false
}


fun addPostComment(postId: Int, userId: Int, commentText: String): Boolean {
    return try {
        transaction {
            Posts.insert {
                it[Posts.userId] = userId
                it[textContent] = commentText
                it[type] = 1
                it[visibility] = 1
                it[reply_to] = postId
                it[createdAt] = DateTime.now()
            }
        }
        val metrics = getPostMetrics(postId)
        // Prepare JSON payload
        val jsonPayload = buildJsonObject {
            put("postId", postId)
            put("likes", metrics.likes)
            put("comments", metrics.comments)
            put("reposts", metrics.reposts)
            put("views", metrics.views)
        }.toString()

        // Publish to Redis
        RedisProvider.commands.publish("post:$postId:reaction", jsonPayload)
        println("📢 Published updated metrics to Redis for post $postId: $jsonPayload")
        true
    } catch (e: Exception) {
        e.printStackTrace()
        false
    }
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
                it[repostedAt] = DateTime.now()
            }
            val metrics = getPostMetrics(postId)
            // Prepare JSON payload
            val jsonPayload = buildJsonObject {
                put("postId", postId)
                put("likes", metrics.likes)
                put("comments", metrics.comments)
                put("reposts", metrics.reposts)
                put("views", metrics.views)
            }.toString()

            // Publish to Redis
            RedisProvider.commands.publish("post:$postId:reaction", jsonPayload)
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
        val metrics = getPostMetrics(postId)
        // Prepare JSON payload
        val jsonPayload = buildJsonObject {
            put("postId", postId)
            put("likes", metrics.likes)
            put("comments", metrics.comments)
            put("reposts", metrics.reposts)
            put("views", metrics.views)
        }.toString()

        // Publish to Redis
        RedisProvider.commands.publish("post:$postId:reaction", jsonPayload)
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
            it[bookmarkedAt] = DateTime.now()
        }
        val metrics = getPostMetrics(postId)
        // Prepare JSON payload
        val jsonPayload = buildJsonObject {
            put("postId", postId)
            put("likes", metrics.likes)
            put("comments", metrics.comments)
            put("reposts", metrics.reposts)
            put("views", metrics.views)
        }.toString()

        // Publish to Redis
        RedisProvider.commands.publish("post:$postId:reaction", jsonPayload)
        true
    }
}

fun removeBookmark(postId: Int, userId: Int): Boolean {
    return transaction {
        val deletedRows = PostBookmarks.deleteWhere {
            (PostBookmarks.postId eq postId) and (PostBookmarks.userId eq userId)
        }
        val metrics = getPostMetrics(postId)
        // Prepare JSON payload
        val jsonPayload = buildJsonObject {
            put("postId", postId)
            put("likes", metrics.likes)
            put("comments", metrics.comments)
            put("reposts", metrics.reposts)
            put("views", metrics.views)
        }.toString()

        // Publish to Redis
        RedisProvider.commands.publish("post:$postId:reaction", jsonPayload)
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
                val absoluteUrl = "https://grub-hardy-actively.ngrok-free.app/feed_image/$filePath"
                PostMediaItem(mediaType, absoluteUrl)
            }else{
                val absoluteUrl = "https://grub-hardy-actively.ngrok-free.app/feed_video/$filePath"
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
        val interaction = getUserPostInteractions(userId, post.id)

        FullPostResponse(
            postId = post.id,
            postWriteup = post.content,
            media = media,
            metrics = post.metrics,
            authorId = post.authorId,
            authorUsername = userProfile?.username ?: "",
            authorFullName = "${userProfile?.firstName.orEmpty()} ${userProfile?.lastName.orEmpty()}",
            authorProfilePictureUrl = userProfile?.profilePicturePath,
            createdAt = post.createdAt.toString(),
            isLiked = interaction.isLiked,
            isReposted = interaction.isReposted,
            isBookmarked = interaction.isBookmarked
        )
    }
}


fun getUserPostInteractions(userId: Int, postId: Int): InteractionStatus {
    return transaction {
        val liked = PostLikes
            .selectAll().where{ (PostLikes.postId eq postId) and (PostLikes.userId eq userId) }
            .any()

        val reposted = PostReposts
            .selectAll().where { (PostReposts.originalPostId eq postId) and (PostReposts.userId eq userId) }
            .any()

        val bookmarked = PostBookmarks
            .selectAll().where { (PostBookmarks.postId eq postId) and (PostBookmarks.userId eq userId) }
            .any()

        InteractionStatus(
            isLiked = liked,
            isReposted = reposted,
            isBookmarked = bookmarked
        )
    }
}


fun getCommunityPosts(
    communityId: Int,
    offset: Long = 0,
    userId: Int,
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
                val interaction = getUserPostInteractions(userId, postId)

                FullPostResponse(
                    postId = postId,
                    postWriteup = postRow[Posts.textContent],
                    media = getPostMedia(postId),
                    metrics = getPostMetrics(postId),
                    authorId = authorInfo.id,
                    authorUsername = authorInfo.username,
                    authorFullName = authorInfo.fullName,
                    authorProfilePictureUrl = authorInfo.profilePictureUrl,
                    createdAt = postRow[Posts.createdAt].toString(),
                    isLiked = interaction.isLiked,
                    isReposted = interaction.isReposted,
                    isBookmarked = interaction.isBookmarked
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

                val media = getPostMedia(postId)
                val metrics = getPostMetrics(postId)
                val userProfile = getUserProfileDetails(userId)
                val interaction = getUserPostInteractions(userId, postId)

                FullPostResponse(
                    postId = postId,
                    postWriteup = content,
                    media = media,
                    metrics = metrics,
                    authorId = userId,
                    authorUsername = userProfile?.username ?: "",
                    authorFullName = "${userProfile?.firstName.orEmpty()} ${userProfile?.lastName.orEmpty()}",
                    authorProfilePictureUrl = userProfile?.profilePicturePath,
                    createdAt = createdAt.toString(),
                    isLiked = interaction.isLiked,
                    isReposted = interaction.isReposted,
                    isBookmarked = interaction.isBookmarked
                )
            }
    }
}

fun getPostReplies(postId: Int, userId: Int,  limit: Int = 20, offset: Long = 0L): List<FullPostResponse> {
    return transaction {
        Posts
            .selectAll().where { Posts.reply_to eq postId }
            .orderBy(Posts.createdAt, SortOrder.ASC)
            .limit(limit)
            .offset(offset)
            .map { row ->
                val replyId = row[Posts.id].value
                val content = row[Posts.textContent]
                val createdAt = row[Posts.createdAt]
                val authorId = row[Posts.userId]

                val metrics = getPostMetrics(replyId)
                val userProfile = getUserProfileDetails(authorId)
                val interaction = getUserPostInteractions(userId, postId)

                FullPostResponse(
                    postId = replyId,
                    postWriteup = content,
                    media = emptyList(), // No media for replies
                    metrics = metrics,
                    authorId = authorId,
                    authorUsername = userProfile?.username ?: "",
                    authorFullName = "${userProfile?.firstName.orEmpty()} ${userProfile?.lastName.orEmpty()}",
                    authorProfilePictureUrl = userProfile?.profilePicturePath,
                    createdAt = createdAt.toString("yyyy-MM-dd HH:mm:ss"),
                    isLiked = interaction.isLiked,
                    isReposted = interaction.isReposted,
                    isBookmarked = interaction.isBookmarked
                )
            }
    }
}

fun getFullPostResponse(postId: Int, userId: Int): FullPostResponse? {
    return transaction {
        val row = Posts
            .selectAll().where{ Posts.id eq postId }
            .limit(1)
            .firstOrNull() ?: return@transaction null

        val content = row[Posts.textContent]
        val createdAt = row[Posts.createdAt]
        val authorId = row[Posts.userId]

        val metrics = getPostMetrics(postId)
        val userProfile = getUserProfileDetails(authorId)
        val interaction = getUserPostInteractions(userId, postId)

        FullPostResponse(
            postId = postId,
            postWriteup = content,
            media = getPostMedia(postId), // use your media function if applicable, or emptyList()
            metrics = metrics,
            authorId = authorId,
            authorUsername = userProfile?.username ?: "",
            authorFullName = "${userProfile?.firstName.orEmpty()} ${userProfile?.lastName.orEmpty()}",
            authorProfilePictureUrl = userProfile?.profilePicturePath,
            createdAt = createdAt.toString("yyyy-MM-dd HH:mm:ss"),
            isLiked = interaction.isLiked,
            isReposted = interaction.isReposted,
            isBookmarked = interaction.isBookmarked
        )
    }
}




