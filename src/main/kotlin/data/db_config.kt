package com.example.data

import com.example.data.classes_daos.*
import com.example.data.models.*
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.jetbrains.exposed.sql.Database

object DatabaseFactory {
    fun init() {
        val hikari = HikariConfig().apply {
            jdbcUrl = "jdbc:postgresql://localhost:5432/anop-app-db"
            driverClassName = "org.postgresql.Driver"
            username = "postgres"
            password = "12345678" // Change to your actual password
            maximumPoolSize = 10
            isAutoCommit = false
            transactionIsolation = "TRANSACTION_REPEATABLE_READ"
            validate()
        }


        Database.connect(HikariDataSource(hikari))
        createTableIfNotExists(UserInitials)
        createTableIfNotExists(AuthenticationCodes)
        createTableIfNotExists(LoginSessions)
        createTableIfNotExists(BasicProfile)
        createTableIfNotExists(ProfilePictures)
        createTableIfNotExists(Projects)
        createTableIfNotExists(Followers)
        createTableIfNotExists(InterestCategoryTable)
        createTableIfNotExists(UserInterestsTable)
        createTableIfNotExists(Communities)
        createTableIfNotExists(CommunityMembers)
        createTableIfNotExists(Messages)
        createTableIfNotExists(OnlineStatus)
        createTableIfNotExists(Posts)
        createTableIfNotExists(PostMedia)
        createTableIfNotExists(PostCategoryMatches)
        createTableIfNotExists(PostHashtags)
        createTableIfNotExists(PostLikes)
        createTableIfNotExists(PostComments)
        createTableIfNotExists(PostReposts)
        createTableIfNotExists(PostViews)
        createTableIfNotExists(PostBookmarks)
    }
}
