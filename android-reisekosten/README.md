# Reisekosten – Android-App

WebView-Wrapper um die Reisekosten-Web-App. Läuft vollständig lokal:
kein Server, kein Login, keine Netzwerkverbindung.

## Öffnen und bauen

In Android Studio **Open** wählen und den Ordner `android-reisekosten`
öffnen. Danach `Run` oder:

```
./gradlew assembleDebug
```

Die APK liegt anschließend unter
`app/build/outputs/apk/debug/app-debug.apk` und lässt sich per Sideload
installieren. Eine Signierung ist dafür nicht nötig.

Gebaut wird außerdem bei jedem Push über GitHub Actions
(`.github/workflows/build-reisekosten-apk.yml`); die fertige APK hängt
dort als Artefakt am Lauf.

## Aufbau

| Datei | Zweck |
|---|---|
| `MainActivity.kt` | WebView, lädt `file:///android_asset/index.html`; Zurück-Taste blättert durch die WebView-History |
| `FileExportBridge.kt` | JS-Brücke `AndroidExport` für den Dateiexport |
| `assets/index.html` | die eigentliche App |
| `assets/jspdf*.js`, `assets/jszip.min.js` | Bibliotheken, lokal eingebunden |
| `assets/nas-shim.js` | optionaler Adapter, siehe unten |

Konfiguration: Kotlin, `minSdk 26`, `targetSdk 34`, `compileSdk 34`.

## Dateiexport

JavaScript ruft die Brücke so auf:

```js
const uri = AndroidExport.saveBase64(dateiname, mimeType, base64);
AndroidExport.shareFile(uri, mimeType);
```

Ab Android 10 schreibt die App über den MediaStore in den öffentlichen
Downloads-Ordner. Bis Android 9 wird – sofern die Berechtigung erteilt
wurde – ebenfalls nach Downloads geschrieben, sonst in den app-eigenen
Ordner; das Teilen funktioniert in beiden Fällen über einen FileProvider.

Läuft die Seite in einem normalen Browser statt in der App, fällt der
Export automatisch auf einen gewöhnlichen Download zurück.

## Fachliche Regeln

- Abrechnungszeitraum vom 21. eines Monats bis zum 20. des Folgemonats
- nur Arbeitstage Montag bis Freitag
- gesetzliche Feiertage in NRW ausgenommen; der **6. Januar ist bewusst
  nicht** enthalten, da Heilige Drei Könige in NRW kein Feiertag ist
- fester Betrag von 7,00 € je Tag
- Datumsangaben werden lokal über `new Date(y, m, d, 12, 0, 0)`
  konstruiert, um Verschiebungen durch UTC zu vermeiden
- die Ortsauswahl erfolgt ausschließlich manuell: Dropdown je Zeile,
  „letzte Stadt übernehmen“ für leere Zeilen, Bulk-Zuweisung auf alle
  Zeilen. Der Export ist gesperrt, solange Tage ohne Ort übrig sind.
- nicht gefahrene Tage werden über `✕` aus der Tabelle entfernt

## Gespeicherte Daten

Alles liegt im `localStorage` des WebViews:

| Schlüssel | Inhalt |
|---|---|
| `rk_cities` | Städteliste |
| `rk_profile` | Name, Firma, Personalnummer, Beginn, Ende |
| `rk_rows_JJJJ-MM` | Zeilen des jeweiligen Monats |
| `rk_done_JJJJ-MM` | Status „Monat abgeschlossen“ |
| `rk_lastcity` | zuletzt verwendeter Ort |

Über den Reiter **Daten** lässt sich alles als JSON sichern und wieder
einlesen.

## Eigene index.html einsetzen

Die mitgelieferte `assets/index.html` wurde anhand der im Repository
vorhandenen Fassung nachgebaut. Wer die NAS-Version verwenden möchte,
ersetzt die Datei und bindet vorher den Adapter ein:

```html
<script src="nas-shim.js"></script>
```

Der Adapter fängt alle `fetch()`-Aufrufe auf `/api/...` ab und bedient
sie aus dem `localStorage`, sodass der bestehende Code unverändert
bleiben kann:

```
GET    /api/reisekosten/rows  ->  liest  localStorage["nas_reisekosten/rows"]
POST   /api/reisekosten/rows  ->  schreibt denselben Schlüssel
DELETE /api/reisekosten/rows  ->  löscht ihn
```

Zusätzlich sind dann noch die Login-Teile (PIN-Abfrage, Rollen,
First-Login) aus der Seite zu entfernen und `doc.save(...)` durch
`deliverBase64(...)` zu ersetzen, damit der Export über die Brücke läuft.
