# 05 – REST-API und Fehlerbehandlung

## Grundlagen
- Basispfad `/api/v1`, JSON, Controller im Package `web`
- DTOs ausschließlich als Records: `<Ressource>Request`, `<Ressource>Response`
- Listen mit Pagination und Sortierung (Defaults in `12-defaults.md`)
- Optimistic Locking über `version`; Konflikt -> 409
- OpenAPI wird automatisch erzeugt; Swagger-UI nur im Profil `dev`
- Der API-Client des Frontends wird aus der OpenAPI-Beschreibung generiert
- Zeitpunkte im JSON als ISO 8601 mit Zeitzonen-Offset (UTC), reine Daten als `YYYY-MM-DD`; Beträge als Dezimalzahl mit Währung `USD`

## Ressourcen
| Methode und Pfad | Zweck | Rolle |
|---|---|---|
| `GET/POST /schedules`, `GET/PUT /schedules/{id}`, `POST /schedules/{id}/deactivate` | Zeitpläne | Lesen: VIEWER, Schreiben: ADMIN |
| `GET /schedules/preview?cron=&timezone=` | Nächste 5 Ausführungen | VIEWER |
| `GET/POST /topics`, `GET/PUT /topics/{id}`, `POST /topics/{id}/deactivate` | Topics inkl. Aggregator-Modell | VIEWER / ADMIN |
| `GET/PUT /topics/{id}/models` | Menge der Recherche-Modelle | VIEWER / ADMIN |
| `GET/PUT /topics/{id}/recipients` | Empfänger-Zuordnung | VIEWER / ADMIN |
| `GET /providers`, `PUT /providers/{id}` | Anbieter (Anzeigename, aktiv, max. parallele Aufrufe, Secret-Name), nie Keys; Antwort zeigt nur, ob das Secret vorhanden ist | VIEWER / ADMIN |
| `GET/POST /models`, `GET/PUT /models/{id}`, `POST /models/{id}/deactivate` | Modelle inkl. Preise; Filter `providerId`, `webSearch`, `structuredOutput` | VIEWER / ADMIN |
| `GET/POST /recipients`, `GET/PUT /recipients/{id}`, `POST /recipients/{id}/deactivate` | Empfänger | VIEWER / ADMIN |
| `GET/PUT /budget` | Monatsbudget und Warnschwelle | VIEWER / ADMIN |
| `GET /costs?from=&to=&groupBy=topic|model|month` | Kostenauswertung | VIEWER |
| `GET /runs` | Läufe; Filter Topic, Status, Zeitraum | VIEWER |
| `GET /runs/{id}` | Detail: Ergebnisse je Modell, Meldungen mit Prüfstatus, Tokens, Kosten, Mail-Status, Prompt-Version | VIEWER |
| `GET /runs/{id}/pdf` | PDF (Stream oder kurzlebige Presigned-URL) | VIEWER |
| `GET /runs/{id}/mail-preview` | HTML-Mail des Laufs | VIEWER |
| `POST /runs/{id}/cancel` | Laufenden Lauf abbrechen: 200; 409 wenn nicht `RUNNING` | ADMIN |
| `POST /topics/{id}/runs` | Manueller Lauf: 202 + Run-ID; 409 wenn Lauf aktiv; 429 bei Rate-Limit; 422 wenn Budget erreicht | ADMIN |

Nur im Profil `dev` (Schritt 9, ohne Rollen, nie in Produktion): `POST /dev/topics/{id}/runs` startet einen Lauf, 202 mit `runId` und `processInstanceId`; 404 bei unbekanntem Topic, 409 bei aktivem Lauf.

## Validierung
- Cron mit Springs `CronExpression`, Zeitzone gegen `ZoneId`, E-Mail-Format, eindeutige Namen
- Recherche-Modelle müssen `supports_web_search` haben, Aggregator-Modell `supports_structured_output`
- `temperature` (0 bis 2) nur, wenn das Modell `supportsTemperature` hat; sonst 400 mit Feldfehler `temperature` (zusätzlich CHECK in der Datenbank, ADR-013)
- Modell und Anbieter müssen aktiv sein; ein aktives Topic braucht mindestens ein Recherche-Modell und mindestens einen aktiven Empfänger
- Preise >= 0; Budget > 0, Warnschwelle 1–100 %
- Änderungen an Topics und Zeitplänen stoßen den Scheduler-Reload sofort an

## Fehlerbehandlung
- Eine Klasse `GlobalExceptionHandler` mit `@RestControllerAdvice`, abgeleitet von `ResponseEntityExceptionHandler` (seit Schritt 9 mit `NotFoundException` -> 404, `ConflictException` -> 409 und 500; Rest in Schritt 19)
- Jede Fehlerantwort ist ein RFC-9457 `ProblemDetail` mit `type`, `title`, `status`, `detail`, `instance`, `traceId`; bei Validierung zusätzlich `errors` (Feld, Meldung)
- Fachliche Fehler: unchecked Exceptions mit gemeinsamer Basisklasse, geworfen in Services

| Fehler | Status |
|---|---|
| Bean Validation, unlesbares JSON, ungeeignetes Modell | 400 |
| Nicht angemeldet | 401 (über `AuthenticationEntryPoint`) |
| Keine Berechtigung | 403 (über `AccessDeniedHandler`) |
| `NotFoundException` | 404 |
| `ConflictException`, Versionskonflikt, Integritätsverletzung | 409 |
| `BudgetExceededException` | 422 |
| Rate-Limit | 429 |
| Alles andere | 500, generische Meldung, Details nur im Log |

- 401/403 entstehen in der Security-Filterkette, wo `@ControllerAdvice` nicht greift; Entry Point und Handler liefern dasselbe Format
- Datenbankdetails und Stacktraces gehen nie nach außen
- Geloggt wird an genau einer Stelle
