package com.terminads.mm.secondscreen

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.terminads.mm.BridgeState
import com.terminads.mm.CommandBridge
import com.terminads.mm.DEFAULT_GAME_SETTINGS
import com.terminads.mm.GameSnapshot
import java.time.Duration
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowSystemClock

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33])
class SecondScreenHostTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private data class CommandCall(
        val op: Int,
        val a: Int,
        val b: Int,
        val name: String?,
    )

    private val pausedState = BridgeState.Live(
        GameSnapshot(
            frameCounter = 7, health = 48, healthCapacity = 48, magic = 0,
            magicCapacity = 0, magicLevel = 0, rupees = 0, playerForm = 4,
            equippedMask = 0, day = 1, timeOfDay = 0x4000, isNight = false,
            doubleDefense = false, buttonItems = listOf(255, 255, 255, 255),
            buttonAmmo = listOf(0, 0, 0, 0), hasPlayState = true,
            hasPlayer = true, sceneId = 0x2D, roomNum = 0,
            playerX = 0f, playerY = 0f, playerZ = 0f, playerYaw = 0,
            isPaused = true, saveLoaded = true, menuOpen = false,
            settings = DEFAULT_GAME_SETTINGS,
        ),
    )
    private val unpausedState = BridgeState.Live(
        pausedState.snapshot.copy(isPaused = false),
    )

    private fun showHost(
        commandBridge: CommandBridge,
        pollBridge: () -> BridgeState = { pausedState },
    ) {
        composeTestRule.setContent {
            SecondScreenHost(
                displayInfo = DisplayInfo(
                    displayId = 1, name = "test", widthPx = 1920, heightPx = 1080,
                    refreshRate = 60f, isDefault = false,
                ),
                pollBridge = pollBridge,
                commandBridge = commandBridge,
                pauseTracker = PauseRequestTracker(nowMillis = { 0L }),
            )
        }
    }

    private fun openOptionsAndSelectFourTimesMsaa() {
        composeTestRule.onNodeWithContentDescription("Options").performClick()
        composeTestRule.onNodeWithText("SETTINGS").assertIsDisplayed()
        composeTestRule.onNodeWithText("4×").performClick()
    }

    @Test
    fun optionsNavigationSubmitsAnAbsoluteRowCommand() {
        val calls = mutableListOf<CommandCall>()
        showHost(
            CommandBridge { op, a, b, name ->
                calls += CommandCall(op, a, b, name)
                0
            },
        )

        openOptionsAndSelectFourTimesMsaa()

        assertEquals(
            listOf(CommandCall(CommandBridge.OP_SET_MSAA, 4, 0, null)),
            calls,
        )
    }

    @Test
    fun observedUnpauseResetsOptionsNavigationBeforeTheNextPause() {
        var polledState: BridgeState = pausedState
        showHost(
            commandBridge = CommandBridge { _, _, _, _ -> 0 },
            pollBridge = { polledState },
        )
        composeTestRule.onNodeWithContentDescription("Options").performClick()
        composeTestRule.onNodeWithText("SETTINGS").assertIsDisplayed()

        polledState = unpausedState
        composeTestRule.mainClock.advanceTimeBy(100L)
        composeTestRule.waitForIdle()

        polledState = pausedState
        composeTestRule.mainClock.advanceTimeBy(100L)
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithContentDescription("Options").assertIsDisplayed()
    }

    @Test
    fun aFullSaveSubmissionIsRetriedOnALaterPoll() {
        val calls = mutableListOf<CommandCall>()
        var saveAttempts = 0
        showHost(
            CommandBridge { op, a, b, name ->
                calls += CommandCall(op, a, b, name)
                if (op == CommandBridge.OP_CVAR_SAVE && saveAttempts++ == 0) 1 else 0
            },
        )
        openOptionsAndSelectFourTimesMsaa()

        ShadowSystemClock.advanceBy(Duration.ofMillis(2_001L))
        composeTestRule.mainClock.advanceTimeBy(100L)
        composeTestRule.waitForIdle()
        assertEquals(1, calls.count { it.op == CommandBridge.OP_CVAR_SAVE })

        composeTestRule.mainClock.advanceTimeBy(100L)
        composeTestRule.waitForIdle()
        assertEquals(2, calls.count { it.op == CommandBridge.OP_CVAR_SAVE })
    }
}
