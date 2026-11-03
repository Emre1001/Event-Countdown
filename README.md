# Event Countdown

Cleane Android-App für Event-Countdowns – mit Homescreen-Widgets.

- **Anzeige frei wählbar:** Tage, Stunden, Minuten, Sekunden – einzeln oder in beliebiger Kombination.
  Nur „Stunden“ zeigt die Gesamtstunden, „Tage + Minuten“ Tage plus den Rest in Minuten.
- **Eigene Farben:** Hintergrund, Zahlen, Akzent, Deckkraft; dazu fertige Stile und ein HSV-Picker.
- **Rollende Ziffern:** Jede Ziffer rollt einzeln wie ein Zählwerk (Slide + Fade + weiche Maske).
- **Widgets:** Klein (2×2) und Breit (4×2), übernehmen Farben und Einheiten des Events.
  Widgets zeigen Tage/Stunden/Minuten (Android erlaubt keinen Sekundentakt) und aktualisieren
  sich genau dann, wenn sich eine sichtbare Zahl ändert; die Zahl wechselt animiert (ViewFlipper).
- Material You (Dynamic Color) in der App, Widget-Ecken folgen dem System-Radius (Android 12+).

## Bauen

Android Studio öffnen und ausführen, oder:

```
./gradlew assembleDebug   # app/build/outputs/apk/debug/
```

GitHub Actions baut die APK bei jedem Push (Artifact `event-countdown-debug`).

Voraussetzungen: JDK 17, Android SDK 35. minSdk 26.

## Einstellungen

- **Darstellung:** System / Hell / Dunkel. Beim Wechsel wächst das neue Theme als Kreis aus dem
  gedrückten Schalter über den Bildschirm.
- **Sprache:** Deutsch, English, Türkçe oder Systemsprache (gilt auch für die Widgets).
- **Updates:** Die App prüft die GitHub Releases dieses Repos (beim Start und alle 6 Stunden).
  Das Popup bietet *Installieren*, *Später* (fragt nach 24 h erneut) und *Dieses Update überspringen*.
  Die APK wird erst nach „Installieren“ in den App-Cache geladen und danach immer gelöscht.

## Releases veröffentlichen (für das Update-Popup)

Android installiert Updates nur, wenn jede APK mit **demselben Schlüssel** signiert ist. Einmalig:

```
keytool -genkeypair -v -keystore release.jks -alias countdown -keyalg RSA -keysize 2048 -validity 10000
base64 -w0 release.jks     # Ausgabe als Secret KEYSTORE_BASE64 speichern
```

Repo-Secrets: `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`.
Den Schlüssel gut sichern (nicht ins Repo!). Dann ein Release auslösen:

```
git tag v1.0.1 && git push origin v1.0.1
```

Der Workflow `release.yml` baut die signierte APK und hängt sie an das GitHub Release.
Das Repo muss öffentlich sein, damit die App Releases ohne Token lesen kann.
