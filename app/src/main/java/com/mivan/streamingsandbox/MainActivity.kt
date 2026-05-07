package com.mivan.streamingsandbox

import android.os.Bundle
import android.view.Window
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.mivan.streamingsandbox.feature.player.presentation.PlayerViewModel
import com.mivan.streamingsandbox.feature.player.presentation.ui.PlayerScreen
import com.mivan.streamingsandbox.ui.theme.StreamingSandboxTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val vm: PlayerViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            StreamingSandboxTheme {
                HandleSystemBarsForOrientation(window = window)
                val uiState by vm.uiState.collectAsState()
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black)
                ) {
                    PlayerScreen(
                        uiState = uiState,
                        onAttachPlayerView = vm::attachPlayerView,
                        onDetachPlayerView = vm::detachPlayerView,
                        openMediaSelector = vm::openMediaSelector,
                        openFavoritesSelector = vm::openFavoritesSelector,
                        onSelectChannel = vm::selectChannel,
                        onSelectVod = vm::selectVod,
                        onToggleChannelFavorite = vm::toggleChannelFavorite,
                        onToggleVodFavorite = vm::toggleVodFavorite,
                        onRetry = vm::retryCurrentMedia,
                        onProgramRefresh = vm::refreshEpg,
                        onTogglePlayPause = vm::togglePlayPause,
                        onSeekBack = vm::seekBack,
                        onSeekForward = vm::seekForward,
                        onSeekTo = vm::seekTo,
                        onGoToLive = vm::goToLive
                    )
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        vm.onHostStart()
    }

    override fun onStop() {
        vm.onHostStop()
        super.onStop()
    }
}

@Composable
private fun HandleSystemBarsForOrientation(window: Window) {
    val lifecycleOwner = LocalLifecycleOwner.current

    val applySystemBarsMode = remember(window) {
        {
            val insetsController = WindowCompat.getInsetsController(window, window.decorView)
            WindowCompat.setDecorFitsSystemWindows(window, false)
            insetsController.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            insetsController.hide(WindowInsetsCompat.Type.statusBars())
        }
    }

    DisposableEffect(window, lifecycleOwner) {
        applySystemBarsMode()

        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                // Re-apply immersive mode because Android can restore bars automatically.
                applySystemBarsMode()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            WindowCompat.setDecorFitsSystemWindows(window, true)
            WindowCompat.getInsetsController(window, window.decorView)
                .show(WindowInsetsCompat.Type.statusBars())
        }
    }
}