package de.kern.reisekosten

import android.webkit.JavascriptInterface

/**
 * Brücke für die Standortermittlung.
 *
 * Aus dem WebView heraus:
 *   if (AndroidLocation && AndroidLocation.isAvailable()) AndroidLocation.requestCity();
 *
 * Das Ergebnis kommt asynchron zurück – die Seite muss dafür
 * window.onGpsCity(ort, fehler) bereitstellen.
 *
 * Die Auflösung der Koordinaten in einen Ortsnamen übernimmt Androids
 * eigener Geocoder. Der WebView bleibt dadurch vollständig vom Netz
 * getrennt, und es wird kein Standort an einen fremden Dienst gesendet.
 */
class LocationBridge(private val activity: MainActivity) {

    @JavascriptInterface
    fun isAvailable(): Boolean = activity.isGeocoderAvailable()

    @JavascriptInterface
    fun requestCity() {
        activity.runOnUiThread { activity.resolveCity() }
    }
}
