# Lyrics Analyzer

Startprojekt: Music-API (Deezer) + Lyrics-API (lyrics.ovh) + Sentiment-Analyse (Stanford CoreNLP) + PostgreSQL.

Songs werden über Deezer gesucht, Songtexte einmalig über lyrics.ovh geladen und **dauerhaft in PostgreSQL gespeichert**,
damit sie nicht bei jedem Lauf erneut abgefragt werden müssen. Anschließend wird mit einem fertigen,
vortrainierten Sentiment-Modell aus Stanford CoreNLP eine Stimmungsanalyse durchgeführt und das Ergebnis
ebenfalls persistiert.

## Architektur

```
DeezerClient        -> sucht Songs (Künstler, Titel, Album) über die Deezer-API
LyricsOvhClient      -> lädt Songtexte über lyrics.ovh (nur falls noch nicht in der DB)
LyricsIngestionService -> orchestriert Suche + Caching-Logik + Speicherung
SentimentAnalysisService -> nutzt StanfordCoreNLP (fertiges Modell) für Sentiment pro Song
SentimentBatchService -> wendet die Analyse auf alle Songs mit Lyrics aber ohne Sentiment an
TrackRepository      -> Spring Data JPA Repository auf der PostgreSQL-Tabelle "track"
```

**Wichtig zum Urheberrecht:** Die gespeicherten Songtexte sind nur für die eigene Analyse gedacht.
Der `/api/tracks` Listen-Endpunkt gibt deshalb bewusst keine vollständigen Lyrics zurück, nur Metadaten
und Sentiment-Werte. Nur der Detail-Endpunkt `/api/tracks/{id}` liefert den vollen Text, dieser sollte
nicht öffentlich/produktiv exponiert werden.

## Setup

### 1. Datenbank starten

```bash
docker compose up -d
```

Das startet PostgreSQL auf Port `5432` (DB: `lyrics_analyzer`, User: `lyrics_app`, Passwort: `lyrics_app_password`).
Die Zugangsdaten sind nur für lokale Entwicklung gedacht — für produktive Umgebungen über Umgebungsvariablen
bzw. ein Secret-Management ersetzen.

### 2. Anwendung starten

```bash
mvn spring-boot:run
```

Beim ersten Start legt Flyway automatisch das Schema (Tabelle `track`) an.

Die App läuft danach auf `http://localhost:8080`.

> **Speicher-Hinweis:** Stanford CoreNLP lädt mehrere Modelle (POS-Tagger, Parser, Sentiment-Modell)
> komplett in den Speicher und kann dabei je nach Songtext mehrere hundert MB bis mehrere GB Heap
> brauchen. Falls beim Sentiment-Endpunkt ein `java.lang.OutOfMemoryError: Java heap space` auftritt,
> braucht die Anwendung mehr Heap.
>
> `mvn spring-boot:run -Dspring-boot.run.jvmArguments="-Xmx8g"` startet die Anwendung intern in einem
> **separaten, geforkten JVM-Prozess** — in der Praxis (vor allem unter Windows/PowerShell) kommt der
> `-Xmx`-Wert dabei nicht immer zuverlässig im eigentlichen App-Prozess an, sodass der Fehler trotz
> gesetztem Parameter weiter auftritt.
>
> **Zuverlässiger Weg:** Jar bauen und direkt mit `java` starten — dann ist kein Maven-Fork mehr
> dazwischen, und `-Xmx` greift garantiert:
> ```bash
> mvn clean package -DskipTests
> java -Xmx8g -jar target/lyrics-analyzer-0.1.0.jar
> ```
> (Windows/PowerShell: identisch, nur den Pfad mit Backslash schreiben: `target\lyrics-analyzer-0.1.0.jar`)
>
> Dieser Weg startet die App nicht im "Live-Reload"-Modus von `spring-boot:run`, ist dafür aber in
> Sachen JVM-Parametern verlässlich — empfehlenswert, sobald die Sentiment-Analyse größere Mengen an
> Songtexten verarbeiten soll.

## Beispiel-Workflow

Die Befehle unten gibt es jeweils für **bash/macOS/Linux** (`curl`) und für **Windows PowerShell**.

> **Hinweis für Windows/PowerShell:** `curl` ist dort nur ein Alias für `Invoke-WebRequest` und versteht
> die Linux-curl-Syntax (`-X`, `-d`, Zeilenfortsetzung mit `\`) nicht. Nutze entweder die PowerShell-Befehle
> unten (`Invoke-RestMethod`) oder rufe explizit `curl.exe` auf (falls über Git for Windows installiert),
> dabei Zeilenfortsetzung mit Backtick `` ` `` statt `\`.

### Songs suchen und Lyrics laden

**bash:**
```bash
curl -X POST http://localhost:8080/api/ingestion \
  -H "Content-Type: application/json" \
  -d '{"searchQuery": "artist:\"Coldplay\"", "limit": 20}'
```

**PowerShell:**
```powershell
$body = @{
    searchQuery = 'artist:"Coldplay"'
    limit = 20
} | ConvertTo-Json

Invoke-RestMethod -Uri "http://localhost:8080/api/ingestion" -Method Post -ContentType "application/json" -Body $body
```

Antwort zeigt, wie viele Songs gefunden, wie viele bereits gecached waren und wie viele neu geladen wurden:

```json
{"searched": 20, "alreadyCached": 0, "newlyFetched": 14, "notFound": 6, "errors": 0}
```

Führst du denselben Request erneut aus, werden die bereits gespeicherten Songs **nicht erneut** bei
lyrics.ovh angefragt (`alreadyCached` steigt, `newlyFetched` bleibt bei 0 für diese Songs).

### Sentiment-Analyse anstoßen

**bash:**
```bash
curl -X POST "http://localhost:8080/api/sentiment/analyze-pending?limit=50"
```

**PowerShell:**
```powershell
Invoke-RestMethod -Uri "http://localhost:8080/api/sentiment/analyze-pending?limit=50" -Method Post
```

Analysiert alle Songs, die Lyrics aber noch kein Sentiment haben, und speichert das Ergebnis dauerhaft.

### Ergebnisse abfragen

**bash:**
```bash
# Liste aller Tracks (ohne Volltext-Lyrics)
curl http://localhost:8080/api/tracks

# Einzelner Track inkl. Lyrics
curl http://localhost:8080/api/tracks/1

# Durchschnittliches Sentiment pro Genre
curl http://localhost:8080/api/tracks/stats/by-genre

# Durchschnittliches Sentiment pro Erscheinungsjahr
curl http://localhost:8080/api/tracks/stats/by-year
```

**PowerShell:**
```powershell
# Liste aller Tracks (ohne Volltext-Lyrics)
Invoke-RestMethod -Uri "http://localhost:8080/api/tracks"

# Einzelner Track inkl. Lyrics
Invoke-RestMethod -Uri "http://localhost:8080/api/tracks/1"

# Durchschnittliches Sentiment pro Genre
Invoke-RestMethod -Uri "http://localhost:8080/api/tracks/stats/by-genre"

# Durchschnittliches Sentiment pro Erscheinungsjahr
Invoke-RestMethod -Uri "http://localhost:8080/api/tracks/stats/by-year"
```

`Invoke-RestMethod` parst die JSON-Antwort automatisch in ein PowerShell-Objekt; willst du die rohe
JSON-Antwort sehen, hänge `| ConvertTo-Json -Depth 5` an.

## Hinweise zur weiteren Entwicklung

- **Genre/Jahr befüllen**: Die Deezer-Such-API liefert in der einfachen `/search`-Antwort kein Genre/Jahr.
  Für vollständige Metadaten kannst du zusätzlich `GET https://api.deezer.com/album/{album_id}` abrufen
  (liefert u.a. `release_date` und `genre_id`) und den `TrackRepository`/`Track` entsprechend befüllen.
- **Rate Limiting**: lyrics.ovh ist ein kleines Community-Projekt ohne SLA. Der konfigurierbare Delay
  (`lyrics-analyzer.ingestion.request-delay-ms` in `application.yml`) verhindert, dass die API zu schnell
  hintereinander angefragt wird. Bei Bedarf erhöhen.
- **ERROR-Status erneut versuchen**: Tracks mit `lyricsStatus = ERROR` werden aktuell nicht automatisch
  erneut versucht. Ein Scheduled Job, der periodisch `findByLyricsStatus(ERROR, ...)` erneut anstößt,
  wäre eine sinnvolle Erweiterung.
- **Weitere NLP-Metriken**: `SentimentAnalysisService` ist der Ort, um zusätzliche Auswertungen
  (z.B. Wortvielfalt, Themen-Clustering) zu ergänzen — ebenfalls mit fertigen Bibliotheks-Modellen statt
  Eigenimplementierung.
- **"Parsing of sentence failed, possibly because of out of memory"**: lyrics.ovh liefert Songtexte ohne
  Zeilenumbrüche, sodass CoreNLPs `ssplit`-Annotator gelegentlich den gesamten Songtext als einen einzigen,
  sehr langen "Satz" erkennt — der statistische Parser braucht dafür überproportional viel Speicher.
  Zwei Gegenmaßnahmen sind bereits eingebaut:
  1. `SentimentAnalysisService.preprocessForSentenceSplitting` zerlegt den Text heuristisch (an Kommas/Klammern)
     in kürzere Segmente, bevor er an CoreNLP geht.
  2. `NlpConfig` setzt `parse.maxlen`, sodass trotzdem zu lange Segmente übersprungen statt mit OOM
     abzubrechen (sie fließen einfach nicht in den Sentiment-Durchschnitt ein).

  Tritt die Warnung trotzdem noch auf, ist das unkritisch (der Song wird weiterhin analysiert, nur dieses
  eine Segment wird ignoriert). Kommt es zu einem echten `OutOfMemoryError` (Request schlägt mit 500 fehl),
  reicht der Heap grundsätzlich nicht aus — dann hilft der in Setup beschriebene Weg über
  `mvn clean package` + `java -Xmx8g -jar ...` zuverlässiger als `mvn spring-boot:run` mit `jvmArguments`.