package org.fossify.phone.helpers

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Credentials as OkHttpCredentials
import org.fossify.phone.database.AppDatabase
import org.fossify.phone.database.dao.CentralCredentialsDao
import org.fossify.phone.extensions.config
import org.fossify.phone.models.CentralCredentials
import java.time.Duration
import java.time.Instant

class AdminSettingsHelper(private val context: Context) {
    private val db by lazy { AppDatabase.getInstance(context) }
    private val credsDao: CentralCredentialsDao by lazy { db.centralCredentialsDao() }

    suspend fun saveCredentials(
        centralUrl: String?,
        username: String,
        password: String
    ): Boolean = withContext(Dispatchers.IO) {
        val normalizedUrl = centralUrl?.takeIf { it.isNotBlank() }
            ?: return@withContext false
        val validationSuccess = validateCredentialsBeforeStorage(normalizedUrl, username, password)
        if (!validationSuccess) {
            return@withContext false
        }

        val credentials = CentralCredentials(
            centralUrl = normalizedUrl,
            projectId = "",
            username = username,
            passwordHash = password,
            validated = true,
            lastValidation = System.currentTimeMillis()
        )

        credsDao.clear()
        credsDao.insert(credentials)
        true
    }

    suspend fun validateCredentialsBeforeStorage(
        centralUrl: String,
        username: String,
        password: String
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            if (centralUrl.isBlank() || username.isBlank() || password.isBlank()) {
                return@withContext false
            }
            val httpClient = OkHttpClient()
            val request = Request.Builder()
                .url("${centralUrl.trimEnd('/')}/v1/users/current")
                .get()
                .addHeader("Authorization", OkHttpCredentials.basic(username, password))
                .build()
            httpClient.newCall(request).execute().use { response ->
                response.isSuccessful
            }
        } catch (_: Exception) {
            false
        }
    }

    suspend fun hasCredentials(): Boolean = withContext(Dispatchers.IO) {
        credsDao.getCredentials() != null
    }

    suspend fun getCredentials(): CentralCredentials? = withContext(Dispatchers.IO) { credsDao.getCredentials() }

    suspend fun getValidatedCredentials(): CentralCredentials? = withContext(Dispatchers.IO) { credsDao.getValidatedCredentials() }

    suspend fun updateValidation(validated: Boolean, lastValidation: Long?) = withContext(Dispatchers.IO) {
        credsDao.updateValidation(validated, lastValidation)
    }

    suspend fun deleteAllCredentials() = withContext(Dispatchers.IO) { credsDao.clear() }

    // Reserved keys management (SharedPreferences)
    fun getReservedKeys(): Set<String> = context.config.reservedKeys

    fun setReservedKeys(keys: Set<String>) {
        context.config.reservedKeys = keys
    }

    fun addReservedKey(key: String) {
        val current = getReservedKeys()
        setReservedKeys(current + key)
    }

    fun removeReservedKey(key: String) {
        val current = getReservedKeys()
        setReservedKeys(current - key)
    }

    fun getReservedKeysWithDefaults(): Set<String> = DEFAULT_RESERVED_KEYS + getReservedKeys()

    fun updateReservedKeys(keys: Set<String>): Boolean {
        val validKeys = keys.filter { isValidReservedKey(it.trim()) }.toSet()
        if (validKeys.isEmpty() && keys.isNotEmpty()) {
            return false
        }

        setReservedKeys(validKeys)
        return true
    }

    fun clearCustomReservedKeys() {
        setReservedKeys(emptySet())
    }

    fun getReservedKeysCount(): Int = getReservedKeys().size

    fun hasReservedKeys(): Boolean = getReservedKeys().isNotEmpty()

    fun isReservedKeyValid(key: String): Boolean = isValidReservedKey(key.trim())

    fun getLogRetentionDays(): Int = context.config.syncLogRetentionDays

    suspend fun updateLogRetentionDays(days: Int): Boolean = withContext(Dispatchers.IO) {
        if (days < 1 || days > 365) {
            return@withContext false
        }
        context.config.syncLogRetentionDays = days
        val cutoff = Instant.now().minus(Duration.ofDays(days.toLong()))
        db.syncLogDao().deleteOlderThan(cutoff)
        true
    }

    private fun isValidReservedKey(key: String): Boolean {
        if (key.isBlank()) return false
        if (key.length > 100) return false
        if (key.any { !it.isLetterOrDigit() && it != '_' && it != '-' }) {
            return false
        }
        return true
    }

    companion object {
        val DEFAULT_RESERVED_KEYS = Config.DEFAULT_RESERVED_KEYS
    }
}
