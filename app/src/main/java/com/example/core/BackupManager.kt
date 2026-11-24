package com.example.core

import android.content.Context
import android.net.Uri
import com.example.data.db.StudoraDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

data class BackupSummary(
    val exportedBytes: Long,
    val filesCount: Int,
    val createdAt: Long
)

class BackupManager(
    private val context: Context,
    private val db: StudoraDatabase,
    private val storage: LocalStorageManager
) {
    companion object {
        private const val MANIFEST_ENTRY = "studora_backup_manifest.json"
        private const val DB_DIR_ENTRY = "database/"
        private const val FILES_DIR_ENTRY = "studora_data/"
    }

    suspend fun exportBackupToUri(destUri: Uri): Result<BackupSummary> = withContext(Dispatchers.IO) {
        try {
            db.checkpoint()
            val dbFile = context.getDatabasePath(StudoraDatabase.DB_NAME)
            if (!dbFile.exists()) {
                return@withContext Result.failure(IOException("Local SQLite database not found."))
            }

            var count = 0
            val pfd = context.contentResolver.openOutputStream(destUri)
                ?: return@withContext Result.failure(IOException("Unable to open backup destination."))

            pfd.use { rawOut ->
                ZipOutputStream(BufferedOutputStream(rawOut)).use { zos ->
                    // 1. Write manifest
                    val manifest = JSONObject().apply {
                        put("app", "STUDORA")
                        put("version", 1)
                        put("createdAt", System.currentTimeMillis())
                        put("dbName", StudoraDatabase.DB_NAME)
                    }
                    zos.putNextEntry(ZipEntry(MANIFEST_ENTRY))
                    zos.write(manifest.toString(2).toByteArray())
                    zos.closeEntry()

                    // 2. Write SQLite DB files
                    listOf(
                        dbFile,
                        File(dbFile.path + "-wal"),
                        File(dbFile.path + "-shm")
                    ).filter { it.exists() && it.length() > 0L }.forEach { f ->
                        zos.putNextEntry(ZipEntry("$DB_DIR_ENTRY${f.name}"))
                        FileInputStream(f).use { it.copyTo(zos) }
                        zos.closeEntry()
                        count++
                    }

                    // 3. Write persistent studora_data files
                    val rootPath = storage.rootDir.canonicalPath
                    storage.rootDir.walkTopDown()
                        .filter { it.isFile }
                        .forEach { file ->
                            val rel = file.canonicalPath.removePrefix(rootPath).trimStart(File.separatorChar)
                            zos.putNextEntry(ZipEntry("$FILES_DIR_ENTRY$rel"))
                            FileInputStream(file).use { it.copyTo(zos) }
                            zos.closeEntry()
                            count++
                        }
                }
            }

            val size = try {
                context.contentResolver.openAssetFileDescriptor(destUri, "r")?.use { it.length } ?: 0L
            } catch (_: Exception) {
                0L
            }
            Result.success(BackupSummary(exportedBytes = size, filesCount = count, createdAt = System.currentTimeMillis()))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun validateBackupUri(srcUri: Uri): Result<JSONObject> = withContext(Dispatchers.IO) {
        try {
            val stream = context.contentResolver.openInputStream(srcUri)
                ?: return@withContext Result.failure(IOException("Cannot open selected backup file."))
            var manifestJson: JSONObject? = null
            stream.use { input ->
                ZipInputStream(BufferedInputStream(input)).use { zis ->
                    var entry: ZipEntry? = zis.nextEntry
                    while (entry != null) {
                        if (entry.name == MANIFEST_ENTRY) {
                            val content = zis.readBytes().toString(Charsets.UTF_8)
                            manifestJson = JSONObject(content)
                            break
                        }
                        zis.closeEntry()
                        entry = zis.nextEntry
                    }
                }
            }
            val parsed = manifestJson
                ?: return@withContext Result.failure(IOException("Invalid backup archive: missing STUDORA manifest."))
            if (parsed.optString("app") != "STUDORA") {
                return@withContext Result.failure(IOException("Selected file is not a valid STUDORA backup."))
            }
            Result.success(parsed)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun restoreBackupFromUri(srcUri: Uri): Result<Int> = withContext(Dispatchers.IO) {
        val stagingDir = File(context.cacheDir, "restore_staging_${System.currentTimeMillis()}")
        try {
            val valid = validateBackupUri(srcUri)
            if (valid.isFailure) return@withContext Result.failure(valid.exceptionOrNull()!!)

            stagingDir.mkdirs()
            val stagingCanonical = stagingDir.canonicalPath
            var restoredFiles = 0

            val stream = context.contentResolver.openInputStream(srcUri)
                ?: return@withContext Result.failure(IOException("Unable to read backup file."))

            stream.use { input ->
                ZipInputStream(BufferedInputStream(input)).use { zis ->
                    var entry: ZipEntry? = zis.nextEntry
                    while (entry != null) {
                        if (!entry.isDirectory) {
                            val outFile = File(stagingDir, entry.name)
                            if (!outFile.canonicalPath.startsWith(stagingCanonical + File.separator)) {
                                throw SecurityException("Unsafe path in backup zip: ${entry.name}")
                            }
                            outFile.parentFile?.mkdirs()
                            FileOutputStream(outFile).use { fos ->
                                zis.copyTo(fos)
                            }
                            restoredFiles++
                        }
                        zis.closeEntry()
                        entry = zis.nextEntry
                    }
                }
            }

            val stagedDb = File(stagingDir, "$DB_DIR_ENTRY${StudoraDatabase.DB_NAME}")
            if (!stagedDb.exists() || stagedDb.length() == 0L) {
                return@withContext Result.failure(IOException("Backup archive does not contain a valid SQLite database."))
            }

            // Close Room instance cleanly before swapping DB files
            StudoraDatabase.closeAndResetInstance()

            val targetDb = context.getDatabasePath(StudoraDatabase.DB_NAME)
            targetDb.parentFile?.mkdirs()
            File(targetDb.path + "-wal").delete()
            File(targetDb.path + "-shm").delete()

            stagedDb.copyTo(targetDb, overwrite = true)
            val stagedWal = File(stagingDir, "$DB_DIR_ENTRY${StudoraDatabase.DB_NAME}-wal")
            if (stagedWal.exists()) stagedWal.copyTo(File(targetDb.path + "-wal"), overwrite = true)
            val stagedShm = File(stagingDir, "$DB_DIR_ENTRY${StudoraDatabase.DB_NAME}-shm")
            if (stagedShm.exists()) stagedShm.copyTo(File(targetDb.path + "-shm"), overwrite = true)

            val stagedFilesDir = File(stagingDir, FILES_DIR_ENTRY.trimEnd('/'))
            if (stagedFilesDir.exists()) {
                storage.rootDir.deleteRecursively()
                storage.rootDir.mkdirs()
                stagedFilesDir.copyRecursively(storage.rootDir, overwrite = true)
            }

            Result.success(restoredFiles)
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            stagingDir.deleteRecursively()
        }
    }
}
