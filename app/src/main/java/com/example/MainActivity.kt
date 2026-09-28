package com.example

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.CodeEditorScreen
import com.example.ui.MainScreen
import com.example.ui.TerminalScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.viewmodel.PyHostViewModel
import com.example.viewmodel.Screen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme(darkTheme = true) {
                // Request notification permission for Android 13+ foreground service
                val notificationPermissionLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.RequestPermission()
                ) { /* acknowledged */ }

                LaunchedEffect(Unit) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                }

                PyHostApp()
            }
        }
    }
}

@Composable
fun PyHostApp(
    viewModel: PyHostViewModel = viewModel()
) {
    val currentScreen by viewModel.currentScreen.collectAsState()

    Box(modifier = Modifier.fillMaxSize()) {
        AnimatedContent(
            targetState = currentScreen,
            transitionSpec = {
                when {
                    targetState == Screen.TERMINAL -> {
                        slideInVertically { it } + fadeIn() togetherWith
                                slideOutVertically { -it / 2 } + fadeOut()
                    }
                    targetState == Screen.CODE_EDITOR -> {
                        slideInHorizontally { it } + fadeIn() togetherWith
                                slideOutHorizontally { -it / 2 } + fadeOut()
                    }
                    else -> {
                        fadeIn() togetherWith fadeOut()
                    }
                }
            },
            label = "screen_transition"
        ) { screen ->
            when (screen) {
                Screen.FILE_LIST -> MainScreen(viewModel = viewModel)
                Screen.CODE_EDITOR -> CodeEditorScreen(viewModel = viewModel)
                Screen.TERMINAL -> TerminalScreen(viewModel = viewModel)
            }
        }
    }
}
