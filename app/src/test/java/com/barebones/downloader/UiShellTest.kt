package com.barebones.downloader

import androidx.compose.ui.test.*
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.barebones.downloader.domain.model.AppSettings
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class UiShellTest {
    @get:Rule val ui = createAndroidComposeRule<MainActivity>()
    @Before fun resetSettings() {
        runBlocking { (ui.activity.application as DownloaderApplication).settings.update { AppSettings() } }
        ui.waitUntil(5_000) { ui.onAllNodesWithText("Add to queue").fetchSemanticsNodes().any { !it.config.contains(SemanticsProperties.Disabled) } }
        ui.waitForIdle()
    }
    @Test fun allFourTabsNavigateAndHistoryRequiresConfirmation() {
        ui.onNodeWithTag("media_url").assertIsDisplayed()
        ui.onNodeWithTag("nav_Queue").performClick()
        ui.onNodeWithText("Demo audio — fake progress").assertExists()
        ui.onNodeWithTag("nav_History").performClick()
        ui.onNodeWithText("Clear history").performClick()
        ui.onNodeWithText("Clear history?").assertIsDisplayed()
        ui.onNodeWithText("Cancel").performClick()
        ui.onNodeWithTag("nav_Settings").performClick()
        ui.onNodeWithText("Output type: Audio only").assertExists()
        ui.onNodeWithTag("nav_Home").performClick()
        ui.onNodeWithTag("media_url").assertIsDisplayed()
    }
    @Test fun invalidUrlShowsErrorAndValidUrlRequiresFolder() {
        ui.onNodeWithTag("media_url").performTextInput("not a URL")
        ui.onNodeWithText("Add to queue").performClick()
        ui.onNodeWithText("Enter a valid http or https URL.").assertExists()
        ui.onNodeWithTag("media_url").performTextReplacement("https://example.org/audio")
        ui.onNodeWithText("Add to queue").performClick()
        ui.onNodeWithText("Choose a save folder").assertIsDisplayed()
    }
    @Test fun settingsAndPreviewReactToMetadataToggle() {
        ui.onNodeWithTag("nav_Settings").performClick()
        ui.onNodeWithTag("metadata_TITLE").performScrollTo().assertIsOn().performClick()
        ui.waitUntil(5_000) { ui.onNodeWithTag("metadata_TITLE").fetchSemanticsNode().config[SemanticsProperties.ToggleableState] == ToggleableState.Off }
        ui.onNodeWithTag("metadata_TITLE").assertIsOff()
        ui.onNodeWithTag("preview_tags").performScrollTo().performClick()
        ui.onNodeWithText("Illustrative tag preview").assertIsDisplayed()
        ui.onNodeWithText("Title: Come Together").assertDoesNotExist()
        ui.onNodeWithText("Artist: The Beatles").assertExists()
        ui.onNodeWithText("Close").performClick()
        ui.onNodeWithTag("nav_Home").performClick()
        ui.onNodeWithTag("nav_Settings").performClick()
        ui.onNodeWithTag("metadata_TITLE").performScrollTo().assertIsOff()
    }
}
