package com.example.data.classes_daos

import kotlinx.serialization.Serializable
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.jodatime.datetime


object Projects : Table("projects") {
    val id = integer("id").autoIncrement().uniqueIndex()
    val userId = integer("user_id") // Who uploaded the project

    // Stage 1: Overview
    val title = varchar("title", 255).nullable()
    val shortDescription = text("short_description").nullable()
    val projectCategory = integer("project_category").nullable()
    val tags = text("tags").nullable() // JSON or comma-separated

    // Stage 2: Media & Documentation
    val caseStudy = text("case_study").nullable()
    val coverImagePath = varchar("cover_image_path", 500).nullable()
    val mediaPaths = text("media_paths").nullable() // JSON or comma-separated

    // Stage 3: Team and Collaboration
    val projectStatus = varchar("project_status", 50).nullable() // "COMPLETED", "ONGOING", etc.
    val roleId = integer("role_id").nullable()
    val teamMemberIds = text("team_member_ids").nullable() // JSON or comma-separated

    // Stage 4: Publishing Options
    val autoPost = bool("auto_post").nullable()
    val notifyConnections = bool("notify_connections").nullable()

    // Meta
    val createdAt = datetime("created_at")
    val updatedAt = datetime("updated_at")
}


@Serializable
data class UploadProjectRequest(
    val overview: ProjectOverview,
    val mediaAndDocs: MediaAndDocumentation,
    val teamAndCollab: TeamAndCollaboration,
    val publishing: PublishingOptions
)

@Serializable
data class ProjectOverview(
    val title: String,
    val shortDescription: String,
    val projectCategory: Int,
    val tags: List<String> // List of hashtags like #AI, #Health
)
@Serializable
data class MediaAndDocumentation(
    val caseStudy: String,
    val coverImagePath: String, // File path to cover image
    val mediaPaths: List<String> // Images/Videos file paths
)

enum class ProjectStatus {
    COMPLETED,
    ONGOING,
    SEEKING_COLLABORATORS
}
@Serializable
data class TeamAndCollaboration(
    val projectStatus: ProjectStatus,
    val roleId: Int, // User's role in project (e.g., 1 = Developer)
    val teamMemberIds: List<String> // List of user IDs
)
@Serializable
data class PublishingOptions(
    val autoPost: Boolean, // Question 1: Auto post about added project
    val notifyConnections: Boolean // Question 2: Notify connections
)

@Serializable
data class UploadProjectStageRequest(
    val userId: Int,
    val deviceId: String,
    val refreshToken: String,
    val projectId: Int? = null,
    val stage: Int,
    val data: UploadProjectRequest
)

