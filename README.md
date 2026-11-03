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
