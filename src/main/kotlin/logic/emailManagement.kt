package com.example.logic

import com.typesafe.config.ConfigFactory
import io.ktor.server.config.*
import java.io.File
import jakarta.mail.*
import jakarta.mail.internet.*
import java.util.*



fun loadConfig(): ApplicationConfig {
    return HoconApplicationConfig(ConfigFactory.load()) // Loads application.conf
}

fun loadEmailTemplate(templateName: String, replacements: Map<String, String>): String {
    val resourcePath = "/templates/$templateName.html"
    val stream = object {}.javaClass.getResourceAsStream(resourcePath)
        ?: throw IllegalArgumentException("Template not found: $resourcePath")
    var template = stream.bufferedReader(Charsets.UTF_8).readText()

    replacements.forEach { (key, value) ->
        template = template.replace("{{$key}}", value)
    }

    return template
}


fun getEmailCredentials(): Pair<String, String> {
    val config = loadConfig()
    val email = config.propertyOrNull("ktor.email.address")?.getString() ?: "DEFAULT_EMAIL"
    val emailPassword = config.propertyOrNull("ktor.email.password")?.getString() ?: "DEFAULT_PASSWORD"
    return email to emailPassword
}


fun sendSingleEmail(
    to: String,
    subject: String,
    templateName: String,
    replacements: Map<String, String>,
    serviceEmail: String,
    serviceEmailPassword: String
) {
    val props = Properties().apply {
        put("mail.smtp.host", "mail.abetn.org.ng")
        put("mail.smtp.port", "465")
        put("mail.smtp.auth", "true")
        put("mail.smtp.ssl.enable", "true")
        put("mail.smtp.starttls.enable", "false")
        put("mail.smtp.socketFactory.class", "javax.net.ssl.SSLSocketFactory")
        put("mail.smtp.auth.mechanisms", "LOGIN")
    }

    val session = Session.getInstance(props, object : Authenticator() {
        override fun getPasswordAuthentication(): PasswordAuthentication {
            return PasswordAuthentication(serviceEmail, serviceEmailPassword)
        }
    })

    try {
        val message = MimeMessage(session).apply {
            setFrom(InternetAddress(serviceEmail))
            setRecipients(Message.RecipientType.TO, InternetAddress.parse(to))
            this.subject = subject

            val emailContent = loadEmailTemplate(templateName, replacements)

            setContent(emailContent, "text/html; charset=utf-8")
        }

        Transport.send(message)
        println("Email sent successfully to $to")
    } catch (e: MessagingException) {
        e.printStackTrace()
    }
}
