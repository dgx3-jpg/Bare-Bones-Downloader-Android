package com.barebonesdownloader.android
import androidx.test.platform.app.InstrumentationRegistry
import com.barebonesdownloader.android.domain.model.*
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.junit.Test
import org.junit.Assert.*
import java.util.UUID
private val updateHistoryId=UUID.fromString("cd76bde5-b3ba-45a4-9f04-65e6943f00e1")
private val updateWaitingId=UUID.fromString("cd76bde5-b3ba-45a4-9f04-65e6943f00e2")
class UpdateSeedTest {
    @Test fun seedExistingVersion() = runBlocking<Unit> {
        val app=InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as DownloaderApplication
        app.ready.await()
        app.settings.update { it.copy(theme=AppThemeMode.DARK,appendSourceUrl=true,videoMaxHeight=720) }
        app.database.downloads().insert(DownloadItem(id=updateHistoryId,sourceUrl="owned-update-history",status=DownloadStatus.COMPLETED))
        app.database.downloads().insert(DownloadItem(id=updateWaitingId,sourceUrl="https://example.org/owned-update",extractorKey="generic",mediaId="update",queueOrder=app.database.downloads().nextOrder(),reservationKey="generic:update|AUDIO"))
        assertTrue(app.settings.settings.first().appendSourceUrl)
    }
}
class UpdateVerifyTest {
    @Test fun preferencesAndRoomSurviveInPlaceUpdate() = runBlocking<Unit> {
        val app=InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as DownloaderApplication
        app.ready.await()
        val settings=app.settings.settings.first()
        assertEquals(AppThemeMode.DARK,settings.theme); assertEquals(720,settings.videoMaxHeight); assertTrue(settings.appendSourceUrl)
        assertEquals(DownloadStatus.COMPLETED,app.database.downloads().get(updateHistoryId)!!.status)
        val pending=app.database.downloads().get(updateWaitingId)!!
        assertEquals(DownloadStatus.QUEUED,pending.status); assertEquals("generic:update|AUDIO",pending.reservationKey)
        app.downloads.clearWaiting()
    }
}
