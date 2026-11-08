package com.barebonesdownloader.android.download

import android.app.*
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.os.PowerManager
import java.util.concurrent.TimeUnit
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.barebonesdownloader.android.DownloaderApplication
import com.barebonesdownloader.android.MainActivity
import com.barebonesdownloader.android.R
import com.barebonesdownloader.android.domain.model.*
import kotlinx.coroutines.*

class DownloadForegroundService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate + CoroutineExceptionHandler { _, e ->
        app.diagnostics.add("Download service stopped: ${sanitizeDiagnostic(e.message.orEmpty())}"); stopForeground(STOP_FOREGROUND_REMOVE); stopSelf()
    })
    private val app get() = application as DownloaderApplication
    private var worker: Job? = null
    private var latestStartId = 0
    private val wakeLock by lazy {
        getSystemService(PowerManager::class.java).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "bbd:active-download").apply { setReferenceCounted(false) }
    }
    override fun onCreate() {
        super.onCreate()
        getSystemService(NotificationManager::class.java).createNotificationChannel(NotificationChannel(CHANNEL, "Active downloads", NotificationManager.IMPORTANCE_LOW))
    }
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        latestStartId = startId
        if (intent?.action == CANCEL) { scope.launch { app.downloads.cancelCurrent(); if (worker?.isActive != true) stopSelfResult(startId) }; return START_NOT_STICKY }
        ServiceCompat.startForeground(this, ID, notification(null), ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        wakeLock.acquire(TimeUnit.HOURS.toMillis(6))
        if (worker?.isActive != true) worker = scope.launch {
            app.ready.await()
            val updates = launch {
                while (isActive) {
                    app.coordinator.flushProgress()
                    val current = app.downloads.items.value.firstOrNull { it.status in activeStatuses }
                    getSystemService(NotificationManager::class.java).notify(ID, notification(current))
                    delay(500)
                }
            }
            try {
                do {
                    val consumed = latestStartId
                    withContext(Dispatchers.IO) { app.coordinator.runQueue() }
                    if (consumed == latestStartId) {
                        updates.cancel(); worker = null; stopForeground(STOP_FOREGROUND_REMOVE); stopSelfResult(consumed); break
                    }
                } while (isActive)
            } finally { updates.cancel() }
        }
        return START_NOT_STICKY
    }
    private fun notification(item: DownloadItem?): Notification {
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val cancel = PendingIntent.getService(this, 1, Intent(this, DownloadForegroundService::class.java).setAction(CANCEL), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        return NotificationCompat.Builder(this, CHANNEL).setSmallIcon(R.drawable.ic_notification).setContentTitle(item?.title ?: "Bare Bones Downloader")
            .setContentText(item?.let { "${it.kind.label}: ${it.status.label}" } ?: "Preparing downloads…")
            .setContentIntent(open).setOngoing(true).setOnlyAlertOnce(true)
            .setProgress(100, item?.progressPercent ?: 0, item?.progressPercent == null)
            .addAction(0, "Cancel current", cancel).build()
    }
    override fun onTimeout(startId: Int, fgsType: Int) { app.coordinator.cancelAll(); stopForeground(STOP_FOREGROUND_REMOVE); stopSelf() }
    override fun onDestroy() { app.coordinator.cancelAll(); scope.cancel(); if (wakeLock.isHeld) wakeLock.release(); super.onDestroy() }
    override fun onBind(intent: Intent?): IBinder? = null
    companion object { const val CANCEL = "com.barebonesdownloader.android.CANCEL"; private const val CHANNEL = "downloads"; private const val ID = 7 }
}
