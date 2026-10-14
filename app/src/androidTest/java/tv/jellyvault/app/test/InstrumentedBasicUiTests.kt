package tv.jellyvault.app.test

import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.navigation3.runtime.NavBackStack
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import tv.jellyvault.app.MainContent
import tv.jellyvault.app.services.NavigationManager
import tv.jellyvault.app.services.ScreensaverService
import tv.jellyvault.app.services.ScreensaverState
import tv.jellyvault.app.services.SetupDestination
import tv.jellyvault.app.ui.nav.Destination
import tv.jellyvault.app.ui.theme.JellyVaultTheme

@HiltAndroidTest
class InstrumentedBasicUiTests {
    @get:Rule(order = 0)
    var hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeTestRule = createAndroidComposeRule<TestActivity>()

    lateinit var screensaverService: ScreensaverService

    @Before
    fun setup() {
        screensaverService = mockk(relaxed = true)
        every { screensaverService.state } returns MutableStateFlow(ScreensaverState(false, false, false, false))
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun myTest() {
        val navigationManager = NavigationManager()
        navigationManager.backStack = NavBackStack(Destination.Home())
        // Start the app
        composeTestRule.setContent {
            JellyVaultTheme {
                MainContent(
                    backStack = mutableListOf(SetupDestination.ServerList),
                    navigationManager = navigationManager,
                    appPreferences = mockk(relaxed = true),
                    backdropService = mockk(relaxed = true),
                    screensaverService = screensaverService,
                    modifier = Modifier,
                )
            }
        }

        composeTestRule.onNodeWithText("Add Server").assertExists()
        composeTestRule.onNodeWithTag("add_server").performKeyInput {
            pressKey(Key.DirectionDown) // TODO
        }
        composeTestRule.onNodeWithTag("add_server").performClickEnter()

        composeTestRule.onNodeWithText("Discovered Servers").assertExists()
    }
}

@OptIn(ExperimentalTestApi::class)
fun SemanticsNodeInteraction.performClickEnter() =
    performKeyInput {
        pressKey(Key.DirectionCenter)
    }
