package com.cinemode.tv

import android.annotation.SuppressLint
import android.app.Activity
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.os.SystemClock
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import java.io.ByteArrayInputStream
import kotlin.math.min

class MainActivity : Activity() {

    // ---- Settings you can change -------------------------------------------
    private val startUrl = "https://cinemode.fun/"
    private val liteMode = true      // true = turn off blur/animations for smoother playback
    private val adShield = true      // true = block ad networks and unrequested pop-up overlays
    // -------------------------------------------------------------------------

    private val allowedHost = Uri.parse(startUrl).host ?: ""

    private lateinit var container: FrameLayout
    private lateinit var webView: WebView
    private lateinit var cursor: CursorView
    private val handler = Handler(Looper.getMainLooper())
    private val hideCursor = Runnable { cursor.visibility = View.INVISIBLE }

    private var customView: View? = null
    private var customCallback: WebChromeClient.CustomViewCallback? = null

    // Hosts containing any of these words are blocked (common ad networks).
    private val blockedWords = listOf(
        "doubleclick", "googlesyndication", "googleadservices", "adservice",
        "popads", "popcash", "propellerads", "adsterra", "exoclick", "juicyads",
        "trafficjunky", "clickadu", "hilltopads", "onclickads", "adcash",
        "richpush", "a-ads", "coinzilla", "bidvertiser", "mgid", "taboola",
        "outbrain", "revcontent", "adnxs", "adsco.re", "tsyndicate",
        "highperformanceformat", "profitablecpmrate", "effectivegatecpm",
        "adskeeper", "clickaine", "monetag", "pubfuture", "admaven",
        "adspyglass", "trafficstars", "ad-maven", "popunder", "pushwoosh"
    )

    // Removes ad overlays that appear with no user input, blocks window.open,
    // and (optionally) strips heavy visual effects.
    private val shieldJs = """
(function(){
  if (window.__shield) return; window.__shield = true;
  try { window.open = function(){ return null; }; } catch(e) {}
  var HOST = location.hostname;
  var LITE = __LITE__;
  var SHIELD = __SHIELD__;
  if (LITE) {
    var st = document.createElement('style');
    st.textContent = '*,*::before,*::after{backdrop-filter:none!important;-webkit-backdrop-filter:none!important;animation-duration:.001s!important;animation-iteration-count:1!important;transition-duration:.001s!important;scroll-behavior:auto!important}';
    (document.head || document.documentElement).appendChild(st);
  }
  if (!SHIELD) return;
  var last = 0;
  ['click','touchstart','touchend','keydown','mousedown','pointerdown'].forEach(function(e){
    window.addEventListener(e, function(ev){ if (ev.isTrusted) last = Date.now(); }, true);
  });
  function ext(u){
    try { var h = new URL(u, location.href).hostname; return h !== HOST && !h.endsWith('.' + HOST); }
    catch(e) { return false; }
  }
  function bad(el){
    var s = getComputedStyle(el);
    if (s.position !== 'fixed' && s.position !== 'absolute') return false;
    if (el.tagName === 'VIDEO' || el.querySelector('video')) return false;
    var z = parseInt(s.zIndex) || 0;
    if (z >= 2000000000) return true;
    var r = el.getBoundingClientRect();
    var big = r.width * r.height > 0.08 * innerWidth * innerHeight;
    if (z >= 1000 && big) {
      var fr = el.tagName === 'IFRAME' ? el : el.querySelector('iframe');
      if (fr && ext(fr.src)) return true;
      var a = el.querySelectorAll('a[href]');
      for (var i = 0; i < a.length; i++) if (ext(a[i].href)) return true;
    }
    return false;
  }
  function check(n){
    if (!(n instanceof HTMLElement)) return;
    setTimeout(function(){
      if (!n.isConnected) return;
      if (Date.now() - last < 2500) return;   // user just did something: leave it alone
      if (bad(n)) n.remove();
    }, 150);
  }
  function watch(t){
    new MutationObserver(function(ms){
      ms.forEach(function(m){ m.addedNodes.forEach(check); });
    }).observe(t, { childList: true });
  }
  watch(document.documentElement);
  if (document.body) watch(document.body);
})();
"""

    private val scrollJs = """
(function(x,y,dx,dy){
  var el = document.elementFromPoint(x,y);
  while (el && el !== document.body && el !== document.documentElement) {
    var s = getComputedStyle(el);
    var cy = /(auto|scroll)/.test(s.overflowY) && el.scrollHeight > el.clientHeight;
    var cx = /(auto|scroll)/.test(s.overflowX) && el.scrollWidth > el.clientWidth;
    if ((dy && cy) || (dx && cx)) { el.scrollBy(dx,dy); return; }
    el = el.parentElement;
  }
  window.scrollBy(dx,dy);
})(__X__,__Y__,__DX__,__DY__);
"""

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        WebView.setWebContentsDebuggingEnabled(true)

        container = FrameLayout(this).apply { setBackgroundColor(Color.BLACK) }
        webView = WebView(this).apply {
            setBackgroundColor(Color.BLACK)
            setLayerType(View.LAYER_TYPE_HARDWARE, null)
            isFocusable = true
            isFocusableInTouchMode = true
        }
        val match = ViewGroup.LayoutParams.MATCH_PARENT
        container.addView(webView, FrameLayout.LayoutParams(match, match))

        cursor = CursorView(this).apply {
            cx = resources.displayMetrics.widthPixels / 2f
            cy = resources.displayMetrics.heightPixels / 2f
        }
        container.addView(cursor, FrameLayout.LayoutParams(match, match))
        setContentView(container)
        showCursor()

        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            mediaPlaybackRequiresUserGesture = false
            loadWithOverviewMode = true
            useWideViewPort = true
            offscreenPreRaster = true
            setSupportMultipleWindows(true)
            javaScriptCanOpenWindowsAutomatically = false
            mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
        }
        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true)

        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(
                view: WebView, request: WebResourceRequest
            ): Boolean {
                val host = request.url.host ?: return true
                val ok = host == allowedHost || host.endsWith(".$allowedHost")
                return !ok
            }

            override fun shouldInterceptRequest(
                view: WebView, request: WebResourceRequest
            ): WebResourceResponse? {
                if (adShield) {
                    val host = request.url.host?.lowercase() ?: return null
                    if (host != allowedHost && blockedWords.any { host.contains(it) }) {
                        return WebResourceResponse(
                            "text/plain", "utf-8", ByteArrayInputStream(ByteArray(0))
                        )
                    }
                }
                return null
            }

            override fun onPageFinished(view: WebView, url: String?) {
                val js = shieldJs
                    .replace("__LITE__", liteMode.toString())
                    .replace("__SHIELD__", adShield.toString())
                view.evaluateJavascript(js, null)
            }
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onCreateWindow(
                view: WebView, isDialog: Boolean, isUserGesture: Boolean, resultMsg: Message?
            ): Boolean = false

            override fun onShowCustomView(view: View, callback: CustomViewCallback) {
                if (customView != null) {
                    callback.onCustomViewHidden()
                    return
                }
                customView = view
                customCallback = callback
                webView.visibility = View.GONE
                cursor.visibility = View.GONE
                container.addView(view, FrameLayout.LayoutParams(match, match))
            }

            override fun onHideCustomView() {
                customView?.let { container.removeView(it) }
                customView = null
                customCallback?.onCustomViewHidden()
                customCallback = null
                webView.visibility = View.VISIBLE
                webView.requestFocus()
                showCursor()
            }
        }

        if (savedInstanceState == null) {
            webView.loadUrl(startUrl)
        } else {
            webView.restoreState(savedInstanceState)
        }
        webView.requestFocus()
    }

    // ---- Remote control: D-pad moves the cursor, OK clicks ------------------

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (customView != null) return super.dispatchKeyEvent(event)

        val code = event.keyCode
        when (code) {
            KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN,
            KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT -> {
                if (event.action == KeyEvent.ACTION_DOWN) {
                    val base = resources.displayMetrics.widthPixels / 160f
                    val step = base * (1f + min(event.repeatCount, 15) * 0.35f)
                    var dx = 0f
                    var dy = 0f
                    when (code) {
                        KeyEvent.KEYCODE_DPAD_UP -> dy = -step
                        KeyEvent.KEYCODE_DPAD_DOWN -> dy = step
                        KeyEvent.KEYCODE_DPAD_LEFT -> dx = -step
                        else -> dx = step
                    }
                    moveCursor(dx, dy)
                }
                return true
            }
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER,
            KeyEvent.KEYCODE_NUMPAD_ENTER -> {
                if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
                    showCursor()
                    click()
                }
                return true
            }
            KeyEvent.KEYCODE_MENU -> {
                if (event.action == KeyEvent.ACTION_DOWN) webView.reload()
                return true
            }
        }
        return super.dispatchKeyEvent(event)
    }

    private fun moveCursor(dx: Float, dy: Float) {
        showCursor()
        val w = container.width.toFloat().takeIf { it > 0 } ?: resources.displayMetrics.widthPixels.toFloat()
        val h = container.height.toFloat().takeIf { it > 0 } ?: resources.displayMetrics.heightPixels.toFloat()
        val edge = h / 14f
        val density = resources.displayMetrics.density

        // Pushing against a screen edge scrolls the page under the cursor.
        var sx = 0
        var sy = 0
        val scrollStep = (Math.abs(dx) + Math.abs(dy)) * 6f / density
        if (dy < 0 && cursor.cy <= edge) sy = -scrollStep.toInt()
        if (dy > 0 && cursor.cy >= h - edge) sy = scrollStep.toInt()
        if (dx < 0 && cursor.cx <= edge) sx = -scrollStep.toInt()
        if (dx > 0 && cursor.cx >= w - edge) sx = scrollStep.toInt()
        if (sx != 0 || sy != 0) {
            val js = scrollJs
                .replace("__X__", (cursor.cx / density).toInt().toString())
                .replace("__Y__", (cursor.cy / density).toInt().toString())
                .replace("__DX__", sx.toString())
                .replace("__DY__", sy.toString())
            webView.evaluateJavascript(js, null)
        }

        cursor.cx = (cursor.cx + dx).coerceIn(0f, w - 1f)
        cursor.cy = (cursor.cy + dy).coerceIn(0f, h - 1f)
        cursor.invalidate()
    }

    private fun click() {
        val x = cursor.cx
        val y = cursor.cy
        val t = SystemClock.uptimeMillis()
        val down = MotionEvent.obtain(t, t, MotionEvent.ACTION_DOWN, x, y, 0)
        down.source = InputDevice.SOURCE_TOUCHSCREEN
        webView.dispatchTouchEvent(down)
        down.recycle()
        handler.postDelayed({
            val up = MotionEvent.obtain(t, SystemClock.uptimeMillis(), MotionEvent.ACTION_UP, x, y, 0)
            up.source = InputDevice.SOURCE_TOUCHSCREEN
            webView.dispatchTouchEvent(up)
            up.recycle()
        }, 60)
    }

    private fun showCursor() {
        cursor.visibility = View.VISIBLE
        handler.removeCallbacks(hideCursor)
        handler.postDelayed(hideCursor, 6000)
    }

    // ---- Lifecycle ----------------------------------------------------------

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        when {
            customView != null -> webView.webChromeClient?.onHideCustomView()
            webView.canGoBack() -> webView.goBack()
            else -> super.onBackPressed()
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        webView.saveState(outState)
    }

    override fun onPause() { super.onPause(); webView.onPause() }
    override fun onResume() { super.onResume(); webView.onResume() }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        container.removeAllViews()
        webView.destroy()
        super.onDestroy()
    }
}
