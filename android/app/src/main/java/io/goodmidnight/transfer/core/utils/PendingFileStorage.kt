package io.goodmidnight.transfer.core.utils

import android.content.Context
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import androidx.core.net.toUri

@Singleton
class PendingFileStorage @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val queueFile = File(context.cacheDir, "pending_shares.json")

    /**
     * Serializes a list of Uris into JSON and saves it to the cache directory.
     */
    suspend fun saveFiles(uris: List<Uri>) = withContext(Dispatchers.IO) {
        try {
            val uriStrings = uris.map { it.toString() }
            val jsonString = Json.encodeToString(uriStrings)
            queueFile.writeText(jsonString)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Reads the JSON file from the cache directory and deserializes it back into a list of Uris.
     * Returns an empty list if the file doesn't exist or an error occurs.
     */
    suspend fun loadFiles(): List<Uri> = withContext(Dispatchers.IO) {
        try {
            if (!queueFile.exists()) return@withContext emptyList()

            val jsonString = queueFile.readText()
            val uriStrings: List<String> = Json.decodeFromString(jsonString)

            uriStrings.map { it.toUri() }
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    /**
     * Deletes the pending shares file from the cache directory.
     */
    suspend fun clearFiles() = withContext(Dispatchers.IO) {
        if (queueFile.exists()) {
            queueFile.delete()
        }
    }
}