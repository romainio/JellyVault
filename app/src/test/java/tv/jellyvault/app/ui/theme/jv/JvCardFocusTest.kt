package tv.jellyvault.app.ui.theme.jv

import android.app.Application
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.unit.dp
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Down from a focused card must reach the next row, not the card beside it.
 * A vertical lift of the focused card broke this on the TV (its focus bounds moved up), so cards only scale.
 */
@OptIn(ExperimentalTestApi::class)
@Config(application = Application::class, sdk = [34])
@RunWith(RobolectricTestRunner::class)
class JvCardFocusTest {
    @get:Rule
    val rule = createComposeRule()

    @Test
    fun down_from_a_raised_card_goes_to_the_next_row() {
        rule.setContent {
            Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
                repeat(2) { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        repeat(4) { col ->
                            val source = remember { MutableInteractionSource() }
                            androidx.compose.foundation.layout.Box(
                                Modifier
                                    .size(116.dp, 172.dp)
                                    .focusable(interactionSource = source)
                                    .testTag("card_${row}_$col"),
                            )
                        }
                    }
                }
            }
        }
        rule.onNodeWithTag("card_0_1").requestFocus()
        rule.waitForIdle()
        rule.onNodeWithTag("card_0_1").performKeyInput { pressKey(Key.DirectionDown) }
        rule.waitForIdle()
        rule.onNodeWithTag("card_1_1").assertIsFocused()
    }

    @Test
    fun down_from_a_raised_tv_card_in_lazy_rows_goes_to_the_next_row() {
        rule.setContent {
            androidx.compose.foundation.lazy.LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(3) { row ->
                    Column {
                        androidx.tv.material3.Text("Row $row", modifier = Modifier.padding(bottom = 8.dp))
                        androidx.compose.foundation.lazy.LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            contentPadding =
                                androidx.compose.foundation.layout
                                    .PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        ) {
                            items(8) { col ->
                                val source = remember { MutableInteractionSource() }
                                androidx.tv.material3.Card(
                                    onClick = {},
                                    interactionSource = source,
                                    scale = JvCardDefaults.scale(),
                                    border = JvCardDefaults.border(),
                                    glow = JvCardDefaults.glow(),
                                    shape = JvCardDefaults.shape(),
                                    modifier = Modifier.size(116.dp, 172.dp).testTag("tv_${row}_$col"),
                                ) {}
                            }
                        }
                    }
                }
            }
        }
        rule.onNodeWithTag("tv_0_1").requestFocus()
        rule.waitForIdle()
        rule.onNodeWithTag("tv_0_1").performKeyInput { pressKey(Key.DirectionDown) }
        rule.waitForIdle()
        rule.onNodeWithTag("tv_1_1").assertIsFocused()
    }
}
