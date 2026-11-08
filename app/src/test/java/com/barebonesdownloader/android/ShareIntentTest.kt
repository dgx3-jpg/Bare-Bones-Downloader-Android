package com.barebonesdownloader.android
import android.content.Intent
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35])
class ShareIntentTest {
    @Test fun onlyPlainTextSharesPrefillAndInputIsBounded() {
        val share=Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT," https://example.com/item ")
        assertEquals("https://example.com/item",sharedPlainText(share))
        assertNull(sharedPlainText(Intent(Intent.ACTION_VIEW).setType("text/plain").putExtra(Intent.EXTRA_TEXT,"https://example.com")))
        assertNull(sharedPlainText(Intent(Intent.ACTION_SEND).setType("image/png")))
        assertEquals(60000,sharedPlainText(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT,"x".repeat(100000)))!!.length)
    }
}
