# Event Countdown

Cleane Android-App für Event-Countdowns – mit frei gestaltbaren Homescreen-Widgets.

## Funktionen

- **Anzeige frei wählbar:** Tage, Stunden, Minuten, Sekunden – einzeln oder kombiniert.
  Nur „Stunden“ zeigt die Gesamtstunden, „Tage + Minuten“ Tage plus den Rest in Minuten.
- **Rollende Ziffern** in der App: Jede Ziffer rollt einzeln wie ein Zählwerk.
- **Stil:** Hintergrund, Zahlen und Text jeweils als Farbe oder **Farbverlauf** (2–3 Farben, Winkel),
  Hintergrundbild mit Abdunkeln, Deckkraft, **Ecken-Rundung**, 4 Schriften
  (Modern/Inter, Rund/Nunito, Serif/Fraunces, Mono/JetBrains Mono) in 4 **Stärken**, Beschriftungen an/aus.
- **Widget-Designer:** Titel, Datum, Countdown und ein eigenes **Bild** frei platzieren
  (ziehen, mit zwei Fingern skalieren, Ausrichtung, einrasten an Mitte und Rändern) –
  getrennt für quadratische (2×2) und breite (4×2) Widgets. Die Vorschau nutzt exakt denselben
  Renderer wie das echte Widget.
- **Sekunden im Widget:** Android-Widgets können nicht sekündlich aktualisiert werden. Die App legt
  deshalb einen `TextClock` (tickt im Launcher selbst, kostet keinen Akku) mit einer eigens erzeugten
  Schrift über das Widget: Deren Ligaturen zeigen statt der Uhr-Sekunde `ss` den Wert `(60 − ss) mod 60`,
  also die verbleibenden Sekunden. Events liegen daher immer auf einer vollen Minute.
- Minutenwechsel per exaktem, nicht weckendem Alarm; das Widget blendet weich über.
- **Neu in 0.0.2:** Milch- und Flüssigglas für alle Farben, Kontur und Schatten für Schrift, 9 Schriften,
  Zahlen-Animationen (nach oben, von oben, Überblenden, Zoom, Klappen, Schmelzen) – auch im Widget je Einheit,
  bis zu 3 Fotos mit Ecken, Rand (Farbe/Verlauf/Glas), Schatten und Drehung, die über den Rand ragen dürfen,
  Notiz und Fortschrittsbalken, Modus „Seit“ (z. B. seit wann man den Scooter hat), Jahre/Monate/Wochen,
  Wiederholung, Erinnerung, Widgets 4×1 (Leiste), 4×4 (groß) und Übersicht, Export/Import als Datei.
- Einstellungen: Theme System/Hell/Dunkel (Kreis-Übergang), Sprache Deutsch/English/Türkçe,
  Updates aus GitHub Releases (Installieren / Später / Überspringen).

## Bauen

```
./gradlew assembleRelease   # app/build/outputs/apk/release/
```

GitHub Actions baut die APK bei jedem Push (Artifact `event-countdown`).
Voraussetzungen: JDK 17, Android SDK 35. minSdk 26 (Android 8).

Schriften neu erzeugen (nur nötig, wenn sie geändert werden):

```
pip install fonttools uharfbuzz
python3 tools/build_fonts.py <ordner mit inter.ttf, nunito.ttf, fraunces.ttf, mono.ttf>
python3 tools/gen_widget_res.py
```

## Releases und Updates

Ein Release entsteht durch einen Tag:

```
git tag v0.0.2 && git push origin v0.0.2   # oder: Actions → „Release APK“ → „Run workflow“
```

Der Workflow `release.yml` baut die APK und hängt sie an das GitHub Release. Die App findet es über
das Update-Popup – dafür muss das Repository **öffentlich** sein.

**Fester Signaturschlüssel (wichtig für Updates):** Android installiert ein Update nur, wenn es mit
demselben Schlüssel signiert ist wie die installierte App. Einmalig einen Schlüssel erzeugen und als
Repo-Secrets hinterlegen (Settings → Secrets and variables → Actions):

```
keytool -genkeypair -v -keystore release.jks -alias countdown -keyalg RSA -keysize 2048 -validity 10000
base64 -w0 release.jks     # Ausgabe → Secret KEYSTORE_BASE64
```

Weitere Secrets: `KEYSTORE_PASSWORD`, `KEY_ALIAS` (z. B. `countdown`), `KEY_PASSWORD`.
Den Schlüssel sicher aufbewahren und **nicht** ins Repository legen. Ohne diese Secrets bricht der
Release-Workflow ab – sonst ließe sich die nächste Version nicht über die installierte aktualisieren
(Android lehnt Updates mit anderem Schlüssel ab; eine Neuinstallation löscht Widgets und Daten).
Mit festem Schlüssel bleiben bei jedem Update alle Widgets, Events und Bilder erhalten.

Widgets lassen sich außerdem im Editor über **Teilen** als `.countdown`-Datei (inklusive Bilder)
sichern oder an Freunde schicken und über **⋮ → Widget-Datei importieren** wieder einlesen.

## Lizenzen

Schriften: Inter, Nunito, Fraunces, JetBrains Mono – SIL Open Font License 1.1
(`app/src/main/assets/licenses/`).
