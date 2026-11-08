package com.barebones.downloader.ui.home

import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.painterResource
import com.barebones.downloader.R
import com.barebones.downloader.domain.model.DownloadItem
import com.barebones.downloader.ui.components.DownloadCard

@Composable fun HomeScreen(url: String, onUrl: (String) -> Unit, ready: Boolean,
    onAdd: () -> Unit, active: DownloadItem?) {
    val context = LocalContext.current
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)) {
        OutlinedTextField(value = url, onValueChange = onUrl, label = { Text("Media URL") },
            placeholder = { Text("Paste a YouTube, TikTok or other link") }, singleLine = true,
            trailingIcon = { IconButton(onClick = {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(context)?.let { onUrl(it.toString()) }
            }) { Icon(painterResource(R.drawable.ic_paste), contentDescription = "Paste URL") } }, modifier = Modifier.fillMaxWidth().testTag("media_url"))
        Button(onClick = onAdd, enabled = ready, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Add to queue") }
        Text("Supports sites available through yt-dlp; some links may require access or may stop working.", style = MaterialTheme.typography.bodySmall)
        active?.let { DownloadCard(it) }
    }
}
