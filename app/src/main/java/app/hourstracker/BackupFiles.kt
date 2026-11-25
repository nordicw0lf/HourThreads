package app.hourstracker

import android.content.ContentResolver
import android.net.Uri
import java.io.ByteArrayOutputStream
import java.nio.charset.CharacterCodingException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal class BackupFiles(private val resolver: ContentResolver) {
    suspend fun write(uri: Uri, backup: HourThreadBackup) = withContext(Dispatchers.IO) {
        val bytes = BackupCodec.encode(backup).encodeToByteArray()
        if (bytes.size > MAX_BYTES) throw BackupTooLarge()
        checkNotNull(resolver.openOutputStream(uri, "wt")).use { it.write(bytes) }
    }

    suspend fun read(uri: Uri): HourThreadBackup = withContext(Dispatchers.IO) {
        val bytes = checkNotNull(resolver.openInputStream(uri)).use { source ->
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            var count = source.read(buffer)
            while (count != -1) {
                if (count > MAX_BYTES - output.size()) throw BackupTooLarge()
                output.write(buffer, 0, count)
                count = source.read(buffer)
            }
            output.toByteArray()
        }
        val contents = try {
            bytes.decodeToString(throwOnInvalidSequence = true)
        } catch (_: CharacterCodingException) {
            throw InvalidBackup()
        }
        BackupCodec.decode(contents)
    }

    private companion object {
        const val MAX_BYTES = 32 * 1024 * 1024
    }
}

internal class BackupTooLarge : Exception()
