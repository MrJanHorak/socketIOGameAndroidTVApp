package com.partygame.sciencefair.tv

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.text.InputType
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.webkit.JavascriptInterface
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import kotlin.concurrent.thread

/**
 * A thin Android TV shell around the Science Fair host screen.
 *
 * It remembers the game server address, opens `<server>/tv` full screen (which
 * creates a room and shows the lobby + QR code), lets the page play audio
 * without a tap, keeps the TV awake, and gives the page Android's own
 * text-to-speech because Android WebView has no Web Speech API.
 */
class MainActivity : Activity() {

    private lateinit var root: FrameLayout
    private lateinit var webView: WebView
    private lateinit var setupView: LinearLayout
    private lateinit var addressInput: EditText
    private lateinit var statusText: TextView
    private lateinit var resumeButton: Button
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var gameLoaded = false

    private val prefs by lazy { getSharedPreferences("science_fair_tv", Context.MODE_PRIVATE) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        root = FrameLayout(this).apply { setBackgroundColor(Color.parseColor("#183f38")) }
        webView = createWebView()
        setupView = createSetupView()
        root.addView(webView, FrameLayout.LayoutParams(MATCH, MATCH))
        root.addView(setupView, FrameLayout.LayoutParams(MATCH, MATCH))
        setContentView(root)
        hideSystemBars()

        tts = TextToSpeech(this) { status ->
            ttsReady = status == TextToSpeech.SUCCESS
            if (ttsReady) {
                tts?.setLanguage(Locale.US)
                tts?.setOnUtteranceProgressListener(utteranceListener)
                runOnUiThread { webView.evaluateJavascript("window.__androidTtsVoicesReady && window.__androidTtsVoicesReady()", null) }
            }
        }

        val saved = prefs.getString(KEY_SERVER, null)
        if (saved.isNullOrBlank()) showSetup(null) else connect(saved)
    }

    // ---------------------------------------------------------------- WebView

    @SuppressLint("SetJavaScriptEnabled")
    private fun createWebView(): WebView = WebView(this).apply {
        setBackgroundColor(Color.parseColor("#183f38"))
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.mediaPlaybackRequiresUserGesture = false
        settings.cacheMode = android.webkit.WebSettings.LOAD_DEFAULT
        isFocusable = true
        isFocusableInTouchMode = true
        addJavascriptInterface(TtsBridge(), "AndroidTts")
        webViewClient = object : WebViewClient() {
            override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && request.isForMainFrame) {
                    showSetup("Couldn't reach the game server (${error.description}). Is it running on your computer?")
                }
            }

            @Deprecated("Deprecated in Java")
            override fun onReceivedError(view: WebView, errorCode: Int, description: String?, failingUrl: String?) {
                // Called on Android 5.x only, and only for the main page.
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
                    showSetup("Couldn't reach the game server ($description). Is it running on your computer?")
                }
            }
        }
    }

    private fun connect(rawAddress: String) {
        val server = normalizeAddress(rawAddress)
        showStatus("Connecting to $server …")
        thread {
            val ok = try {
                (URL("$server/health").openConnection() as HttpURLConnection).run {
                    connectTimeout = 4000
                    readTimeout = 4000
                    val code = responseCode
                    disconnect()
                    code == 200
                }
            } catch (ignored: Exception) {
                false
            }
            runOnUiThread {
                if (ok) {
                    prefs.edit().putString(KEY_SERVER, server).apply()
                    gameLoaded = true
                    webView.loadUrl("$server/tv")
                    setupView.visibility = View.GONE
                    webView.visibility = View.VISIBLE
                    webView.requestFocus()
                } else {
                    showSetup("No game server answered at $server.\nOn your computer, run  npm run play  in the frontend folder and use the \"On your network\" address it prints.")
                }
            }
        }
    }

    private fun normalizeAddress(raw: String): String {
        var address = raw.trim().trimEnd('/')
        if (!address.startsWith("http://") && !address.startsWith("https://")) address = "http://$address"
        // Default to the game server port when only an IP or host name was typed.
        val hostPart = address.substringAfter("://")
        if (!hostPart.contains(':')) address = "$address:3000"
        return address
    }

    // ------------------------------------------------------------ Setup screen

    private fun createSetupView(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER
        setPadding(dp(64), dp(32), dp(64), dp(32))
        setBackgroundColor(Color.parseColor("#183f38"))

        addView(text("🧪 Science Fair Showdown", 34f, bold = true))
        addView(text("Enter the game server address shown on your computer, e.g. 192.168.1.20:3000", 18f).apply {
            setPadding(0, dp(12), 0, dp(20))
        })

        addressInput = EditText(context).apply {
            setSingleLine()
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
            imeOptions = EditorInfo.IME_ACTION_GO
            textSize = 24f
            setTextColor(Color.parseColor("#183f38"))
            setBackgroundColor(Color.parseColor("#fff5d9"))
            setPadding(dp(16), dp(10), dp(16), dp(10))
            setText(prefs.getString(KEY_SERVER, null) ?: "http://192.168.1.")
            setOnEditorActionListener { _, actionId, _ ->
                if (actionId == EditorInfo.IME_ACTION_GO || actionId == EditorInfo.IME_ACTION_DONE) {
                    connect(text.toString()); true
                } else false
            }
        }
        addView(addressInput, LinearLayout.LayoutParams(dp(560), ViewGroup.LayoutParams.WRAP_CONTENT))

        val buttons = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(0, dp(20), 0, dp(12))
        }
        buttons.addView(button("Connect") { connect(addressInput.text.toString()) })
        resumeButton = button("Back to game") {
            setupView.visibility = View.GONE
            webView.visibility = View.VISIBLE
            webView.requestFocus()
        }
        buttons.addView(resumeButton)
        buttons.addView(button("New room") {
            prefs.getString(KEY_SERVER, null)?.let { connect(it) }
        })
        addView(buttons)

        statusText = text("", 16f).apply { setTextColor(Color.parseColor("#ffd6c4")) }
        addView(statusText)
        addView(text("Tip: press Back during the game to return here.", 14f).apply {
            alpha = 0.7f
            setPadding(0, dp(24), 0, 0)
        })
    }

    private fun showSetup(message: String?) {
        statusText.text = message ?: ""
        resumeButton.visibility = if (gameLoaded) View.VISIBLE else View.GONE
        setupView.visibility = View.VISIBLE
        (if (gameLoaded) resumeButton else addressInput).requestFocus()
    }

    private fun showStatus(message: String) {
        statusText.text = message
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK || keyCode == KeyEvent.KEYCODE_MENU) {
            if (setupView.visibility == View.VISIBLE) {
                if (gameLoaded && keyCode == KeyEvent.KEYCODE_BACK) {
                    setupView.visibility = View.GONE
                    webView.requestFocus()
                    return true
                }
                return super.onKeyDown(keyCode, event) // Back on the setup screen exits the app.
            }
            showSetup(null)
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    // ------------------------------------------------------- Text-to-speech

    private val utteranceListener = object : UtteranceProgressListener() {
        override fun onStart(utteranceId: String?) {}
        override fun onDone(utteranceId: String?) = notifyPage("__androidTtsDone", utteranceId)
        @Deprecated("Deprecated in Java")
        override fun onError(utteranceId: String?) = notifyPage("__androidTtsError", utteranceId)
        override fun onStop(utteranceId: String?, interrupted: Boolean) = notifyPage("__androidTtsDone", utteranceId)
    }

    private fun notifyPage(callback: String, utteranceId: String?) {
        val id = utteranceId?.filter { it.isDigit() } ?: return
        runOnUiThread { webView.evaluateJavascript("window.$callback && window.$callback('$id')", null) }
    }

    /** Exposed to the page as window.AndroidTts; see src/tvBridge.js in the frontend. */
    inner class TtsBridge {
        @JavascriptInterface
        fun speak(id: String, text: String, rate: Double, pitch: Double, volume: Double, voiceName: String) {
            val engine = tts
            if (!ttsReady || engine == null) {
                notifyPage("__androidTtsError", id); return
            }
            engine.setSpeechRate(rate.toFloat().coerceIn(0.5f, 2f))
            engine.setPitch(pitch.toFloat().coerceIn(0.5f, 2f))
            if (voiceName.isNotBlank() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                engine.voices?.firstOrNull { it.name == voiceName }?.let { engine.setVoice(it) }
            }
            val params = Bundle().apply { putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, volume.toFloat().coerceIn(0f, 1f)) }
            engine.speak(text.take(TextToSpeech.getMaxSpeechInputLength()), TextToSpeech.QUEUE_ADD, params, id)
        }

        @JavascriptInterface
        fun cancel() {
            tts?.stop()
        }

        @JavascriptInterface
        fun voicesJson(): String {
            val engine = tts ?: return "[]"
            if (!ttsReady) return "[]"
            val defaultName = try { engine.defaultVoice?.name } catch (ignored: Exception) { null }
            val list = JSONArray()
            try {
                engine.voices
                    ?.filter { it.locale.language == "en" && !it.isNetworkConnectionRequired }
                    ?.sortedBy { it.name }
                    ?.take(30)
                    ?.forEach { voice ->
                        list.put(JSONObject().put("name", voice.name).put("lang", voice.locale.toLanguageTag()).put("default", voice.name == defaultName))
                    }
            } catch (ignored: Exception) {
                // Some engines throw while still loading voices.
            }
            return list.toString()
        }
    }

    // ------------------------------------------------------------ Lifecycle

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemBars()
    }

    override fun onResume() {
        super.onResume()
        webView.onResume()
    }

    override fun onPause() {
        webView.onPause()
        super.onPause()
    }

    override fun onDestroy() {
        tts?.shutdown()
        webView.destroy()
        super.onDestroy()
    }

    @Suppress("DEPRECATION")
    private fun hideSystemBars() {
        window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_FULLSCREEN
            or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
            or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
            or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
            or View.SYSTEM_UI_FLAG_LAYOUT_STABLE)
    }

    // ---------------------------------------------------------------- Helpers

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    private fun text(value: String, size: Float, bold: Boolean = false) = TextView(this).apply {
        text = value
        textSize = size
        gravity = Gravity.CENTER
        setTextColor(Color.parseColor("#fff5d9"))
        if (bold) setTypeface(typeface, Typeface.BOLD)
    }

    private fun button(label: String, onClick: () -> Unit) = Button(this).apply {
        text = label
        textSize = 18f
        isFocusable = true
        setOnClickListener { onClick() }
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            setMargins(dp(8), 0, dp(8), 0)
        }
    }

    companion object {
        private const val KEY_SERVER = "server_url"
        private const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
    }
}
