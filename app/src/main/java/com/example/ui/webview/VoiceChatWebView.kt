package com.example.ui.webview

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.util.Log
import android.view.ViewGroup
import android.webkit.ConsoleMessage
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.PermissionRequest
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.example.data.AuthState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val TARGET_WEB_URL = "https://yaraan-voice-chat.netlify.app"
private const val TAG = "VoiceChatWebView"

class WebAppInterface(
    private val onLogout: () -> Unit,
    private val onUserMessage: (String) -> Unit
) {
    @JavascriptInterface
    fun postMessage(message: String) {
        Log.d(TAG, "Message from Web: $message")
        if (message == "user_logged_out" || message.contains("logout", ignoreCase = true)) {
            onLogout()
        } else {
            onUserMessage(message)
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun VoiceChatWebView(
    authState: AuthState.Authenticated,
    onLogoutRequested: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var webViewInstance by remember { mutableStateOf<WebView?>(null) }
    var pageProgress by remember { mutableFloatStateOf(0f) }
    var isPageLoading by remember { mutableStateOf(true) }
    var hasError by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }
    var permissionGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionsToRequest = remember {
        mutableListOf(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.CAMERA,
            Manifest.permission.MODIFY_AUDIO_SETTINGS
        ).apply {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.POST_NOTIFICATIONS)
                add(Manifest.permission.READ_MEDIA_IMAGES)
            }
        }.toTypedArray()
    }

    // Permissions Launcher for Voice Chat and Media
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val recordAudioGranted = permissions[Manifest.permission.RECORD_AUDIO] ?: false
        permissionGranted = recordAudioGranted
        webViewInstance?.reload()
    }

    LaunchedEffect(Unit) {
        val missingPermissions = permissionsToRequest.filter {
            ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missingPermissions.isNotEmpty()) {
            permissionLauncher.launch(missingPermissions.toTypedArray())
        }
    }

    // Handle Android Back Navigation
    BackHandler(enabled = webViewInstance?.canGoBack() == true) {
        webViewInstance?.goBack()
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF05020A))
    ) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                try {
                    WebView(ctx).apply {
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                        setBackgroundColor(android.graphics.Color.parseColor("#05020A"))

                        settings.apply {
                            javaScriptEnabled = true
                            domStorageEnabled = true
                            databaseEnabled = true
                            mediaPlaybackRequiresUserGesture = false
                            allowFileAccess = true
                            allowContentAccess = true
                            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                            cacheMode = WebSettings.LOAD_DEFAULT
                            useWideViewPort = true
                            loadWithOverviewMode = true
                            displayZoomControls = false
                            builtInZoomControls = false

                            val defaultUA = userAgentString ?: ""
                            userAgentString = "$defaultUA YaraanFlutterApp YaraanNativeAndroid/1.0"
                        }

                        try {
                            val cookieManager = CookieManager.getInstance()
                            cookieManager.setAcceptCookie(true)
                            cookieManager.setAcceptThirdPartyCookies(this, true)
                        } catch (e: Throwable) {
                            Log.e(TAG, "CookieManager init error", e)
                        }

                        addJavascriptInterface(
                            WebAppInterface(
                                onLogout = {
                                    coroutineScope.launch {
                                        onLogoutRequested()
                                    }
                                },
                                onUserMessage = { msg ->
                                    Log.d(TAG, "Web JS message: $msg")
                                }
                            ),
                            "YaraanAppChannel"
                        )

                        webChromeClient = object : WebChromeClient() {
                            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                pageProgress = newProgress / 100f
                                isPageLoading = newProgress < 100
                            }

                            override fun onPermissionRequest(request: PermissionRequest?) {
                                request?.let { permRequest ->
                                    try {
                                        val requestedResources = permRequest.resources
                                        Log.d(TAG, "Web requested permissions: ${requestedResources?.joinToString()}")
                                        permRequest.grant(requestedResources)
                                    } catch (e: Throwable) {
                                        Log.e(TAG, "Error granting WebView permissions", e)
                                    }
                                }
                            }

                            override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
                                Log.d(TAG, "Web Console: ${consoleMessage?.message()}")
                                return super.onConsoleMessage(consoleMessage)
                            }
                        }

                        webViewClient = object : WebViewClient() {
                            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                                super.onPageStarted(view, url, favicon)
                                isPageLoading = true
                                hasError = false
                                injectBypassStyles(view)
                            }

                            override fun onPageFinished(view: WebView?, url: String?) {
                                super.onPageFinished(view, url)
                                isPageLoading = false
                                injectBypassStyles(view)
                                dispatchNativeAuth(view, authState)
                            }

                            override fun onReceivedError(
                                view: WebView?,
                                request: WebResourceRequest?,
                                error: WebResourceError?
                            ) {
                                super.onReceivedError(view, request, error)
                                if (request?.isForMainFrame == true) {
                                    hasError = true
                                    errorMessage = error?.description?.toString() ?: "Failed to connect to Yaraan servers."
                                }
                            }
                        }

                        loadUrl(TARGET_WEB_URL)
                        webViewInstance = this
                    }
                } catch (e: Throwable) {
                    Log.e(TAG, "WebView instantiation error", e)
                    hasError = true
                    errorMessage = "WebView unavailable: ${e.localizedMessage}"
                    WebView(ctx)
                }
            },
            update = { view ->
                webViewInstance = view
            }
        )

        // Loading Progress Bar at top
        if (isPageLoading) {
            LinearProgressIndicator(
                progress = { pageProgress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .align(Alignment.TopCenter)
                    .statusBarsPadding(),
                color = Color(0xFFC084FC),
                trackColor = Color(0x33C084FC)
            )
        }

        // Full Page Loading Spinner when initial page is connecting
        AnimatedVisibility(
            visible = isPageLoading && pageProgress < 0.4f,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFF05020A)),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Surface(
                        shape = RoundedCornerShape(22.dp),
                        color = Color(0xFF180D2E),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.15f)),
                        shadowElevation = 16.dp,
                        modifier = Modifier.size(100.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(
                                color = Color(0xFFC084FC),
                                modifier = Modifier.size(38.dp),
                                strokeWidth = 3.dp
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Entering Yaraan...",
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        // Error Screen overlay with Retry button
        if (hasError) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFF05020A))
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(24.dp))
                        .background(Color(0xFF180D2E))
                        .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(24.dp))
                        .padding(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = "Error",
                        tint = Color(0xFFF59E0B),
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Connection Issue",
                        color = Color.White,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = errorMessage.ifBlank { "Could not load voice chat rooms. Please check your internet connection." },
                        color = Color(0xFF9CA3AF),
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    Button(
                        onClick = {
                            hasError = false
                            webViewInstance?.reload()
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF7C3AED)
                        ),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null, tint = Color.White)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Retry", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            try {
                webViewInstance?.apply {
                    stopLoading()
                    loadUrl("about:blank")
                    clearHistory()
                    removeAllViews()
                    destroy()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error destroying webview", e)
            } finally {
                webViewInstance = null
            }
        }
    }
}

/**
 * Injects CSS and DOM manipulations into the web page to bypass & hide the web login screen,
 * ensuring only the native Android login controls the session.
 */
private fun injectBypassStyles(webView: WebView?) {
    val bypassScript = """
        (function() {
            try {
                localStorage.setItem('privacy_accepted', 'true');
                localStorage.setItem('user_logged_in', 'true');

                var style = document.getElementById('native-bypass-style');
                if (!style) {
                    style = document.createElement('style');
                    style.id = 'native-bypass-style';
                    style.innerHTML = '#view-auth, .auth-overlay, #auth-content-wrapper, #email-auth-full-page, #forgot-password-modal { display: none !important; visibility: hidden !important; opacity: 0 !important; pointer-events: none !important; }';
                    document.head.appendChild(style);
                }
                
                var authView = document.getElementById('view-auth');
                if (authView) authView.style.display = 'none';
                
                var authMain = document.getElementById('auth-main-screen');
                if (authMain) authMain.style.display = 'none';
            } catch(e) {
                console.error("Bypass injection error:", e);
            }
        })();
    """.trimIndent()

    webView?.evaluateJavascript(bypassScript, null)
}

/**
 * Dispatches the authenticated user token/credentials to the Web JS event listener.
 */
private fun dispatchNativeAuth(webView: WebView?, authState: AuthState.Authenticated) {
    val jsScript = when {
        authState.authType == "google" && !authState.idToken.isNullOrBlank() -> {
            """
                (function() {
                    try {
                        localStorage.setItem('privacy_accepted', 'true');
                        window.postMessage({
                            type: 'googleLogin',
                            idToken: '${authState.idToken}'
                        }, '*');
                        
                        var authView = document.getElementById('view-auth');
                        if (authView) authView.style.display = 'none';
                    } catch(e) {
                        console.error("Google Auth Dispatch Error:", e);
                    }
                })();
            """.trimIndent()
        }
        authState.authType == "email" && !authState.email.isNullOrBlank() && !authState.password.isNullOrBlank() -> {
            """
                (function() {
                    try {
                        localStorage.setItem('privacy_accepted', 'true');
                        window.postMessage({
                            type: 'emailLogin',
                            email: '${authState.email}',
                            password: '${authState.password}'
                        }, '*');
                        
                        var authView = document.getElementById('view-auth');
                        if (authView) authView.style.display = 'none';
                    } catch(e) {
                        console.error("Email Auth Dispatch Error:", e);
                    }
                })();
            """.trimIndent()
        }
        else -> {
            """
                (function() {
                    try {
                        localStorage.setItem('privacy_accepted', 'true');
                        localStorage.setItem('user_logged_in', 'true');
                        var authView = document.getElementById('view-auth');
                        if (authView) authView.style.display = 'none';
                    } catch(e) {}
                })();
            """.trimIndent()
        }
    }

    webView?.evaluateJavascript(jsScript, null)
}
