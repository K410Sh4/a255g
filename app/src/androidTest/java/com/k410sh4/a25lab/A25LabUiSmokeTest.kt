package com.k410sh4.a25lab

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import org.junit.Rule
import org.junit.Test

class A25LabUiSmokeTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun dashboardShowsPrimaryNavigation() {
        composeRule.onNodeWithText("Início").assertIsDisplayed()
        composeRule.onNodeWithText("Percepção").assertIsDisplayed()
        composeRule.onNodeWithText("Conexões").assertIsDisplayed()
        composeRule.onNodeWithText("Lab").assertIsDisplayed()
    }
}
