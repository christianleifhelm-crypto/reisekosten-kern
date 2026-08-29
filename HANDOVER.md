# Übergabe an die lokale Sitzung

Diese Datei fasst zusammen, was bisher gebaut wurde und was noch offen ist.
Die bisherige Arbeit lief in einer Cloud-Sitzung ohne Zugriff auf das NAS und
ohne Android-SDK — daher die Umwege über GitHub Actions. Lokal fallen beide
Einschränkungen weg.

Alles liegt auf dem Branch `claude/app-creation-KHrN8`.

---

## Was im Repository liegt

| Ordner | Inhalt |
|---|---|
| `android-reisekosten/` | **Die aktuelle Android-App.** Kotlin, WebView-Wrapper, minSdk 26, targetSdk 34 |
| `mobile/` | Frühere Capacitor-App (Zeiterfassung, Reisen, Kunden) — eigenständig, unabhängig davon |
| `zeiterfassung/` | Zeiterfassung als Web-App (Netlify Blobs) |
| `index.html` | Die alte Web-Fassung im Repo-Wurzelverzeichnis (Netlify-Backend) |
| `dist/reisekosten.apk` | Zuletzt gebaute APK, dazu `dist/APK-INFO.txt` mit Prüfsumme |

Die eigentliche App steckt in `android-reisekosten/app/src/main/assets/index.html`.

---

## Android-App — Stand

Basis ist die **echte NAS-Fassung** der Reisekosten-App (vom Nutzer hochgeladen),
angepasst für den Einzelbetrieb ohne Server. Markup, CSS und PDF-Layout sind die
originalen.

**Entfernt:** PIN-Login, PIN-Wechsel, Rollen, Admin-Statusübersicht, Sammel-ZIP
über alle Mitarbeiter, Mitarbeiterverwaltung, sämtliche `fetch()`-Aufrufe, der
Zeiterfassungs-Abgleich über `/api/zeit`, die CDN-Verweise.

**Auf localStorage umgestellt:**

| Schlüssel | Inhalt |
|---|---|
| `rka_rows_JJJJ-MM` | Zeilen des jeweiligen Zeitraums |
| `rka_fertig_JJJJ-MM` | Status „Monat abgeschlossen" |
| `rka_cities` | Städteliste |
| `rka_profile` | Name, Firma, Personalnummer, Beginn, Ende |
| `rka_lastcity` | zuletzt verwendeter Ort |

**Native Brücken** (Kotlin, `android-reisekosten/app/src/main/java/de/kern/reisekosten/`):

- `FileExportBridge` → JS-Objekt `AndroidExport`
  `saveBase64(name, mime, base64)` schreibt ab Android 10 über MediaStore nach
  Downloads, darunter über FileProvider; `shareFile(uri, mime)` öffnet die
  Teilen-Auswahl.
- `LocationBridge` → JS-Objekt `AndroidLocation`
  `requestCity()` ermittelt den Standort und löst den Ortsnamen über Androids
  eigenen `Geocoder` auf. Ergebnis kommt asynchron über `window.onGpsCity({city,
  error})` zurück. Bewusst nativ, damit `blockNetworkLoads` an bleiben kann und
  keine Koordinaten an Dritte gehen.

### Fachliche Festlegungen

- Zeitraum vom 21. eines Monats bis zum 20. des Folgemonats
- Nur Arbeitstage Montag bis Freitag, NRW-Feiertage ausgenommen
- **Der 6. Januar ist kein Feiertag in NRW** — war in der NAS-Fassung fälschlich
  ausgeschlossen, ist jetzt korrigiert
- Fester Betrag 7,00 € pro Tag; die frühere Auswahl 7–14 € ist entfallen
- Datumskonstruktion lokal über `new Date(y, m, d, 12, 0, 0)`, kein `toISOString`
  im Datumspfad
- **Ortsauswahl erfolgt manuell:** Dropdown je Zeile, „Letzte Stadt übernehmen"
  für leere Zeilen, Bulk-Zuweisung auf alle Zeilen. Neue Zeilen starten ohne Ort,
  der PDF-Export bleibt gesperrt, solange Tage ohne Ort offen sind. Einen
  Zufallsgenerator gibt es nicht und soll es nicht geben.
- Tabelle scrollt in einem eigenen Container, damit Betrag und Löschen-Knopf auf
  dem Handy erreichbar bleiben

### Tests

Zwei Playwright-Suiten wurden gegen die App gefahren, 50 Prüfungen, alle grün:
Zeitraumgrenzen, Feiertagsbehandlung in beide Richtungen, fester Satz und
Monatssumme, Exportsperre, Bulk- und Letzte-Stadt-Verhalten, Persistenz über
Neustart, Fertig-Status, Städteliste, Maps-URL-Erkennung, PDF an die Brücke mit
korrektem Dateinamen und MIME-Typ, JSZip, sowie die GPS-Brücke inklusive
Fehlerfällen. Die Testskripte lagen im Scratchpad der Cloud-Sitzung und sind
**nicht** im Repo — bei Bedarf neu schreiben.

---

## Offen: Portwechsel auf dem NAS

Die alte Web-App läuft auf dem Synology-NAS unter `c-liveform.de` und belegt die
Hauptdomain. Sie soll auf **Port 8443** umziehen, damit Port 443 für eine neue
Hauptseite frei wird. Veröffentlicht ist sie über den **Synology Reverse Proxy**.

Ausführliche Anleitung siehe die Artifact-Seite aus der Cloud-Sitzung; die
Kurzfassung:

1. Reverse-Proxy-Eintrag bearbeiten, Quellport 443 → 8443, Ziel unverändert
   (`Systemsteuerung › Anmeldeportal › Reverse Proxy`)
2. Zertifikat dem neuen Dienst `c-liveform.de:8443` zuordnen
   (`Systemsteuerung › Sicherheit › Zertifikat › Konfigurieren`)
3. DSM-Firewall: TCP 8443 freigeben
4. Router: extern 8443 → NAS 8443; die 443-Weiterleitung vorerst stehen lassen
5. Testen: `https://c-liveform.de:8443`, aus dem Heimnetz und über Mobilfunk
6. Erst danach Port 443 räumen

### Vor dem Ändern erst diese Lesebefehle über SSH

```bash
sudo docker ps --format '{{.Names}}\t{{.Ports}}\t{{.Image}}'
sudo cat /usr/syno/etc/www/ReverseProxy/*.json 2>/dev/null | grep -E '"(frontend|backend)"' -A6
sudo ss -tlnp | grep -E ':(80|443|8443|3000|5000)\b'
```

### Fallstricke

- **Port 80 muss weitergeleitet bleiben.** DSM verlängert Let's-Encrypt-Zertifikate
  über eine Anfrage auf Port 80. Fehlt die Weiterleitung, läuft das Zertifikat
  binnen 90 Tagen ab.
- **Der alte Service Worker überlebt den Umzug.** Die App registriert
  `c-liveform.de/sw.js` und bleibt damit für die gesamte Hauptdomain zuständig.
  Kommt dort später eine andere Seite hin, liefert der Worker womöglich weiter
  die zwischengespeicherte App aus. Abhilfe: eine `sw.js` an dieselbe Stelle
  legen, die sich selbst abmeldet, Caches löscht und offene Tabs neu lädt.
- **Andere Herkunft, andere Browserdaten.** `c-liveform.de` und
  `c-liveform.de:8443` sind für den Browser verschieden — Anmeldung und lokale
  Zwischenstände starten neu. Die SQLite-Daten im Container bleiben unberührt.
- **Die Portal-Schaltfläche** in der App zeigt auf `/` und landet künftig auf der
  neuen Hauptseite. Entfernen oder umbiegen.
- **Port 8443 ist nicht überall erlaubt.** Aus Kundennetzen kommen oft nur 80 und
  443 durch. Falls das stört: Subdomain `reisekosten.c-liveform.de` auf 443,
  gleicher Handgriff im Reverse Proxy, nur an anderer Stelle.

---

## Offen: APK lokal bauen

In der Cloud-Sitzung war `dl.google.com` gesperrt, deshalb lief der Build über
GitHub Actions. Lokal mit installiertem Android Studio genügt:

```bash
cd android-reisekosten
./gradlew assembleDebug
```

Die APK liegt danach unter
`android-reisekosten/app/build/outputs/apk/debug/app-debug.apk`.

In Android Studio: **Open** und den Ordner `android-reisekosten` wählen.

Der Workflow `.github/workflows/build-reisekosten-apk.yml` baut weiterhin bei
jedem Push und legt das Ergebnis zusätzlich als `dist/reisekosten.apk` ab. Wer
lokal baut, braucht ihn nicht — er stört aber auch nicht.

---

## Mögliche nächste Schritte

- **Ortsteile statt Gemeinden bei GPS.** Der `Geocoder` liefert je nach Gegend
  „Warendorf" statt „Freckenhorst". Ausgelesen wird derzeit in der Reihenfolge
  `locality` → `subAdminArea` → `subLocality` → `adminArea`; Reihenfolge lässt
  sich drehen.
- **Daten vom NAS übernehmen.** Die App startet mit leerem localStorage. Ein
  Import aus der SQLite-Datenbank wäre machbar, wenn ein Export vorliegt.
- **Doppelter Code in der NAS-Fassung.** In der Originaldatei sind `saveRows`,
  `loadSavedRows`, `generateAndLoad` sowie der komplette Fertig- und Admin-Teil
  zweimal definiert; die zweite Fassung überschreibt die erste. In der
  Android-Version bereinigt, auf dem NAS läuft es weiterhin so.
- **`nas-shim.js`** unter `android-reisekosten/app/src/main/assets/` fängt
  `fetch()`-Aufrufe auf `/api/...` ab und bedient sie aus dem localStorage.
  Wird von der aktuellen App nicht gebraucht, ist aber nützlich, falls die
  NAS-Fassung noch einmal unverändert eingesetzt werden soll.
