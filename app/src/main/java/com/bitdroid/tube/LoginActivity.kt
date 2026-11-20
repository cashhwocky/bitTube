package com.bitdroid.tube

import android.annotation.SuppressLint
import android.os.Bundle
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.bitdroid.tube.data.Account
import com.bitdroid.tube.data.Library
import com.bitdroid.tube.ui.Bit
import com.bitdroid.tube.ui.BitTheme
import com.bitdroid.tube.ui.CookieLoader
import com.bitdroid.tube.ui.Header
import com.bitdroid.tube.ui.backdrop
import kotlinx.coroutines.launch

/** Google's own sign-in page in a WebView. When it lands back on YouTube we keep the cookies. */
class LoginActivity : ComponentActivity() {
    private var web: WebView? = null
    private var done = false

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        Bit.apply(this)
        val cm = CookieManager.getInstance()
        cm.setAcceptCookie(true)
        setContent {
            BitTheme {
                var loading by remember { mutableStateOf(true) }
                BackHandler { if (web?.canGoBack() == true) web?.goBack() else finish() }
                Column(Modifier.fillMaxSize().backdrop()) {
                    Header("Sign in", "Your YouTube account", onBack = { finish() })
                    Box(
                        Modifier.fillMaxWidth().weight(1f).navigationBarsPadding().padding(start = 10.dp, end = 10.dp, bottom = 10.dp)
                            .clip(RoundedCornerShape(30.dp)).background(Bit.surface),
                    ) {
                        AndroidView(
                            factory = { ctx ->
                                WebView(ctx).also { w ->
                                    web = w
                                    cm.setAcceptThirdPartyCookies(w, true)
                                    w.settings.javaScriptEnabled = true
                                    w.settings.domStorageEnabled = true
                                    w.webViewClient = object : WebViewClient() {
                                        override fun onPageFinished(view: WebView?, url: String?) {
                                            super.onPageFinished(view, url)
                                            loading = false
                                            if (url != null && (url.startsWith("https://www.youtube.com") || url.startsWith("https://m.youtube.com"))) {
                                                val cookies = cm.getCookie("https://www.youtube.com")
                                                if (cookies != null && cookies.contains("SAPISID=")) {
                                                    cm.flush()
                                                    signedIn(cookies)
                                                }
                                            }
                                        }
                                    }
                                    w.loadUrl(Account.LOGIN_URL)
                                }
                            },
                            modifier = Modifier.fillMaxSize(),
                        )
                        if (loading) CookieLoader(Modifier.align(Alignment.Center))
                    }
                }
            }
        }
    }

    private fun signedIn(cookies: String) {
        if (done) return
        done = true
        Account.saveCookies(cookies)
        // Profile + subscriptions come in the background; the app keeps working meanwhile.
        (application as BitTubeApp).appScope.launch {
            Account.refreshProfile()
            runCatching { Account.channels() }.getOrNull()?.let { Library.importSubscriptions(it) }
        }
        finish()
    }

    override fun onDestroy() {
        web?.destroy()
        web = null
        super.onDestroy()
    }
}
