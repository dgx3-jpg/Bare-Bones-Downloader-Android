package com.barebonesdownloader.android

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NavigationSmokeTest {
    @get:Rule val ui = createAndroidComposeRule<MainActivity>()
    @Test fun mainAndSettingsNavigate() {
        ui.onNodeWithTag("links_input").assertExists()
        ui.onNodeWithTag("mode_VIDEO").performClick()
        ui.onNodeWithText("Video folder").assertExists()
        ui.onNodeWithTag("open_settings").performClick()
        ui.onNodeWithTag("section_Video").performScrollTo().performClick()
        ui.onNodeWithText("Maximum resolution").assertExists()
        ui.onNodeWithTag("cancel_settings").performClick()
        ui.onNodeWithTag("links_input").assertExists()
    }
}
