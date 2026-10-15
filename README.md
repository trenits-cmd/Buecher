# Bücher – Native Android Reading Management App

„Bücher“ ist eine moderne, native Android-Lese- und Bücherregal-Verwaltung entwickelt mit **Jetpack Compose (Material 3)** und **Kotlin**.

## Hauptmerkmale

- **Regal & Bibliothek:** Verwaltung von Büchern nach Status (*Ungelesen*, *Wird gelesen*, *Pausiert*, *Gelesen*), Sprache (*Deutsch*, *Englisch*, *Französisch*) und kombinierten Sprach- und Status-Filtern.
- **Aktive Lektüre & Leseprognose:** Intelligente Berechnung des Lesetempos (Seiten pro Tag) und geschätzter Fertigstellungstermin für aktive Bücher (filterbar nach Sprache).
- **Jahres-Leseziel & Statistiken:** Anpassbares jährliches Leseziel mit Fortschrittsbalken, Sprachstatistiken und Jahresübersicht.
- **Monatliche Lesegrafik:** Interaktives monatliches Balkendiagramm (Januar–Dezember) für gelesene Seiten, filterbar nach Jahr und Sprache.
- **Archiv:** Archiv für gelesene Bücher, filterbar nach Sprache, Sternebewertung und Beendigungsjahr, inklusive durchschnittlicher Seitenzahl pro Tag (Ø S./Tag).
- **Mehrquellen-Suche & URL-Parsing:** Automatische Suche über **Open Library** und **Google Books** sowie automatisches Auflösen von Amazon- und Web-Links.
- **Speicherung & Cloud-Sync:** Lokaler SharedPreferences-Cache + optionaler Remote-Sync über ein GitHub-Repository (`buecher.json`).
- **CSV Backup Export & Import:** Vollständiges Sichern und Wiederherstellen der gesamten Bibliothek (inklusive aller 13 Datenfelder wie ID, Titel, Autor, Sprache, Seiten, Status, Bewertungen, Cover-URLs, Amazon-Links und Lesedaten) über CSV-Dateien mit Direktfeedback.
- **Automatisierte APK-Erstellung (CI/CD):** GitHub Actions Workflow (`android-apk.yml`) mit Java 21 für automatisierte Builds und Releases (`Buecher-App.apk`).

## Tech Stack

- **UI:** Jetpack Compose, Material 3, Coil
- **Architektur & State:** MVVM, Kotlin Coroutines, StateFlow
- **Netzwerk & Daten:** Retrofit 2, Gson, GitHub REST API, Google Books API, Open Library API
- **Build System:** Gradle 8.13, AGP 8.8.2, Java 21 (Temurin / Adoptium)

## Build & Test

```bash
# Debug APK bauen
./gradlew :app:assembleDebug

# Unit-Tests ausführen
./gradlew :app:testDebugUnitTest
```

## GitHub Actions & APK Download

Jeder Push auf den `main`-Branch triggert den GitHub Actions Workflow. Die direkt installierbare Android-App (**`Buecher-App.apk`**) steht automatisch im Bereich **Releases** des GitHub-Repositories zum 1-Klick-Download bereit.
