package com.barebones.downloader

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NavigationSmokeTest {
    @get:Rule val ui = createAndroidComposeRule<MainActivity>()
    @Test fun navigateBetweenFourScreens() {
        ui.onNodeWithTag("media_url").assertIsDisplayed()
        ui.onNodeWithTag("nav_Queue").performClick()
        ui.onNodeWithText("Clear completed").assertExists()
        ui.onNodeWithTag("nav_History").performClick()
        ui.onNodeWithText("Clear history").assertExists()
        ui.onNodeWithTag("nav_Settings").performClick()
        ui.onNodeWithText("Save folder").assertExists()
        ui.onNodeWithTag("nav_Home").performClick()
        ui.onNodeWithTag("media_url").assertIsDisplayed()
    }
}
