package dev.titanslot.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import dev.titanslot.app.AppState
import dev.titanslot.app.GameController
import dev.titanslot.app.Mode

/**
 * Full screen, cutout included. The Titan 2 Elite's camera hole is a small square in the top
 * left corner and its corners are rounded, so the layouts keep to the middle instead of
 * giving up a 123 px strip across the whole top.
 */
@Composable
fun UiRoot(content: @Composable () -> Unit) {
    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        val density = LocalDensity.current
        val w = with(density) { maxWidth.toPx() }
        val h = with(density) { maxHeight.toPx() }
        val ui = remember(w, h, density) { Ui.of(w, h, density) }
        CompositionLocalProvider(LocalUi provides ui) {
            Box(Modifier.fillMaxSize()) { content() }
        }
    }
}

@Composable
fun TitanSlotApp(app: AppState) = UiRoot {
    when (app.mode) {
        Mode.Setup -> SetupScreen(app)
        else -> {
            ShelfScreen(app)
            ShelfOverlays(app)
        }
    }
    ToastHost(app.toast)
}

@Composable
fun GameApp(game: GameController) = UiRoot {
    GameScreen(game)
    GameOverlays(game)
    ToastHost(game.toast)
}
