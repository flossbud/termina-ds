package com.terminads.mm.secondscreen

import androidx.compose.material3.Text
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Proves the Compose UI test toolchain runs inside the Docker image. If this
 * test cannot be made green, Tasks 6-8 drop their Robolectric layer and keep
 * their pure-model coverage -- see the plan's Task 1 note.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33])
class ComposeSmokeTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun composeRuleRendersAndFindsANode() {
        composeTestRule.setContent { Text("termina-ds-compose-smoke") }
        composeTestRule.onNodeWithText("termina-ds-compose-smoke").assertIsDisplayed()
    }
}
