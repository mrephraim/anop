package com.example.data.db_operations

import com.example.data.classes_daos.Projects
import com.example.data.classes_daos.Projects.autoPost
import com.example.data.classes_daos.Projects.caseStudy
import com.example.data.classes_daos.Projects.coverImagePath
import com.example.data.classes_daos.Projects.mediaPaths
import com.example.data.classes_daos.Projects.notifyConnections
import com.example.data.classes_daos.Projects.projectCategory
import com.example.data.classes_daos.Projects.projectStatus
import com.example.data.classes_daos.Projects.roleId
import com.example.data.classes_daos.Projects.shortDescription
import com.example.data.classes_daos.Projects.tags
import com.example.data.classes_daos.Projects.teamMemberIds
import com.example.data.classes_daos.Projects.title
import com.example.data.classes_daos.UploadProjectRequest
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.update
import org.joda.time.DateTime

fun generateNewProjectId(): Int {
    return (100000..999999).random()
}


fun uploadProjectStage(userId: Int, projectId: Int?, stage: Int, data: UploadProjectRequest): String {
    return transaction {
        // Verify userId exists first (you need to create this function for UUID userIds)
        if (!isUserProfileExists(userId)) {
            return@transaction "User with id $userId does not exist."
        }

        val existingProject = projectId?.let {
            Projects.selectAll()
                .where { (Projects.id eq projectId) and (Projects.userId eq userId) }
                .singleOrNull()
        }

        val now = DateTime.now()

        if (existingProject == null && stage != 1) {
            return@transaction "You must start with stage 1 (overview) to create a new project."
        }

        val finalProjectId = existingProject?.get(Projects.id) ?: generateNewProjectId()

        when (stage) {
            1 -> {
                // Insert or update overview
                if (existingProject == null) {
                    Projects.insert {
                        it[id] = finalProjectId
                        it[Projects.userId] = userId
                        it[title] = data.overview.title
                        it[shortDescription] = data.overview.shortDescription
                        it[projectCategory] = data.overview.projectCategory
                        it[tags] = Json.encodeToString(data.overview.tags)
                        it[createdAt] = now
                        it[updatedAt] = now
                    }
                } else {
                    Projects.update({ Projects.id eq finalProjectId }) {
                        it[title] = data.overview.title
                        it[shortDescription] = data.overview.shortDescription
                        it[projectCategory] = data.overview.projectCategory
                        it[tags] = Json.encodeToString(data.overview.tags)
                        it[updatedAt] = now
                    }
                }
            }

            2 -> {
                Projects.update({ Projects.id eq finalProjectId }) {
                    it[caseStudy] = data.mediaAndDocs.caseStudy
                    it[coverImagePath] = data.mediaAndDocs.coverImagePath
                    it[mediaPaths] = Json.encodeToString(data.mediaAndDocs.mediaPaths)
                    it[updatedAt] = now
                }
            }

            3 -> {
                Projects.update({ Projects.id eq finalProjectId }) {
                    it[projectStatus] = data.teamAndCollab.projectStatus.name
                    it[roleId] = data.teamAndCollab.roleId
                    it[teamMemberIds] = Json.encodeToString(data.teamAndCollab.teamMemberIds)
                    it[updatedAt] = now
                }
            }

            4 -> {
                Projects.update({ Projects.id eq finalProjectId }) {
                    it[autoPost] = data.publishing.autoPost
                    it[notifyConnections] = data.publishing.notifyConnections
                    it[updatedAt] = now
                }
            }

            else -> return@transaction "Invalid stage"
        }

        // Re-check if all stages are complete
        val project = Projects.selectAll()
            .where { Projects.id eq finalProjectId }
            .single()

        val isComplete = listOf(
            project[title],
            project[shortDescription],
            project[projectCategory],
            project[tags],
            project[caseStudy],
            project[coverImagePath],
            project[mediaPaths],
            project[projectStatus],
            project[roleId],
            project[teamMemberIds],
            project[autoPost],
            project[notifyConnections]
        ).all { it != null }

        return@transaction if (isComplete) {
            "Project completed and fully uploaded."
        } else {
            "Stage $stage uploaded. Project still in progress."
        }
    }
}
