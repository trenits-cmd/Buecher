# Bücherregal für Android

Native Android-App mit Kotlin und Jetpack Compose. Bücher werden als `buecher.json` in einem GitHub-Repository gespeichert; Titel, ISBN und Amazon-Produktlinks können über Google Books ergänzt werden.

## Voraussetzungen

- Android Studio mit Android SDK Platform 35
- JDK 17 (für den Gradle-Wrapper 8.11.1 empfohlen)
- Ein GitHub-Repository und ein Personal Access Token mit Lese- und Schreibzugriff auf dessen Inhalte

## Start

1. Das Projekt in Android Studio öffnen und die Gradle-Synchronisierung abwarten.
2. Die App starten und über das Zahnrad GitHub-Benutzer/Organisation, Repository, Branch und Token eintragen.
3. `buecher.json` wird beim ersten Speichern im gewählten Branch angelegt.

Der Token bleibt nur im Arbeitsspeicher der laufenden App und wird nach einem App-Neustart erneut abgefragt. Repository, Branch und Benutzername werden lokal gespeichert.

## Build

```bash
./gradlew :app:assembleDebug
```

## APK von GitHub herunterladen

Nach Commit und Push des Workflows findest du `buecherregal-apk` unter **Actions → Android APK → letzter erfolgreicher Lauf → Artifacts**. Ein Push eines Versions-Tags wie `v1.0.0` erstellt zusätzlich ein GitHub Release mit `app-debug.apk` im Bereich **Releases** des Repositories. Das APK ist debug-signiert und für Installation und Tests gedacht.

## JSON-Format

```json
[
	{
		"id": "eindeutige-id",
		"title": "Beispieltitel",
		"author": "Beispielautor",
		"pageCount": 320,
		"language": "de",
		"rating": 4,
		"isRead": false,
		"coverImageUrl": "https://example.org/cover.jpg",
		"amazonUrl": "https://www.amazon.de/dp/1234567890",
		"pagesRead": 80
	}
]
```

Google Books wird ohne API-Schlüssel verwendet. Bei Amazon-Links sucht die App zunächst mit der Produktkennung, sofern sie als ISBN indexiert ist.