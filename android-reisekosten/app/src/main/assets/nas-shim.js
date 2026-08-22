/*
 * nas-shim.js — Optionaler Adapter für die bisherige NAS-Fassung der App.
 *
 * ZWECK
 * Die NAS-Version spricht über fetch() mit einem Express/SQLite-Backend
 * (/api/reisekosten/rows, /api/cities, …). In der Android-App gibt es kein
 * Backend. Dieser Adapter fängt alle Aufrufe auf /api/... ab und bedient
 * sie aus dem localStorage – die aufrufende Seite merkt davon nichts.
 *
 * EINBINDEN
 * Als allererstes Skript im <head>, noch vor dem App-Code:
 *   <script src="nas-shim.js"></script>
 *
 * ABBILDUNG
 *   GET    /api/foo/bar   -> liest  localStorage["nas_foo/bar"]
 *   POST   /api/foo/bar   -> setzt  localStorage["nas_foo/bar"] auf den Body
 *   PUT    /api/foo/bar   -> wie POST
 *   DELETE /api/foo/bar   -> löscht den Schlüssel
 *
 * Ein GET auf einen unbekannten Schlüssel liefert [] (bzw. den in
 * NAS_DEFAULTS hinterlegten Startwert) statt eines 404 – die alte App
 * erwartet an vielen Stellen ein Array.
 */
(function () {
  "use strict";

  var PREFIX = "nas_";

  // Startwerte, falls unter dem Schlüssel noch nichts gespeichert ist.
  var NAS_DEFAULTS = {
    "cities": [
      "Ahlen","Albersloh","Alverskirchen","Beckum","Beelen","Diestedde","Drensteinfurt",
      "Enniger","Ennigerloh","Everswinkel","Freckenhorst","Füchtorf","Liesborn","Milte",
      "Neubeckum","Oelde","Ostbevern","Rinkerode","Sassenberg","Sendenhorst","Stromberg",
      "Telgte","Vorhelm","Wadersloh","Warendorf","Westkirchen"
    ]
  };

  function keyFor(path) {
    // "/api/reisekosten/rows?x=1" -> "reisekosten/rows"
    var p = String(path).split("?")[0].replace(/^.*\/api\//, "").replace(/\/+$/, "");
    return PREFIX + p;
  }

  function read(path) {
    var k = keyFor(path);
    try {
      var raw = localStorage.getItem(k);
      if (raw !== null) return JSON.parse(raw);
    } catch (e) { /* defekter Eintrag – Startwert verwenden */ }

    var bare = k.slice(PREFIX.length);
    return Object.prototype.hasOwnProperty.call(NAS_DEFAULTS, bare)
      ? NAS_DEFAULTS[bare]
      : [];
  }

  function write(path, value) {
    try {
      localStorage.setItem(keyFor(path), JSON.stringify(value));
      return true;
    } catch (e) {
      return false;
    }
  }

  function jsonResponse(body, status) {
    return new Response(JSON.stringify(body), {
      status: status || 200,
      headers: { "Content-Type": "application/json" }
    });
  }

  var nativeFetch = window.fetch ? window.fetch.bind(window) : null;

  window.fetch = function (input, init) {
    var url = (typeof input === "string") ? input : (input && input.url) || "";
    var opts = init || {};
    var method = String((opts.method) || (input && input.method) || "GET").toUpperCase();

    // Alles, was nicht an die API geht, unverändert durchreichen.
    if (url.indexOf("/api/") === -1) {
      if (nativeFetch) return nativeFetch(input, init);
      return Promise.reject(new Error("fetch nicht verfügbar: " + url));
    }

    try {
      if (method === "GET") {
        return Promise.resolve(jsonResponse(read(url)));
      }

      if (method === "POST" || method === "PUT" || method === "PATCH") {
        var body = opts.body;
        var parsed;
        if (typeof body === "string") {
          try { parsed = JSON.parse(body); } catch (e) { parsed = body; }
        } else {
          parsed = body;
        }
        var ok = write(url, parsed);
        return Promise.resolve(jsonResponse(
          ok ? { ok: true } : { ok: false, error: "Speicher voll" },
          ok ? 200 : 507
        ));
      }

      if (method === "DELETE") {
        try { localStorage.removeItem(keyFor(url)); } catch (e) {}
        return Promise.resolve(jsonResponse({ ok: true }));
      }

      return Promise.resolve(jsonResponse({ error: "Methode nicht unterstützt" }, 405));
    } catch (e) {
      return Promise.resolve(jsonResponse({ error: String(e && e.message || e) }, 500));
    }
  };

  // Für Debug-Zwecke aus der Seite heraus erreichbar.
  window.NasShim = {
    read: read,
    write: write,
    keyFor: keyFor,
    clear: function () {
      var doomed = [];
      for (var i = 0; i < localStorage.length; i++) {
        var k = localStorage.key(i);
        if (k && k.indexOf(PREFIX) === 0) doomed.push(k);
      }
      doomed.forEach(function (k) { localStorage.removeItem(k); });
      return doomed.length;
    }
  };
})();
