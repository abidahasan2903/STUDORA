package com.example.core

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.StatFs
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.UUID
import java.util.concurrent.TimeUnit

data class StorageStats(
    val databaseBytes: Long,
    val documentsBytes: Long,
    val imagesBytes: Long,
    val audioBytes: Long,
    val otherBytes: Long,
    val totalAppBytes: Long,
    val availableDeviceBytes: Long
)

class LocalStorageManager(private val context: Context) {

    val rootDir: File by lazy {
        File(context.filesDir, "studora_data").apply { mkdirs() }
    }

    val documentsDir: File by lazy { File(rootDir, "documents").apply { mkdirs() } }
    val scansDir: File by lazy { File(rootDir, "scans").apply { mkdirs() } }
    val audioRecordingsDir: File by lazy { File(rootDir, "audio_recordings").apply { mkdirs() } }
    val aiAudioDir: File by lazy { File(rootDir, "ai_audio").apply { mkdirs() } }
    val tasksDir: File by lazy { File(rootDir, "tasks").apply { mkdirs() } }
    val routinesDir: File by lazy { File(rootDir, "routines").apply { mkdirs() } }
    val busDir: File by lazy { File(rootDir, "bus").apply { mkdirs() } }
    val nutritionDir: File by lazy { File(rootDir, "nutrition").apply { mkdirs() } }
    val faviconsDir: File by lazy { File(rootDir, "favicons").apply { mkdirs() } }
    val tempDir: File by lazy { File(context.cacheDir, "studora_temp").apply { mkdirs() } }

    private val httpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(8, TimeUnit.SECONDS)
            .build()
    }

    fun availableSpaceBytes(): Long {
        return try {
            val stat = StatFs(context.filesDir.absolutePath)
            stat.availableBytes
        } catch (_: Exception) {
            0L
        }
    }

    fun hasEnoughSpace(requiredBytes: Long = 5L * 1024L * 1024L): Boolean {
        val available = availableSpaceBytes()
        return available == 0L || available > requiredBytes
    }

    fun getDisplayNameFromUri(uri: Uri): String {
        var name: String? = null
        if (uri.scheme == "content") {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (idx >= 0) name = cursor.getString(idx)
                }
            }
        }
        if (name.isNullOrBlank()) {
            name = uri.lastPathSegment?.substringAfterLast('/')
        }
        return name?.ifBlank { "document_${System.currentTimeMillis()}" }
            ?: "document_${System.currentTimeMillis()}"
    }

    fun getMimeType(uri: Uri): String {
        return context.contentResolver.getType(uri)
            ?: MimeTypeMap.getFileExtensionFromUrl(uri.toString())
                ?.let { MimeTypeMap.getSingleton().getMimeTypeFromExtension(it.lowercase()) }
            ?: "application/octet-stream"
    }

    suspend fun copyUriToDirectory(
        uri: Uri,
        targetDir: File,
        preferredExtension: String? = null
    ): Result<File> = withContext(Dispatchers.IO) {
        try {
            if (!hasEnoughSpace(2L * 1024L * 1024L)) {
                return@withContext Result.failure(IOException("Insufficient device storage space."))
            }
            targetDir.mkdirs()
            val originalName = getDisplayNameFromUri(uri)
            val ext = preferredExtension
                ?: originalName.substringAfterLast('.', "").takeIf { it.isNotBlank() }
                ?: MimeTypeMap.getSingleton().getExtensionFromMimeType(getMimeType(uri))
                ?: "bin"
            val safeBase = originalName.substringBeforeLast('.')
                .replace(Regex("[^a-zA-Z0-9._-]"), "_")
                .take(40)
                .ifBlank { "file" }
            val destFile = File(targetDir, "${safeBase}_${UUID.randomUUID().toString().take(8)}.$ext")

            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(destFile).use { output ->
                    val buffer = ByteArray(16 * 1024)
                    var bytesRead: Int
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                    }
                    output.flush()
                }
            } ?: return@withContext Result.failure(IOException("Unable to read selected file."))

            if (!destFile.exists() || destFile.length() == 0L) {
                destFile.delete()
                return@withContext Result.failure(IOException("Imported file was empty or failed to save."))
            }
            Result.success(destFile)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun saveBitmapToFile(
        bitmap: Bitmap,
        targetDir: File,
        prefix: String = "img",
        quality: Int = 92
    ): Result<File> = withContext(Dispatchers.IO) {
        try {
            if (!hasEnoughSpace(2L * 1024L * 1024L)) {
                return@withContext Result.failure(IOException("Insufficient device storage space."))
            }
            targetDir.mkdirs()
            val file = File(targetDir, "${prefix}_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(6)}.jpg")
            FileOutputStream(file).use { out ->
                if (!bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out)) {
                    return@withContext Result.failure(IOException("Failed to encode image."))
                }
                out.flush()
            }
            if (!file.exists() || file.length() == 0L) {
                file.delete()
                return@withContext Result.failure(IOException("Image file could not be written."))
            }
            Result.success(file)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun createNewFile(targetDir: File, prefix: String, extension: String): File {
        targetDir.mkdirs()
        return File(targetDir, "${prefix}_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(6)}.$extension")
    }

    fun deleteFileSafely(path: String?): Boolean {
        if (path.isNullOrBlank()) return false
        return try {
            val file = File(path)
            if (file.exists() && file.canonicalPath.startsWith(context.filesDir.canonicalPath)) {
                file.delete()
            } else {
                false
            }
        } catch (_: Exception) {
            false
        }
    }

    fun cleanupTempFiles() {
        try {
            tempDir.listFiles()?.forEach { it.deleteRecursively() }
        } catch (_: Exception) {
        }
    }

    fun getFileProviderUri(file: File): Uri {
        return FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
    }

    fun shareFile(file: File, mimeType: String, title: String): Result<Unit> {
        return try {
            if (!file.exists()) return Result.failure(IOException("File not found on device."))
            val uri = getFileProviderUri(file)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = mimeType
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            val chooser = Intent.createChooser(intent, title).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(chooser)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun shareText(text: String, title: String) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
        val chooser = Intent.createChooser(intent, title).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(chooser)
    }

    fun openFileExternally(file: File, mimeType: String): Result<Unit> {
        return try {
            if (!file.exists()) return Result.failure(IOException("File not found on device."))
            val uri = getFileProviderUri(file)
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, mimeType)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun downloadAndCacheFavicon(websiteUrl: String): String? = withContext(Dispatchers.IO) {
        try {
            val host = Uri.parse(websiteUrl).host?.takeIf { it.isNotBlank() } ?: return@withContext null
            val iconFile = File(faviconsDir, "${host.replace(Regex("[^a-zA-Z0-9._-]"), "_")}.png")
            if (iconFile.exists() && iconFile.length() > 0L) {
                return@withContext iconFile.absolutePath
            }
            val faviconUrl = "https://www.google.com/s2/favicons?domain=$host&sz=128"
            val request = Request.Builder().url(faviconUrl).build()
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                val bytes = response.body?.bytes() ?: return@withContext null
                if (bytes.isEmpty()) return@withContext null
                FileOutputStream(iconFile).use { it.write(bytes) }
                iconFile.absolutePath
            }
        } catch (_: Exception) {
            null
        }
    }

    suspend fun computeStorageStats(dbName: String): StorageStats = withContext(Dispatchers.IO) {
        val dbFile = context.getDatabasePath(dbName)
        val dbWal = File(dbFile.path + "-wal")
        val dbShm = File(dbFile.path + "-shm")
        val dbBytes = (if (dbFile.exists()) dbFile.length() else 0L) +
            (if (dbWal.exists()) dbWal.length() else 0L) +
            (if (dbShm.exists()) dbShm.length() else 0L)

        val docsBytes = dirSize(documentsDir)
        val imagesBytes = dirSize(scansDir) + dirSize(tasksDir) + dirSize(routinesDir) +
            dirSize(busDir) + dirSize(nutritionDir) + dirSize(faviconsDir)
        val audioBytes = dirSize(audioRecordingsDir) + dirSize(aiAudioDir)
        val totalApp = dbBytes + docsBytes + imagesBytes + audioBytes

        StorageStats(
            databaseBytes = dbBytes,
            documentsBytes = docsBytes,
            imagesBytes = imagesBytes,
            audioBytes = audioBytes,
            otherBytes = dirSize(tempDir),
            totalAppBytes = totalApp,
            availableDeviceBytes = availableSpaceBytes()
        )
    }

    private fun dirSize(dir: File): Long {
        if (!dir.exists()) return 0L
        var sum = 0L
        dir.walkTopDown().forEach { if (it.isFile) sum += it.length() }
        return sum
    }
}

fun formatFileSize(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val kb = bytes / 1024.0
    if (kb < 1024) return String.format("%.1f KB", kb)
    val mb = kb / 1024.0
    if (mb < 1024) return String.format("%.1f MB", mb)
    val gb = mb / 1024.0
    return String.format("%.2f GB", gb)
}
