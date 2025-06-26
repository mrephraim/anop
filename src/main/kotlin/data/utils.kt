package com.example.data


import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.transactions.transaction
import java.security.SecureRandom
import java.util.*
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

object SecurityUtils {
    private const val SECRET_KEY = "Ephraim7index" // Use a secure key
    private const val SALT = "KNK25" // Random salt value
    private const val ALGORITHM = "AES/CBC/PKCS5Padding"

    // Generate AES key from password & salt
    private fun getAESKey(): SecretKey {
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val spec = PBEKeySpec(SECRET_KEY.toCharArray(), SALT.toByteArray(), 65536, 256)
        return SecretKeySpec(factory.generateSecret(spec).encoded, "AES")
    }

    // Encrypt password
    fun encryptPassword(password: String): String {
        val cipher = Cipher.getInstance(ALGORITHM)
        val key = getAESKey()
        val iv = IvParameterSpec(ByteArray(16)) // Initialization Vector (IV)
        cipher.init(Cipher.ENCRYPT_MODE, key, iv)
        val encrypted = cipher.doFinal(password.toByteArray())
        return Base64.getEncoder().encodeToString(encrypted) // Encode as Base64
    }

    // Decrypt password
    fun decryptPassword(encryptedPassword: String): String {
        val cipher = Cipher.getInstance(ALGORITHM)
        val key = getAESKey()
        val iv = IvParameterSpec(ByteArray(16))
        cipher.init(Cipher.DECRYPT_MODE, key, iv)
        val decodedBytes = Base64.getDecoder().decode(encryptedPassword)
        return String(cipher.doFinal(decodedBytes))
    }
}

object TokenUtils {
    private val secureRandom = SecureRandom()

    fun generateSecureToken(): String {
        val tokenBytes = ByteArray(64)
        secureRandom.nextBytes(tokenBytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(tokenBytes)
    }
}

fun doesTableExist(tableName: String): Boolean {
    return transaction {
        val result = exec("""
            SELECT EXISTS (
                SELECT 1
                FROM information_schema.tables
                WHERE table_name = '$tableName' AND table_schema = 'public'
            )
        """) { resultSet ->
            resultSet.next()
            resultSet.getBoolean(1)
        }
        result ?: false
    }
}
fun createTableIfNotExists(table: Table) {
    transaction {
        val tableNameLowerCase = table.tableName.lowercase() // Convert table name to lowercase
        if (!doesTableExist(tableNameLowerCase)) {
            SchemaUtils.create(table)
            println("$tableNameLowerCase table created.")
        } else {
            println("$tableNameLowerCase table already exists.")
        }
    }
}


