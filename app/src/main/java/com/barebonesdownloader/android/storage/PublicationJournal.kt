package com.barebonesdownloader.android.storage

import android.content.Context
import android.net.Uri
import android.util.AtomicFile
import android.provider.DocumentsContract
import java.io.FileNotFoundException
import com.barebonesdownloader.android.data.repository.RoomDownloadRepository
import com.barebonesdownloader.android.domain.model.DownloadStatus
import com.barebonesdownloader.android.download.DownloadLog
import java.io.File
import java.util.UUID

/** Records only newly created app publications; never scans/deletes arbitrary destination files. */
class PublicationJournal(private val context: Context, private val deleteOwned: (Uri) -> Boolean = { DocumentsContract.deleteDocument(context.contentResolver,it) }) {
    private val root get() = File(context.noBackupFilesDir,"pending-publications").also { it.mkdirs() }
    fun clearPending(id: UUID): Boolean {
        val file=File(root,"$id.uri")
        if (!file.exists() && !File(root,"$id.uri.bak").exists()) return true
        val uri=AtomicFile(file).readFully().toString(Charsets.UTF_8)
        return discard(id,uri)
    }
    fun record(id: UUID, uri: String) {
        val file = AtomicFile(File(root,"$id.uri")); val output = file.startWrite()
        try { output.write(uri.toByteArray(Charsets.UTF_8)); file.finishWrite(output) }
        catch (t: Throwable) { file.failWrite(output); throw t }
    }
    fun finished(id: UUID) = AtomicFile(File(root,"$id.uri")).delete()
    fun discard(id: UUID, uri: String): Boolean {
        val marker = File(root,"$id.uri")
        if ((!marker.isFile && !File(root,"$id.uri.bak").isFile) || AtomicFile(marker).readFully().toString(Charsets.UTF_8) != uri) return false
        // exists() swallows permission/provider failures. Never discard the recovery
        // marker merely because that convenience query reports false.
        val removed = try { deleteOwned(Uri.parse(uri)) } catch (_: FileNotFoundException) { true }
        if (removed) finished(id)
        return removed
    }
    suspend fun recover(repository: RoomDownloadRepository, log: DownloadLog) {
        root.listFiles().orEmpty().map { File(root,it.name.removeSuffix(".bak")) }.distinct().filter { it.name.endsWith(".uri") }.forEach { file ->
            val id = runCatching { UUID.fromString(file.name.removeSuffix(".uri")) }.getOrNull() ?: return@forEach
            try {
                val uri = AtomicFile(file).readFully().toString(Charsets.UTF_8)
                require(Uri.parse(uri).scheme == "content")
                val item = repository.get(id)
                if (item?.status == DownloadStatus.COMPLETED && item.outputUri == uri) finished(id)
                else if (discard(id,uri)) log.add("Removed an interrupted publication; retained private staging for explicit Retry.")
                else log.add("Recovery warning: an interrupted document could not be removed. Restore folder permission or remove it manually before retrying.")
            } catch (e: Exception) { log.add("Recovery warning: incomplete publication cleanup needs folder access (${e.javaClass.simpleName}: ${e.message.orEmpty().take(500)}).") }
        }
    }
}
