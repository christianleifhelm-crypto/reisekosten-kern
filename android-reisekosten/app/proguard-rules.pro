# Die JS-Brücke wird ausschließlich aus JavaScript heraus aufgerufen –
# ohne diese Regel entfernt R8 die Methoden im Release-Build.
-keepclassmembers class de.kern.reisekosten.FileExportBridge {
    @android.webkit.JavascriptInterface <methods>;
}
-keepclassmembers class de.kern.reisekosten.LocationBridge {
    @android.webkit.JavascriptInterface <methods>;
}
