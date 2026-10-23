package dev.rosalogia.photoduel.feature.duel

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.test.doubleClick
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.pinch
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.swipeUp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FlipViewTest {
    @get:Rule val rule = createComposeRule()

    private val zoom = ZoomState()
    private var showChallenger by mutableStateOf(false)
    private val pressEvents = mutableListOf<Boolean>()
    private val swipes = mutableListOf<Boolean>()

    @Before
    fun setUp() {
        rule.setContent {
            FlipView(
                king = ImageBitmap(400, 300),
                challenger = ImageBitmap(400, 300),
                showChallenger = showChallenger,
                zoom = zoom,
                onPress = {
                    pressEvents += it
                    showChallenger = it
                },
                onSwipe = { swipes += it },
                onDrag = {},
                modifier = Modifier.fillMaxSize(),
            )
        }
    }

    @Test
    fun pressShowsChallengerUntilRelease() {
        rule.onNodeWithTag("flipView").performTouchInput { down(center) }
        rule.runOnIdle { assertThat(showChallenger).isTrue() }
        rule.onNodeWithTag("flipView").performTouchInput { up() }
        rule.runOnIdle {
            assertThat(showChallenger).isFalse()
            assertThat(pressEvents).containsExactly(true, false).inOrder()
        }
    }

    @Test
    fun swipeUpKeepsChallenger_swipeDownKeepsKing() {
        rule.onNodeWithTag("flipView").performTouchInput { swipeUp() }
        rule.onNodeWithTag("flipView").performTouchInput { swipeDown() }
        rule.runOnIdle { assertThat(swipes).containsExactly(true, false).inOrder() }
    }

    @Test
    fun pinchZoomsAndStopsFlipping() {
        rule.onNodeWithTag("flipView").performTouchInput {
            pinch(center - Offset(20f, 0f), center - Offset(200f, 0f), center + Offset(20f, 0f), center + Offset(200f, 0f))
        }
        rule.runOnIdle {
            assertThat(zoom.scale).isGreaterThan(2f)
            assertThat(showChallenger).isFalse()
            assertThat(swipes).isEmpty()
        }
    }

    @Test
    fun whenZoomedOneFingerPansInsteadOfFlippingOrPicking() {
        rule.onNodeWithTag("flipView").performTouchInput {
            pinch(center - Offset(20f, 0f), center - Offset(200f, 0f), center + Offset(20f, 0f), center + Offset(200f, 0f))
        }
        pressEvents.clear()
        val before = rule.runOnIdle { zoom.offset }
        rule.onNodeWithTag("flipView").performTouchInput { swipeUp() }
        rule.runOnIdle {
            assertThat(zoom.offset).isNotEqualTo(before)
            assertThat(pressEvents).isEmpty()
            assertThat(swipes).isEmpty()
        }
    }

    @Test
    fun doubleTapTogglesZoom() {
        rule.onNodeWithTag("flipView").performTouchInput { doubleClick(center) }
        rule.runOnIdle { assertThat(zoom.isZoomed).isTrue() }
        rule.onNodeWithTag("flipView").performTouchInput { doubleClick(center) }
        rule.runOnIdle { assertThat(zoom.isZoomed).isFalse() }
    }
}
