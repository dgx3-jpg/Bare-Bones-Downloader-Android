package com.barebonesdownloader.android.storage

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.barebonesdownloader.android.domain.model.DownloadItem
import com.barebonesdownloader.android.media.StagedMedia
import com.barebonesdownloader.android.media.SizeBudget
import com.barebonesdownloader.android.domain.model.DownloadKind
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

class FolderUnavailable : IllegalStateException("The selected folder is unavailable or its write permission was revoked. Choose replacement…")
class DestinationExists : IllegalStateException("A file with this name already exists. Existing media was left unchanged.")
interface MediaStorage {
    fun writable(uri: String?): Boolean
    fun completed(id: java.util.UUID) {}
    fun discard(id: java.util.UUID, uri: String): Boolean = false
    suspend fun publish(item: DownloadItem, media: StagedMedia, checkCancelled: () -> Unit): String
}
class SafMediaStorage(private val context: Context) : MediaStorage {
    private val journal = PublicationJournal(context)
    override fun completed(id: java.util.UUID) { journal.finished(id) }
    override fun discard(id: java.util.UUID, uri: String) = journal.discard(id, uri)
    override fun writable(uri: String?) = uri != null && SafFolders.writable(context, uri)
    override suspend fun publish(item: DownloadItem, media: StagedMedia, checkCancelled: () -> Unit): String = withContext(Dispatchers.IO) {
        if (!writable(item.destinationUri)) throw FolderUnavailable()
        check(journal.clearPending(item.id)) { "An earlier incomplete publication could not be removed. Restore folder access or remove it manually before retrying." }
        val cap = SizeBudget.bytes(if (item.kind == DownloadKind.AUDIO) item.settingsSnapshot.audioMaxSizeMb else item.settingsSnapshot.videoMaxSizeMb)
        require(SizeBudget.fits(media.file.length(), cap)) { "Final media exceeds the strict size cap; nothing was published." }
        val tree = DocumentFile.fromTreeUri(context, Uri.parse(item.destinationUri)) ?: throw FolderUnavailable()
        val name = FilenamePolicy.name(item, media.title, media.file.extension)
        if (tree.findFile(name) != null) throw DestinationExists()
        checkCancelled(); currentCoroutineContext().ensureActive()
        // SAF providers do not all offer atomic moves or exclusive rename. Use collision-safe
        // create + verified copy, and delete the newly-created document on every failure.
        val document = tree.createFile(media.mime, name) ?: throw FolderUnavailable()
        var recorded = false
        try {
            if (document.name != name) throw DestinationExists()
            journal.record(item.id, document.uri.toString()); recorded = true
            val sourceHash = MessageDigest.getInstance("SHA-256")
            context.contentResolver.openOutputStream(document.uri, "wt")?.use { output ->
                media.file.inputStream().use { input ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        checkCancelled(); currentCoroutineContext().ensureActive()
                        val count = input.read(buffer); if (count < 0) break
                        sourceHash.update(buffer, 0, count)
                        output.write(buffer, 0, count)
                    }
                    output.flush()
                }
            } ?: throw FolderUnavailable()
            checkCancelled(); currentCoroutineContext().ensureActive()
            var bytes = 0L
            val destinationHash = MessageDigest.getInstance("SHA-256")
            context.contentResolver.openInputStream(document.uri)?.use { input ->
                val buffer = ByteArray(64 * 1024)
                while (true) { checkCancelled(); currentCoroutineContext().ensureActive(); val n = input.read(buffer); if (n < 0) break; bytes += n; destinationHash.update(buffer, 0, n) }
            } ?: error("Could not verify the saved document.")
            require(bytes > 0 && bytes == media.file.length() && sourceHash.digest().contentEquals(destinationHash.digest())) { "The saved copy is incomplete. It was removed." }
            checkCancelled(); document.uri.toString()
        } catch (t: Throwable) {
            if (!document.delete()) throw IllegalStateException("Saving failed and the incomplete document could not be removed. Remove it from the selected folder before retrying.", t)
            if (recorded) journal.finished(item.id)
            throw t
        }
    }
}
