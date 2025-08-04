package com.example.data.classes_daos

import kotlinx.serialization.Serializable

@Serializable
data class UserSearchResult(
    val userId: Int,
    val fullName: String,
    val username: String,
    val profileUrl: String?,
    val score: Int
)

@Serializable
data class CommunitySearchResult(
    val id: Int,
    val name: String,
    val description: String,
    val profilePictureUrl: String?,
    val coverPhotoUrl: String?,
    val score: Int,
    val category: String,
    val userCount: Int,
    val isAlreadyMember: Boolean
)


@Serializable
data class SuggestedUser(
    val id: Int,
    val name: String,
    val isFollower: Boolean? = false,
    val username: String,
    val imageUrl: String,
    val isFollowing: Boolean = false
)
