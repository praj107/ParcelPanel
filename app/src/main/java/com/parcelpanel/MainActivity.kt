package com.parcelpanel

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.compose.viewModel
import com.parcelpanel.ui.ParcelPanelApp
import com.parcelpanel.ui.ParcelViewModel
import com.parcelpanel.ui.theme.ParcelPanelTheme

class MainActivity : ComponentActivity() {
    private var currentIntentState by mutableStateOf<Intent?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        currentIntentState = intent

        val app = application as ParcelPanelApplication
        setContent {
            val factory = remember(app) {
                ParcelViewModel.Factory(
                    repository = app.repository,
                    scheduler = app.refreshScheduler,
                )
            }
            val viewModel: ParcelViewModel = viewModel(factory = factory)
            val capturedIntent = currentIntentState
            ParcelPanelTheme {
                ParcelPanelApp(
                    viewModel = viewModel,
                    incomingIntent = capturedIntent,
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        currentIntentState = intent
    }
}

