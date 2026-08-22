package de.kern.reisekosten

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

/**
 * Einziger Bildschirm der App: ein WebView, der die lokale index.html
 * aus den Assets lädt. Es wird nichts aus dem Netz nachgeladen.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView

    // Muss unbedingt auf Feldebene registriert werden – eine Registrierung
    // nach onCreate() löst eine IllegalStateException aus.
    private val legacyStoragePermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* Ablehnung ist unkritisch */ }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        webView = WebView(this).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )

            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true          // localStorage / IndexedDB
                databaseEnabled = true

                allowFileAccess = true            // Zugriff auf file:///android_asset
                allowContentAccess = true

                // Die Seite bringt ihr eigenes Viewport-Meta mit.
                loadWithOverviewMode = false
                useWideViewPort = false
                builtInZoomControls = false
                displayZoomControls = false

                // Rein lokal – kein Netzwerkzugriff nötig oder erwünscht.
                cacheMode = WebSettings.LOAD_NO_CACHE
                blockNetworkLoads = true
                mediaPlaybackRequiresUserGesture = true
            }

            // Alles bleibt im WebView; nichts wird an einen Browser abgegeben.
            webViewClient = WebViewClient()
            webChromeClient = WebChromeClient()

            addJavascriptInterface(FileExportBridge(this@MainActivity), "AndroidExport")
        }

        setContentView(webView)

        // Zurück-Taste blättert erst durch die WebView-History.
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (webView.canGoBack()) {
                    webView.goBack()
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        })

        requestLegacyStoragePermissionIfNeeded()

        if (savedInstanceState == null) {
            webView.loadUrl("file:///android_asset/index.html")
        }
    }

    /**
     * Bis Android 9 braucht das Schreiben in den öffentlichen Downloads-Ordner
     * eine Berechtigung. Ab Android 10 übernimmt das der MediaStore ohne Nachfrage.
     * Wird die Berechtigung abgelehnt, weicht der Export auf den app-eigenen
     * Ordner aus – Teilen funktioniert weiterhin.
     */
    private fun requestLegacyStoragePermissionIfNeeded() {
        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.P) return

        val permission = Manifest.permission.WRITE_EXTERNAL_STORAGE
        if (ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED) return

        legacyStoragePermission.launch(permission)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        webView.saveState(outState)
    }

    override fun onRestoreInstanceState(savedInstanceState: Bundle) {
        super.onRestoreInstanceState(savedInstanceState)
        webView.restoreState(savedInstanceState)
    }

    override fun onDestroy() {
        webView.destroy()
        super.onDestroy()
    }
}
