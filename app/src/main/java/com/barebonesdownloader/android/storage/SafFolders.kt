package com.barebonesdownloader.android.storage

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract

object SafFolders {
    fun name(context: Context, uri: Uri): String = runCatching {
        val doc = DocumentsContract.buildDocumentUriUsingTree(uri, DocumentsContract.getTreeDocumentId(uri))
        context.contentResolver.query(doc, arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME), null, null, null)?.use {
            if (it.moveToFirst()) it.getString(0) else null
        }
    }.getOrNull() ?: uri.lastPathSegment?.substringAfterLast(':') ?: "Selected folder"
    fun writable(context: Context, value: String): Boolean = runCatching {
        val uri = Uri.parse(value)
        if (!context.contentResolver.persistedUriPermissions.any { it.uri == uri && it.isWritePermission }) return false
        val doc = DocumentsContract.buildDocumentUriUsingTree(uri, DocumentsContract.getTreeDocumentId(uri))
        context.contentResolver.query(doc, arrayOf(DocumentsContract.Document.COLUMN_FLAGS), null, null, null)?.use {
            it.moveToFirst() && it.getInt(0) and DocumentsContract.Document.FLAG_DIR_SUPPORTS_CREATE != 0
        } ?: false
    }.getOrDefault(false)
}
