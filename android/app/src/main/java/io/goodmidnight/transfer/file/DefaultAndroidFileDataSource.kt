package io.goodmidnight.transfer.file

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import io.goodmidnight.transfer.data.datasource.AndroidFileDataSource
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Concrete implementation of AndroidFileDataSource.
 * It manages physical file creation and provides raw UNIX File Descriptors (FDs)
 * to the C++ Native layer, abstracting away Android's Scoped Storage complexities.
 */
@Singleton
class DefaultAndroidFileDataSource @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : AndroidFileDataSource {

    /**
     * Creates or opens a hidden metadata file used for tracking transfer progress (Resume feature).
     *
     * @param fileName The name of the meta file (e.g., "video.mp4.meta").
     * @return A raw File Descriptor (Int) for C++ mmap, or -1 if creation fails.
     */
    override fun createMetaFileDescriptor(fileName: String): Int {
        return try {
            // Save metadata files in the app's internal cache directory to keep them hidden from the user.
            val metaFile = File(context.cacheDir, fileName)
            if (!metaFile.exists()) metaFile.createNewFile()

            // Open the file in Read/Write mode to allow the C++ layer to update progress.
            val parcelFd = ParcelFileDescriptor.open(metaFile, ParcelFileDescriptor.MODE_READ_WRITE)

            // detachFd() relinquishes the ownership of the FD from the JVM to the Native C++ layer.
            // The C++ layer is now responsible for closing this descriptor.
            parcelFd.detachFd()
        } catch (e: Exception) {
            Log.w("DefaultAndroidFileDataSource", "createMetaFileDescriptor: $e")
            -1
        }
    }

    /**
     * Creates or opens the actual destination file in the public device storage.
     *
     * @param fileName The actual name of the file being transferred.
     * @param relativePath The sub-directory path inside the public Downloads folder.
     * @return A raw File Descriptor (Int) for C++ mmap, or -1 if creation fails.
     */
    override fun createTransferFileDescriptor(fileName: String, relativePath: String): Int {
        return try {
            val resolver = context.contentResolver

            // Android 10 (API 29) and above - Scoped Storage enforcement
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val contentValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                    // Treat all incoming files as generic binary streams to bypass strict MediaStore categorization
                    put(MediaStore.MediaColumns.MIME_TYPE, "application/octet-stream")
                    // Define the exact save location (e.g., "Downloads/MyTransfer")
                    put(
                        MediaStore.MediaColumns.RELATIVE_PATH,
                        "${Environment.DIRECTORY_DOWNLOADS}/$relativePath"
                    )
                }

                // Insert a new empty record into MediaStore to obtain a valid Content URI
                val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
                if (uri != null) {
                    // Open the URI in Read/Write mode ("rw") to get the ParcelFileDescriptor
                    val parcelFd = resolver.openFileDescriptor(uri, "rw")
                    return parcelFd?.detachFd() ?: -1
                }
            } else {
                // Android 9 (API 28) and below - Legacy direct file path access
                val downloadDir = File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                    relativePath
                )

                // Ensure the target directory structure exists
                if (!downloadDir.exists()) downloadDir.mkdirs()

                val newFile = File(downloadDir, fileName)
                if (!newFile.exists()) newFile.createNewFile()

                val parcelFd =
                    ParcelFileDescriptor.open(newFile, ParcelFileDescriptor.MODE_READ_WRITE)
                return parcelFd.detachFd()
            }
            -1 // Return -1 if both storage methods fail
        } catch (e: Exception) {
            Log.w("DefaultAndroidFileDataSource", "createTransferFileDescriptor: $e")
            -1
        }
    }
}