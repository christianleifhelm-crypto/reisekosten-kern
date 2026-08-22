package de.kern.reisekosten

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Address
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import androidx.core.os.CancellationSignal
import org.json.JSONObject
import java.util.Locale
import java.util.concurrent.Executors

/**
 * Einziger Bildschirm der App: ein WebView, der die lokale index.html
 * aus den Assets lädt. Es wird nichts aus dem Netz nachgeladen.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private val worker = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())

    // Muss unbedingt auf Feldebene registriert werden – eine Registrierung
    // nach onCreate() löst eine IllegalStateException aus.
    private val legacyStoragePermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* Ablehnung ist unkritisch */ }

    private val locationPermission =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { granted ->
            if (granted.values.any { it }) {
                lookupCity()
            } else {
                postCityResult(null, "Standortfreigabe wurde abgelehnt.")
            }
        }

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

                // Rein lokal – der WebView selbst darf nicht ins Netz.
                // Die Ortsauflösung läuft nativ über den Geocoder.
                cacheMode = WebSettings.LOAD_NO_CACHE
                blockNetworkLoads = true
                mediaPlaybackRequiresUserGesture = true
            }

            // Alles bleibt im WebView; nichts wird an einen Browser abgegeben.
            webViewClient = WebViewClient()
            webChromeClient = WebChromeClient()

            addJavascriptInterface(FileExportBridge(this@MainActivity), "AndroidExport")
            addJavascriptInterface(LocationBridge(this@MainActivity), "AndroidLocation")
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

    // ────────────────────────── Standort ──────────────────────────

    fun isGeocoderAvailable(): Boolean = Geocoder.isPresent()

    /** Einstiegspunkt aus dem WebView: Berechtigung prüfen, dann ermitteln. */
    fun resolveCity() {
        val fine = Manifest.permission.ACCESS_FINE_LOCATION
        val coarse = Manifest.permission.ACCESS_COARSE_LOCATION

        val hasFine = ContextCompat.checkSelfPermission(this, fine) == PackageManager.PERMISSION_GRANTED
        val hasCoarse = ContextCompat.checkSelfPermission(this, coarse) == PackageManager.PERMISSION_GRANTED

        if (hasFine || hasCoarse) {
            lookupCity()
        } else {
            locationPermission.launch(arrayOf(fine, coarse))
        }
    }

    @SuppressLint("MissingPermission")
    private fun lookupCity() {
        val lm = getSystemService(Context.LOCATION_SERVICE) as? LocationManager
        if (lm == null) {
            postCityResult(null, "Standortdienst nicht verfügbar.")
            return
        }

        val provider = when {
            LocationManagerCompat.hasProvider(lm, LocationManager.GPS_PROVIDER) -> LocationManager.GPS_PROVIDER
            LocationManagerCompat.hasProvider(lm, LocationManager.NETWORK_PROVIDER) -> LocationManager.NETWORK_PROVIDER
            else -> null
        }
        if (provider == null) {
            postCityResult(null, "Kein Standortanbieter aktiv. Bitte Standort einschalten.")
            return
        }

        try {
            LocationManagerCompat.getCurrentLocation(
                lm, provider, CancellationSignal(), worker
            ) { location ->
                if (location == null) {
                    // Letzte bekannte Position als Rückfallebene
                    val last = try { lm.getLastKnownLocation(provider) } catch (e: SecurityException) { null }
                    if (last == null) {
                        postCityResult(null, "Standort konnte nicht ermittelt werden.")
                    } else {
                        geocode(last)
                    }
                } else {
                    geocode(location)
                }
            }
        } catch (e: SecurityException) {
            postCityResult(null, "Standortfreigabe fehlt.")
        } catch (e: Exception) {
            postCityResult(null, e.message ?: "Standortfehler")
        }
    }

    private fun geocode(loc: Location) {
        if (!Geocoder.isPresent()) {
            postCityResult(null, "Ortsauflösung auf diesem Gerät nicht verfügbar.")
            return
        }
        val geocoder = Geocoder(this, Locale.GERMANY)

        if (Build.VERSION.SDK_INT >= 33) {
            geocoder.getFromLocation(loc.latitude, loc.longitude, 1, object : Geocoder.GeocodeListener {
                override fun onGeocode(addresses: MutableList<Address>) {
                    postCityResult(cityFrom(addresses.firstOrNull()), null)
                }
                override fun onError(errorMessage: String?) {
                    postCityResult(null, errorMessage ?: "Ortsauflösung fehlgeschlagen.")
                }
            })
        } else {
            worker.execute {
                try {
                    @Suppress("DEPRECATION")
                    val addresses = geocoder.getFromLocation(loc.latitude, loc.longitude, 1)
                    postCityResult(cityFrom(addresses?.firstOrNull()), null)
                } catch (e: Exception) {
                    postCityResult(null, "Ortsauflösung fehlgeschlagen (keine Verbindung?).")
                }
            }
        }
    }

    private fun cityFrom(a: Address?): String? {
        if (a == null) return null
        return a.locality
            ?: a.subAdminArea
            ?: a.subLocality
            ?: a.adminArea
    }

    /** Ergebnis in die Seite zurückgeben: window.onGpsCity(ort, fehler) */
    private fun postCityResult(city: String?, error: String?) {
        val payload = JSONObject()
            .put("city", city ?: JSONObject.NULL)
            .put("error", (error ?: if (city == null) "Kein Ortsname gefunden." else null) ?: JSONObject.NULL)
            .toString()

        main.post {
            webView.evaluateJavascript(
                "window.onGpsCity && window.onGpsCity($payload);", null
            )
        }
    }

    // ──────────────────────────────────────────────────────────────

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        webView.saveState(outState)
    }

    override fun onRestoreInstanceState(savedInstanceState: Bundle) {
        super.onRestoreInstanceState(savedInstanceState)
        webView.restoreState(savedInstanceState)
    }

    override fun onDestroy() {
        worker.shutdownNow()
        webView.destroy()
        super.onDestroy()
    }
}
