package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.example.data.AuthState
import com.example.ui.AuthViewModel
import com.example.ui.login.LoginScreen
import com.example.ui.splash.SplashScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.webview.VoiceChatWebView

class MainActivity : ComponentActivity() {

    private val authViewModel: AuthViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            enableEdgeToEdge()
        } catch (e: Throwable) {
            android.util.Log.e("MainActivity", "enableEdgeToEdge error", e)
        }

        try {
            if (com.google.firebase.FirebaseApp.getApps(this).isEmpty()) {
                com.google.firebase.FirebaseApp.initializeApp(this)
            }
        } catch (e: Throwable) {
            android.util.Log.e("MainActivity", "Firebase init error", e)
        }

        try {
            setContent {
                MyApplicationTheme(darkTheme = true) {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = Color(0xFF05020A)
                    ) {
                        MainAppContent(viewModel = authViewModel)
                    }
                }
            }
        } catch (e: Throwable) {
            android.util.Log.e("MainActivity", "setContent error", e)
        }
    }
}

@Composable
fun MainAppContent(viewModel: AuthViewModel) {
    val authState by viewModel.authState.collectAsState()
    var isSplashFinished by remember { mutableStateOf(false) }

    if (!isSplashFinished) {
        SplashScreen(
            onSplashFinished = {
                isSplashFinished = true
            },
            modifier = Modifier.fillMaxSize()
        )
    } else {
        Crossfade(
            targetState = authState,
            animationSpec = tween(400),
            label = "auth_crossfade"
        ) { state ->
            when (state) {
                is AuthState.Authenticated -> {
                    VoiceChatWebView(
                        authState = state,
                        onLogoutRequested = {
                            viewModel.onLogoutFromWeb()
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }
                else -> {
                    LoginScreen(
                        viewModel = viewModel,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
    }
}
