package com.margin.reader

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.webkit.JavascriptInterface
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.browser.customtabs.CustomTabsIntent
import androidx.webkit.WebViewAssetLoader
import org.json.JSONObject

class MainActivity : ComponentActivity(), TextToSpeech.OnInitListener {

    private lateinit var web: WebView
    private var tts: TextToSpeech? = null
    @Volatile private var ttsReady = false
    private var fileCallback: ValueCallback<Array<Uri>>? = null
    private var pageLoaded = false
    private val pending = ArrayDeque<String>()
    private var lastClip: String? = null

    private val pickPdf = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        fileCallback?.onReceiveValue(if (uri != null) arrayOf(uri) else null)
        fileCallback = null
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        tts = TextToSpeech(this, this)

        // Serves app/src/main/assets at https://appassets.androidplatform.net/assets/ so the
        // page runs from a secure origin (needed for workers, WebAssembly and IndexedDB).
        val loader = WebViewAssetLoader.Builder()
            .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(this))
            .build()

        web = WebView(this)
        setContentView(web)
        web.settings.javaScriptEnabled = true
        web.settings.domStorageEnabled = true
        web.settings.allowFileAccess = false
        web.settings.mediaPlaybackRequiresUserGesture = false

        web.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, url: String?) {
                pageLoaded = true
                flushPending()
            }

            override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? =
                loader.shouldInterceptRequest(request.url)

            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                if (request.url.host == "appassets.androidplatform.net") return false
                openExternal(request.url.toString())
                return true
            }
        }

        web.webChromeClient = object : WebChromeClient() {
            override fun onShowFileChooser(
                view: WebView,
                callback: ValueCallback<Array<Uri>>,
                params: FileChooserParams
            ): Boolean {
                fileCallback?.onReceiveValue(null)
                fileCallback = callback
                return try {
                    pickPdf.launch(arrayOf("application/pdf"))
                    true
                } catch (e: Exception) {
                    fileCallback = null
                    callback.onReceiveValue(null)
                    false
                }
            }
        }

        web.addJavascriptInterface(Bridge(), "Native")

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                web.evaluateJavascript("(window.nativeBack && window.nativeBack()) ? 'y' : 'n'") { result ->
                    if (result == null || !result.contains("y")) finish()
                }
            }
        })

        handleIntent(intent)
        web.loadUrl("https://appassets.androidplatform.net/assets/www/index.html")
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    // Results from Margin in Claude arrive as margin://result?d=<packed>.
    private fun handleIntent(intent: Intent?) {
        val data = intent?.data ?: return
        if (data.scheme != "margin") return
        data.getQueryParameter("d")?.let { deliver(it) }
    }

    // Fallback: the artifact also copies the result as "MARGIN-APP-1:<packed>"; import it when the app is foregrounded.
    override fun onResume() {
        super.onResume()
        web.post {
            try {
                val cm = getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager
                val text = cm.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.text?.toString()?.trim()
                if (text != null && text.startsWith("MARGIN-APP-1:") && text != lastClip) {
                    lastClip = text
                    deliver(text.removePrefix("MARGIN-APP-1:"))
                }
            } catch (e: Exception) { }
        }
    }

    private fun deliver(packed: String) {
        pending.addLast(packed)
        flushPending()
    }

    private fun flushPending() {
        if (!pageLoaded) return
        while (pending.isNotEmpty()) {
            val quoted = JSONObject.quote(pending.removeFirst())
            web.evaluateJavascript("window.__fromClaude && window.__fromClaude($quoted)", null)
        }
    }

    override fun onInit(status: Int) {
        ttsReady = status == TextToSpeech.SUCCESS
        if (!ttsReady) return
        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {}
            override fun onDone(utteranceId: String?) = finished(utteranceId, true)
            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) = finished(utteranceId, false)
            override fun onError(utteranceId: String?, errorCode: Int) = finished(utteranceId, false)
            override fun onStop(utteranceId: String?, interrupted: Boolean) = finished(utteranceId, false)
        })
    }

    private fun finished(id: String?, ok: Boolean) {
        val quoted = JSONObject.quote(id ?: "")
        runOnUiThread { web.evaluateJavascript("window.__ttsDone && window.__ttsDone($quoted, $ok)", null) }
    }

    // Opens Margin in Claude in an in-app browser sheet that shares the browser's claude.ai sign-in.
    // Closing the sheet returns to the same page in this app.
    private fun openExternal(url: String) {
        runOnUiThread {
            val uri = Uri.parse(url)
            try {
                CustomTabsIntent.Builder()
                    .setShowTitle(true)
                    .setShareState(CustomTabsIntent.SHARE_STATE_OFF)
                    .build()
                    .launchUrl(this, uri)
            } catch (e: Exception) {
                try { startActivity(Intent(Intent.ACTION_VIEW, uri)) } catch (e2: Exception) { }
            }
        }
    }

    inner class Bridge {
        @JavascriptInterface
        fun ttsAvailable(): Boolean = ttsReady

        @JavascriptInterface
        fun speak(text: String, rate: Float, id: String) {
            val engine = tts
            if (engine == null || !ttsReady) { finished(id, false); return }
            engine.setSpeechRate(rate)
            val result = engine.speak(text, TextToSpeech.QUEUE_ADD, Bundle(), id)
            if (result != TextToSpeech.SUCCESS) finished(id, false)
        }

        @JavascriptInterface
        fun stopSpeaking() { tts?.stop() }

        @JavascriptInterface
        fun openUrl(url: String) = openExternal(url)
    }

    override fun onDestroy() {
        tts?.stop()
        tts?.shutdown()
        web.destroy()
        super.onDestroy()
    }
}
