package com.nadim.bikepart.shop


import com.nadim.bikepart.R
import android.system.Os
import android.system.OsConstants
import java.io.File
import java.io.InputStream
import java.io.RandomAccessFile
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

data class PhotoReference(val sha256: String, val sizeBytes: Long) {
    init {
        require(sha256.matches(Regex("[0-9a-f]{64}")))
        require(sizeBytes in 1..MAX_PHOTO_BYTES)
    }
}

sealed interface PhotoChange {
    data object Keep : PhotoChange
    data object Remove : PhotoChange
    class Import(val open: () -> InputStream) : PhotoChange
}

internal const val MAX_PHOTO_BYTES = 20L * 1024 * 1024

/** Publishes complete, synced files without replacing an existing content identity.
 * Unreferenced blobs are retained: deletion needs the future backup pin registry.
 */
internal class ProductPhotos(private val directory: File, private val context: android.content.Context) {
    suspend fun import(source: PhotoChange.Import): PhotoReference = withContext(Dispatchers.IO) {
        check(directory.isDirectory || directory.mkdirs()) { context.getString(R.string.could_not_create_photo_storage) }
        val temporary = File.createTempFile("import-", ".tmp", directory)
        try {
            val digest = MessageDigest.getInstance("SHA-256")
            var size = 0L
            source.open().use { input ->
                temporary.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val count = input.read(buffer)
                        if (count < 0) break
                        size += count
                        if (size > MAX_PHOTO_BYTES) throw ShopValidation(ProductField.Photo, context.getString(R.string.choose_a_photo_no_larger_than))
                        digest.update(buffer, 0, count)
                        output.write(buffer, 0, count)
                    }
                    if (size == 0L) throw ShopValidation(ProductField.Photo, context.getString(R.string.the_selected_photo_is_empty))
                    output.fd.sync()
                }
            }
            val reference = PhotoReference(digest.digest().joinToString("") { "%02x".format(it) }, size)
            val target = File(directory, reference.sha256)
            // Serialize publication across Shop instances and processes. Android SELinux
            // disallows hard links in app data; rename is atomic within this directory.
            synchronized(publicationLock) {
                RandomAccessFile(File(directory, ".publication.lock"), "rw").use { lockFile ->
                    lockFile.channel.lock().use {
                        if (!target.exists()) Os.rename(temporary.path, target.path)
                    }
                }
            }
            check(read(reference) != null) { context.getString(R.string.stored_photo_failed_verification) }
            // Persist the directory entries too, including newly created ancestors.
            for (folder in listOf(directory, directory.parentFile, directory.parentFile?.parentFile).filterNotNull()) {
                val descriptor = Os.open(folder.path, OsConstants.O_RDONLY, 0)
                try { Os.fsync(descriptor) } finally { Os.close(descriptor) }
            }
            reference
        } finally { temporary.delete() }
    }

    suspend fun read(reference: PhotoReference): ByteArray? = withContext(Dispatchers.IO) {
        val file = File(directory, reference.sha256)
        if (!file.isFile || file.length() != reference.sizeBytes) return@withContext null
        val bytes = file.inputStream().use { it.readBytes() }
        val hash = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
        bytes.takeIf { it.size.toLong() == reference.sizeBytes && hash == reference.sha256 }
    }

    private companion object { val publicationLock = Any() }
}
