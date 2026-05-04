package io.goodmidnight.transfer.core.utils

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.DecimalFormat
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.log10
import kotlin.math.pow

@Singleton
class FileResolver @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    /**
     * Copies the content of a Uri to the app's temporary cache folder and returns the absolute path.
     * This is a crucial preprocessing step because the C++ engine (POSIX API) requires a direct file path.
     */
    suspend fun getAbsolutePathFromUri(uri: Uri): String? = withContext(Dispatchers.IO) {
        val contentResolver = context.contentResolver
        val fileName = getFileName(uri) ?: "temp_transfer_${System.currentTimeMillis()}"

        // Create a temporary file in the cache directory
        val tempFile = File(context.cacheDir, fileName)

        try {
            contentResolver.openInputStream(uri)?.use { inputStream ->
                FileOutputStream(tempFile).use { outputStream ->
                    inputStream.copyTo(outputStream)
                }
            }
            tempFile.absolutePath
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Extracts the display name of the file from the given Uri.
     */
    private fun getFileName(uri: Uri): String? {
        var result: String? = null
        if (uri.scheme == "content") {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (index != -1) {
                        result = cursor.getString(index)
                    }
                }
            }
        }
        if (result == null) {
            result = uri.path?.let { File(it).name }
        }
        return result
    }

    /**
     * Calculates the total size of the cache directory and returns a human-readable string (e.g., "1.2 MB").
     */
    suspend fun getCacheSizeFormatted(): String = withContext(Dispatchers.IO) {
        val cacheDir = context.cacheDir
        val sizeInBytes = getFolderSize(cacheDir)
        formatSize(sizeInBytes)
    }

    /**
     * Deletes all temporary files inside the cache directory.
     */
    suspend fun clearAllCacheFiles(): Boolean = withContext(Dispatchers.IO) {
        try {
            val cacheDir = context.cacheDir
            if (cacheDir.exists()) {
                cacheDir.deleteRecursively()
                cacheDir.mkdirs()
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /**
     * Recursively calculates the total size of all files within a directory.
     */
    private fun getFolderSize(file: File): Long {
        var size: Long = 0
        if (file.isDirectory) {
            file.listFiles()?.forEach { child ->
                size += getFolderSize(child)
            }
        } else {
            size = file.length()
        }
        return size
    }

    /**
     * Converts bytes to a human-readable format (e.g., KB, MB, GB).
     */
    private fun formatSize(size: Long): String {
        if (size <= 0) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        val digitGroups = (log10(size.toDouble()) / log10(1024.0)).toInt()

        return DecimalFormat("#,##0.#").format(
            size / 1024.0.pow(digitGroups.toDouble())
        ) + " " + units[digitGroups]
    }
}