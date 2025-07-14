package com.example.application

import com.example.logic.*
import io.ktor.server.application.*
import io.ktor.server.routing.*

/* fun Application.configureRouting() {

} */

fun Application.internalRoute(){
    routing {
        userAuth()
        loginRoute()
        passwordResetRequestRoute()
        basicProfile()
        projectRoutes()
        followRoutes()
        interestsRoutes()
        communityRoutes()
        chatWebSocketRoute()
        postRoutes()
        reactionWebSocketRoute()
    }
}