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
| `service` | Fachlogik, fachliche Exceptions |
| `provider` | `NewsProvider`, Implementierungen je Anbieter, Registry, Aggregator-Aufruf, Parallelitätsbegrenzung |
| `research` | Parsing der Recherche-Antworten, Prüfung der Meldungen (Zeitraum, URL, Duplikate) |
| `cost` | Kostenberechnung, Budgetprüfung |
| `process` | BPMN-Delegates, `DigestProcessStarter` |
| `scheduling` | Laden der Zeitpläne, Reload, Start der Läufe |
| `render` | HTML-Mail- und PDF-Erzeugung aus demselben Aggregat, Längenbegrenzung, Gotenberg-Client |
| `mail` | Mailversand per SMTP über `JavaMailSender` (lokal Mailpit, in AWS SES); ein Code-Pfad für alle Umgebungen |
| `storage` | S3-Zugriff |
| `metrics` | Micrometer-Metriken |
| `repository` | Spring-Data-Repositories |
| `domain` | JPA-Entities |

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
- Modellnamen nie fest im Code oder in `application.yml` (Ausnahme: Default-Aggregator-Modell, siehe `12-defaults.md`)
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
- Antwort, die sich nicht parsen lässt: ein Reparaturversuch mit demselben Modell, danach `FAILED`

## Prüfung der Meldungen
Reihenfolge, jeweils mit Status am `news_item`:
1. `OUT_OF_PERIOD`: Veröffentlichungsdatum außerhalb des Zeitraums (fehlendes Datum ist erlaubt, wird aber im PDF nicht als bestätigt gezählt)
2. `DUPLICATE_PREVIOUS`: normalisierte URL bereits in einem früheren erfolgreichen Lauf desselben Topics gemeldet (Rückblick siehe Defaults)
3. `DUPLICATE_RUN`: gleiche normalisierte URL von mehreren Modellen in diesem Lauf; eine Meldung bleibt `VALID`, die Modelle werden für den Konsens gezählt
4. `UNREACHABLE`: URL-Prüfung per HTTP mit kurzem Timeout schlägt fehl (siehe SSRF-Schutz in `06-security.md`)
5. Sonst `VALID`

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
