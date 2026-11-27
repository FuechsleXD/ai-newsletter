# 02 – Architektur und Konventionen

## Überblick
```
Browser ──HTTPS──> Reverse Proxy (Caddy, TLS) ──> Spring Boot Backend ──> PostgreSQL
                    └─ liefert React-UI aus        ├─ REST-API (/api/v1)
                                                   ├─ CIB seven (Prozess digestRun)
                                                   ├─ Scheduler (Zeitpläne aus der DB)
                                                   ├─ Engine-Webapp (/bpm, nur ADMIN)
                                                   └─ Actuator (nur intern)
Backend ──> KI-Anbieter | Quellen-URLs (Prüfung) | Gotenberg (PDF, intern) | S3 (PDFs) | SES (Mail) | CloudWatch (Logs, Metriken)
```
Hosting ohne Load Balancer, Details in `11-deployment-cicd.md`.

## Packages
`de.fuechsle.ainewsletter` (Maven: `de.fuechsle:ai-news-digest`)
| Package | Inhalt |
|---|---|
| `config` | `@ConfigurationProperties` (Defaults), technische Konfiguration |
| `web` | Controller, Request/Response-Records, Mapper, `GlobalExceptionHandler` |
| `security` | Security-Konfiguration, Entry Point, Access-Denied-Handler |
| `service` | Fachlogik, fachliche Exceptions; Abbrechen eines Laufs (`RunCancellationService`) |
| `provider` | `NewsProvider`, Implementierungen je Anbieter, Registry, `ModelSnapshot` (Modell und Preise für einen Aufruf), `SearchTrace` (Suchbegriffe und Treffer eines Aufrufs zur Diagnose; bei Anthropic aus der vollständigen Antwort in den Spring-AI-Metadaten), `ModelCalls` (Token und Kosten über Aufruf und Reparaturversuche), `JsonModelCall` (Aufruf mit JSON-Vertrag und Reparaturversuch), `ProviderCallLimiter` (Parallelitätsbegrenzung pro Anbieter; die Registry gibt nur begrenzte Provider heraus) |
| `aggregate` | Aggregator-Aufruf, `DigestAggregate`, Parsing und Validierung der Aggregator-Antwort, Icon-Schlüssel, Speichern des Ergebnisses |
| `research` | Parsing der Recherche-Antworten, URL-Normalisierung, Prüfung der Meldungen (Zeitraum, URL, Duplikate) |
| `cost` | Kostenberechnung (`CostCalculator`, je Aufruf mit Preisstufe nach Prompt-Länge), Budgetprüfung (`BudgetService`), Budgetwarnung (`BudgetWarningService`), Kostenauswertung nach Topic, Modell und Monat (`CostReportService`) |
| `process` | BPMN-Delegates, `DigestProcessStarter` |
| `scheduling` | `DigestScheduler`: Zeitpläne der aktiven Topics aus der DB, Reload ohne Neustart, Start der Läufe über `DigestProcessStarter`, Nachholen verpasster Starts beim Start der App (ADR-019); nur mit `newsdigest.scheduler.enabled` (Profil `prod`) |
| `maintenance` | Wartungsjob (`MaintenanceJob`) kurz nach dem Start und dann alle 24 h: überfällige Läufe abbrechen (`OverdueRunCanceller`), Aufbewahrungsfristen und History-Cleanup der Engine (`RetentionService`) |
| `render` | HTML-Mail- und PDF-Erzeugung aus demselben Aggregat (`DigestView`, Thymeleaf-Templates in `templates/pdf/` und `templates/mail/`, Klartext-Teil), Längenbegrenzung (`TextShortener`), Ausgabesprache mit festen Texten und Datumsformat (`DigestLanguage`, `messages/digest*.properties`), Gotenberg-Client |
| `mail` | Mailversand per SMTP über `JavaMailSender` (lokal Mailpit, in AWS SES); ein Code-Pfad für alle Umgebungen; Digest-Versand mit Protokoll je Empfänger, Betreiber-Mail |
| `storage` | Ablage der PDFs (`DigestStorage`): `LocalDigestStorage` (Default, lokales Verzeichnis) oder `S3DigestStorage` (Profil `prod`), gemeinsame Schlüsselprüfung `StorageKeys` (ADR-015) |
| `metrics` | Micrometer-Metriken |
| `repository` | Spring-Data-Repositories |
| `domain` | JPA-Entities |
| `migration` | Flyway-Java-Migrationen (offizielle CIB-seven-Skripte im Schema `bpm`, siehe `03-datenmodell.md`) |
| `prompt` | Laden der versionierten Prompt-Templates, `PromptRenderer` |
| `eval` | Prompt-Eval im Profil `eval`: Topics durch den normalen Prozess laufen lassen und Ergebnisse nach `data/eval/` exportieren, mit Bericht der Websuchen je Modell (`SearchTraceReport`) |
| `secret` | `SecretProvider`: `EnvironmentSecretProvider` (Default) oder `AwsSecretsManagerSecretProvider` mit Cache (Profil `prod`); die AWS-Clients legt `config.AwsClientConfig` nur bei Bedarf an (ADR-015) |

## Schichten
- Controller -> Service -> Repository; keine Logik und kein try/catch im Controller
- Delegates sind dünn und rufen nur Services auf
- Entities werden weder ausgegeben noch in Prozessvariablen oder Prompts durchgereicht

## Records
- API-DTOs: `<Ressource>Request` / `<Ressource>Response` als Records mit Bean Validation
- Interne Wertobjekte ebenfalls Records, z. B. `ModelInvocation`, `ResearchResponse`, `NewsItemCandidate`, `DigestAggregate`, `CostSnapshot`
- Entities bleiben normale JPA-Klassen; Mapping in expliziten Mapper-Klassen (MapStruct nur nach Rückfrage)

## KI-Anbindung
- Interface `NewsProvider` pro Anbieter; Registry ordnet `ai_provider.code` der Implementierung zu
- Das Modell ist ein Aufrufparameter: jeder Aufruf bekommt ein Record mit Modell-ID, Temperatur, Max-Tokens (bei Spring AI über Chat-Options pro Request)
- Modellnamen und Preise nie fest im Code, in `application.yml` oder in Migrationen; auch das Aggregator-Modell steht nur am Topic (ADR-013)
- Umsetzung für Anthropic: `AnthropicNewsProvider` über Spring AI (`spring-ai-anthropic`, ohne Starter); Modell-ID, Max-Tokens, Temperatur (nur wenn gesetzt), API-Key, Timeout und Websuche (`max_uses`) werden pro Aufruf gesetzt. Spring AI 2.0.3 liefert bei einer Antwort ohne Inhalt (z. B. Ablehnung) weder Stop-Grund noch Token-Zahlen; die Input-Token werden dann aus dem Prompt geschätzt und als Schätzung markiert. Ein pausierter Such-Turn (`pause_turn`) wird nicht fortgesetzt, das Ergebnis ist `FAILED` (ADR-014)
- Recherche nutzt die Web-Suche bzw. Grounding des Anbieters
- Jeder Aufruf liefert Token-Zahlen; daraus und aus den Modellpreisen werden die Kosten berechnet und gespeichert
- Parallelität: begrenzte gleichzeitige Aufrufe pro Anbieter (Semaphore, Default in `12-defaults.md`), damit Rate-Limits nicht greifen
- Ein Timeout oder Fehler eines Modells ist ein Ergebnis mit Status `FAILED`, kein Abbruch des Laufs
- Neuer Anbieter = neue `NewsProvider`-Implementierung + Zeile in `ai_provider`; Prozess und API bleiben gleich

## Recherche-Antwort (JSON-Vertrag)
```json
{
  "items": [{
    "headline": "string",
    "summary": "string (2-3 Sätze)",
    "url": "string",
    "publishedAt": "YYYY-MM-DD oder null"
  }]
}
```
- Max. Anzahl Meldungen pro Modell siehe Defaults
- Antwort, die sich nicht parsen lässt: ein Reparaturversuch mit demselben Modell (Prompt `repair-json`, ohne Websuche), danach `FAILED`; beide Aufrufe werden gezählt und berechnet
- Text vor oder nach dem JSON-Objekt (Einleitungssatz, Markdown-Codeblock) wird ignoriert; einzelne Meldungen, die den Vertrag verletzen (Überschrift oder Zusammenfassung fehlt, keine absolute http(s)-URL, URL länger als 2048 Zeichen), werden verworfen, der Rest bleibt gültig; ein Datum, das nicht `YYYY-MM-DD` ist, gilt als unbekannt; Meldungen über dem Maximum werden abgeschnitten (das Modell sortiert nach Bedeutung)
- Umsetzung: `ResearchService` (Package `research`) liefert ein `ResearchResult` mit Status, Meldungen, Token, Websuchen, Kosten und Fehlertext; gespeichert wird es von `RunResearchService`. Aufruf, Reparaturversuch und Kostenerfassung teilen sich Recherche und Aggregation (`JsonModelCall`, `ModelCalls`)

## Prüfung der Meldungen
Reihenfolge, jeweils mit Status am `news_item`:
1. `OUT_OF_PERIOD`: Veröffentlichungsdatum außerhalb des Zeitraums (fehlendes Datum ist erlaubt, wird aber im PDF nicht als bestätigt gezählt)
2. `DUPLICATE_PREVIOUS`: normalisierte URL bereits in einem früheren erfolgreichen Lauf desselben Topics gemeldet (Rückblick siehe Defaults)
3. `DUPLICATE_RUN`: gleiche normalisierte URL von mehreren Modellen in diesem Lauf; eine Meldung bleibt `VALID`, die Modelle werden für den Konsens gezählt
4. `UNREACHABLE`: URL-Prüfung per HTTP mit kurzem Timeout schlägt fehl (siehe SSRF-Schutz in `06-security.md`)
5. Sonst `VALID`

Umsetzung (Stand Schritt 14): `NewsItemChecker` prüft nach dem Join alle Meldungen des Laufs in dieser Reihenfolge und beginnt dabei jedes Mal von vorn (wiederholbarer Job). Bei `DUPLICATE_RUN` bleibt die zuerst gespeicherte Meldung (kleinste ID) Kandidat; eine Meldung außerhalb des Zeitraums oder aus einem früheren Lauf zählt dabei nicht mit. `DUPLICATE_PREVIOUS` vergleicht mit Meldungen `VALID` aus Läufen `COMPLETED` desselben Topics, die höchstens `ai.duplicate-lookback-days` vor dem Start dieses Laufs begonnen haben. Nur die übrigen Kandidaten gehen an `UrlChecker` (Package `research`, SSRF-Schutz in `06-security.md`), jede URL einmal pro Lauf, höchstens `url-check.max-concurrent-checks` gleichzeitig. `UNREACHABLE` sind: Host nicht auflösbar, keine Verbindung, Timeout, vom SSRF-Schutz gesperrt, mehr als `url-check.max-redirects` Weiterleitungen, 404 oder 410. Alle anderen Antworten gelten als erreichbar, auch 401/403/429/5xx, weil viele Nachrichtenseiten Bots abweisen, obwohl der Artikel existiert. Die Prüfung läuft in der Transaktion des Jobs (wenige Sekunden).

## Aggregation
- `AggregationService` (Package `aggregate`) rendert den Prompt `aggregate`, ruft das Aggregator-Modell des Laufs ohne Websuche auf und parst die Antwort (`AggregateResponseParser`); bei ungültiger Antwort ein Reparaturversuch wie bei der Recherche
- Dazu bekommt er die Überschriften der Sections des letzten `COMPLETED`-Laufs des Topics (`{{previousHeadlines}}`, eine pro Zeile, `none` ohne Vorlauf; abschaltbar mit `ai.pass-previous-headlines-to-aggregator`). Sie stammen aus früherer Modellausgabe und stehen außerhalb des Datenblocks: spitze Klammern und Zeilenumbrüche werden entfernt, jede Überschrift auf `output.max-heading-chars` gekürzt
- An den Aggregator gehen nur Meldungen mit Status `VALID`, je Meldung mit den Anzeigenamen aller Modelle, die dieselbe normalisierte URL im Lauf gemeldet haben (Konsens). Spitze Klammern im JSON werden als `\u003c`/`\u003e` geschrieben, damit Text aus den Meldungen den Block `<MELDUNGEN>` nicht schließen kann
- Harte Verstöße (kein JSON, `title` oder `summary` fehlt, keine brauchbare Section) führen zum Reparaturversuch, danach ist die Aggregation gescheitert
- Weiche Verstöße werden korrigiert: unbekannte `sourceIds` entfernt, Sections ohne bekannte Quelle, Überschrift oder Text verworfen, mehr als das Maximum abgeschnitten, unbekannter `iconKey` wird `default`, bei `flowSteps` und `highlightNumber` zugleich bleibt nur `highlightNumber`; weniger Sections als das Minimum sind erlaubt
- Texte werden beim Parsen nicht gekürzt, sondern erst vor dem Rendern
- `sources` und `models` setzt die App aus den geprüften Meldungen, nie aus der Antwort des Modells
- Icon-Schlüssel stehen in `src/main/resources/pdf/icon-keys.txt` (mit `default`); die SVGs folgen in Schritt 17
- `RunAggregationService` speichert direkt nach dem Aufruf in eigener Transaktion: Inhalt in `digest_run.aggregate_json`, Token und Kosten in `aggregation_result` (bei Wiederholung addiert), bei Fehlschlag den Grund in `digest_run.error_message`

Zeitzonen: Der Zeitraum eines Laufs wird in der Zeitzone seines Zeitplans berechnet und als Datum (Anfang bis Ende inklusive) an die Modelle gegeben; `publishedAt` wird als lokales Datum dieser Zeitzone verglichen. Gespeichert wird immer in UTC.

URL-Normalisierung: Kleinschreibung von Schema und Host, ohne Fragment, ohne Tracking-Parameter (`utm_*` u. ä.), ohne abschließenden Slash.

## Prompts
- Templates in `src/main/resources/prompts/<name>/v<nummer>.txt`, aktive Version in der Konfiguration; vordefinierte Version `v1` für `research`, `aggregate` und `repair-json` liegt bereits vor
- Platzhalter `{{name}}` (z. B. `{{topicName}}`, `{{periodFrom}}`), ersetzt durch eine eigene Klasse `PromptRenderer`; Liste und Regeln in `src/main/resources/prompts/README.md`
- Die verwendete Prompt-Version wird in `digest_run` gespeichert
- Änderungen an Prompts nur als neue Version, alte bleiben erhalten
- Bewertung von Prompt-Änderungen siehe `10-tests.md` (Beispielantworten und Eval-Profil)

## Konfiguration und Secrets
- API-Keys, DB-Zugang und SES-Zugang nur im AWS Secrets Manager (lokal: nicht eingecheckte `.env`); Details in `06-security.md`
- Profile: `dev` (lokal), `prod` (AWS), `eval` (manuelle Prompt-Bewertung mit echten APIs)

## Beobachtbarkeit
- Strukturiertes Logging mit Korrelations-ID (`traceId`, `runId`); keine Mailadressen oder Secrets im Log
- Metriken siehe `11-deployment-cicd.md`

## Code-Stil
- Java: Formatierung mit Spotless (Google Java Format), Prüfung in `./mvnw verify`
- Frontend: ESLint + Prettier, Prüfung in `npm run lint`
- `.editorconfig` im Root (UTF-8, LF, Einrückung)
- Abhängigkeiten dafür gelten als freigegeben

## Allgemeine Konventionen
- Constructor Injection, kein Field Injection
- Code und Kommentare Englisch, Doku und UI Deutsch
