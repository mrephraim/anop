package com.example.data

import com.example.data.classes_daos.*
import com.example.data.models.*
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.jetbrains.exposed.sql.Database

object DatabaseFactory {
    fun init() {
//        val hikari = HikariConfig().apply {
//            jdbcUrl = "jdbc:postgresql://dpg-d1efn6ali9vc73a0o7n0-a:5432/anop_test_db"
//            driverClassName = "org.postgresql.Driver"
//            username = "anop_test_db_user"
//            password = "UvdFhhvddlbEDPLwdm8yAwHjcfq4U57Q"
//            maximumPoolSize = 10
//            isAutoCommit = false
//            transactionIsolation = "TRANSACTION_REPEATABLE_READ"
//            validate()
//        }

        val hikari = HikariConfig().apply {
            jdbcUrl = System.getenv("DB_URL")
            driverClassName = "org.postgresql.Driver"
            username = System.getenv("DB_USER")
            password = System.getenv("DB_PASSWORD")
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
        createTableIfNotExists(PostReposts)
        createTableIfNotExists(PostViews)
        createTableIfNotExists(PostBookmarks)
        createTableIfNotExists(Notifications)
        createTableIfNotExists(CommunityReports)
    }
}
