package com.barebonesdownloader.android

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.barebonesdownloader.android.domain.model.*
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
    @Before fun reset() {
        runBlocking { (ui.activity.application as DownloaderApplication).settings.update { AppSettings() } }
        ui.waitUntil(5_000) { ui.onAllNodesWithTag("queue_button").fetchSemanticsNodes().isNotEmpty() }
        ui.waitForIdle()
    }
    @Test fun modeSwitchPreservesInputAndRelevantFolderPrompt() {
        ui.onNodeWithTag("links_input").performScrollTo().performTextInput("https://youtu.be/BaW_jenozKc")
        ui.onNodeWithTag("mode_VIDEO").performScrollTo().performClick()
        ui.onNodeWithText("Video folder").assertExists()
        ui.onNodeWithTag("links_input").assertTextContains("https://youtu.be/BaW_jenozKc")
        ui.onNodeWithTag("queue_button").performScrollTo().performClick()
        ui.onNodeWithText("Choose video folder").assertIsDisplayed()
        ui.onNodeWithText("Cancel").performClick()
        ui.onNodeWithTag("links_input").assertTextContains("https://youtu.be/BaW_jenozKc")
        ui.onNodeWithTag("mode_AUDIO").performScrollTo().performClick()
        ui.onNodeWithText("Queue audio").assertExists()
    }
    @Test fun settingsCancelDiscardsAndSavePersistsAppearance() {
        ui.onNodeWithTag("open_settings").performClick()
        listOf("General", "Audio", "Video", "Metadata", "Appearance").forEach { ui.onNodeWithTag("section_$it").assertExists() }
        ui.onNodeWithTag("section_Appearance").performScrollTo().performClick()
        ui.onNodeWithTag("Theme").performClick(); ui.onNodeWithText("Dark").performClick()
        ui.onNodeWithTag("cancel_settings").performClick()
        ui.onNodeWithTag("open_settings").performClick(); ui.onNodeWithTag("section_Appearance").performScrollTo().performClick()
        ui.onNodeWithText("Light").assertExists()
        ui.onNodeWithTag("Theme").performClick(); ui.onNodeWithText("Dark").performClick()
        ui.onNodeWithTag("save_settings").performClick()
        ui.waitUntil(5_000) { ui.onAllNodesWithTag("open_settings").fetchSemanticsNodes().isNotEmpty() }
        ui.onNodeWithTag("open_settings").performClick(); ui.onNodeWithTag("section_Appearance").performScrollTo().performClick()
        ui.onNodeWithText("Dark").assertExists()
    }
    @Test fun previewUsesV2SampleAndTagToggle() {
        ui.onNodeWithTag("open_settings").performClick(); ui.onNodeWithTag("section_Metadata").performScrollTo().performClick()
        ui.onNodeWithText("Title: Here Comes the Sun").assertExists()
        ui.onNodeWithTag("metadata_TITLE").performScrollTo().assertIsOn().performClick()
        ui.onNodeWithText("Title: ").assertExists()
        ui.onNodeWithTag("cancel_settings").performClick()
    }
}
