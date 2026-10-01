package com.k410sh4.a25lab

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import org.junit.Rule
import org.junit.Test

class A25LabUiSmokeTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun dashboardShowsPrimaryNavigation() {
        composeRule.onNodeWithTag("nav-Dashboard").assertIsDisplayed()
        composeRule.onNodeWithTag("nav-Perception").assertIsDisplayed()
        composeRule.onNodeWithTag("nav-Connectivity").assertIsDisplayed()
        composeRule.onNodeWithTag("nav-Lab").assertIsDisplayed()
    }

    @Test
    fun genesisScreenOpensOnAndroid16() {
        composeRule.onNode(hasScrollAction())
            .performScrollToNode(hasText("GENESIS"))
        composeRule.onNodeWithText("GENESIS").performClick()
        composeRule.onNodeWithText("Memória aprendente v0.1").assertIsDisplayed()
        composeRule.onNodeWithText("Importar PDF").assertIsDisplayed()
    }
}
