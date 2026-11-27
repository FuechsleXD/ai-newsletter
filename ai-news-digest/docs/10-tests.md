# 10 – Tests und Coverage

## Grundregeln
- Keine echten KI-, SES/SMTP-, S3- oder externen HTTP-Aufrufe in automatischen Tests (lokale Container wie PostgreSQL und Gotenberg über Testcontainers sind erlaubt)
- DB-Tests gegen echtes PostgreSQL mit Testcontainers (kein H2)
- Jede Änderung kommt mit Tests; keine Tests ohne Aussage nur für die Coverage-Zahl

## Testarten
| Art | Werkzeug | Inhalt |
|---|---|---|
| Unit | JUnit 5, Mockito, AssertJ | Zeitraum, Cron/Zeitzone/Defaults, Modellauflösung, Übergabe von Modell-ID und Parametern, Ablehnung ungeeigneter Modelle, Prompt-Erzeugung und -Version, Parsing der Recherche-Antwort inkl. Reparaturversuch, URL-Normalisierung, Duplikat- und Zeitraumprüfung, Kostenberechnung, Budgetprüfung, Aggregat-Validierung, Textkürzung |
| URL-Prüfung | WireMock o. ä. | Erreichbar, 404, Timeout, Weiterleitung; SSRF: private IPs, Loopback, Metadaten-Adresse, Weiterleitung auf interne Adresse werden abgelehnt |
| Migration | Testcontainers | Alle Migrationen auf leerer DB; UNIQUE, FK, CHECK, Teilindizes; Hibernate-Validierung grün |
| Repository | `@DataJpaTest` + Testcontainers | Abfragen, Filter, Pagination, Monatskosten, Duplikatsuche |
| Prozess | `@SpringBootTest` + Testcontainers, eingebettete Engine | Pflichtfälle unten |
| Scheduler | `@SpringBootTest` | Konfigurationsänderung wirkt nach Reload ohne Neustart |
| API | `@WebMvcTest` / MockMvc | CRUD, Validierung -> 400, 404, 409, 422 (Budget), 429, Pagination, keine API-Keys in Antworten |
| Fehlerbehandlung | `@WebMvcTest` | Jede Zuordnung im `GlobalExceptionHandler`, 500 ohne Stacktrace |
| Security | Spring Security Test, selbst signierte Test-JWTs (eigener Schlüssel, lokaler JWKS) | Kein Token 401; abgelaufen, falscher Issuer, falsche Audience, falsche Signatur, ID Token statt Access Token jeweils 401; VIEWER schreibend 403; ADMIN erlaubt; Rollen-Mapping aus `realm_access.roles`; Engine nur ADMIN; Actuator nicht öffentlich; Security-Header |
| Keycloak-Integration (optional) | Testcontainers mit Keycloak | Realm-Datei wird importiert, Login mit Testbenutzer liefert Token, das die API akzeptiert |
| Rendering | Testcontainers (Gotenberg), PDFBox, Jsoup | Kurzes, normales, maximal langes JSON (`fixtures/digest/`): PDF über echten Gotenberg-Container genau eine Seite, Schriften eingebettet, PNG-Vorschau nach `target/pdf-preview/`; Nachkürzung bei zwei Seiten; Gotenberg nicht erreichbar -> Retry/Incident; HTML-Mail enthält alle Abschnitte, Texte sind escaped. Stand Schritt 8: kurz und normal genau eine Seite, lang höchstens zwei (Seitenprüfung und Nachkürzen mit dem finalen Layout in Schritt 17); Schriften folgen in Schritt 17 |
| Metriken | `SimpleMeterRegistry` | Zähler und Timer werden bei Erfolg und Fehler gesetzt |
| Frontend | Vitest, React Testing Library, MSW | Seiten, Formulare, Validierung, Rollen-Ausblendung, Budget-Hinweis, Fehleranzeige |
| E2E (optional) | Playwright | Anmelden, Topic anlegen, Lauf auslösen |

## Pflichtfälle Prozess
1. Happy Path: alle Modelle ok, PDF und HTML-Mail, Mail an alle aktiven Empfänger, `last_successful_run_at` gesetzt
2. Parallelität: Recherche-Instanzen laufen gleichzeitig (Jobs nicht exklusiv); der Aggregator startet erst, wenn ALLE Instanzen beendet sind. Umgesetzt in `ParallelResearchProcessTest` (zwei Modelle sind nachweislich gleichzeitig im Aufruf, jedes genau einmal; drei Modelle bei zwei Plätzen: höchstens zwei gleichzeitig, alle drei Ergebnisse) und `ProviderCallLimiterTest` (Plätze, Zeitüberschreitung, Prüfung gegen die Sperrzeit)
3. Ein Modell fällt aus: übrige Instanzen laufen weiter, Aggregat aus den übrigen
4. Gleichzeitiges Beenden mehrerer Instanzen: `OptimisticLockingException` beim Join wird durch Job-Wiederholung aufgelöst, kein Incident, Lauf korrekt
5. Alle Modelle fallen aus bzw. keine gültige Meldung: Betreiber-Mail, `last_successful_run_at` unverändert
6. Budget erreicht: Lauf wird mit `SKIPPED_BUDGET` beendet, keine KI-Aufrufe
7. Mailversand scheitert für einen Empfänger: Protokoll je Empfänger, Retries, danach Incident
8. Zweiter Start desselben Topics bei aktivem Lauf wird verhindert
9. Geänderte Modellwahl wirkt beim nächsten Lauf ohne neue Prozessversion
10. Duplikate aus früheren Läufen landen nicht im Aggregator-Input
11. Modelle liefern, aber keine gültige Meldung: `NO_NEWS`, keine Mail an Empfänger, `last_successful_run_at` gesetzt
12. Idempotenz: wiederholter Mail-Job verschickt an bereits erreichte Empfänger nichts erneut; wiederholte Recherche legt kein zweites `provider_result` an
13. Abbrechen eines laufenden Laufs: Prozessinstanz beendet, Status `CANCELLED`, neuer Lauf möglich

Test-Hilfen von CIB seven prüfen; sonst direkt mit `RuntimeService`, `HistoryService` und Job-Ausführung testen.

Stand nach Roadmap-Schritt 6 (`DigestRunProcessTest`, eingebettete Engine mit laufendem Job Executor, Testcontainers, Fake-Anbieter `FAKE` mit Verhalten je Modellname-Präfix `ok-`, `empty-`, `fail-`, `slow-`): abgedeckt sind 1 (ohne Mail und PDF, die noch Platzhalter sind), 5 (ohne Betreiber-Mail), 6, 8, 9, 11 und 12 (Recherche, `RunResearchServiceTest`). Fall 2, 3 und 4 folgen mit mehreren Modellen in Schritt 13, 10 mit den Duplikaten früherer Läufe in Schritt 14, 13 mit dem Abbrechen in Schritt 16.

Stand nach Roadmap-Schritt 7: Der Fake-Anbieter liefert zusätzlich Aggregator-Antworten (erkennbar am Block `<MELDUNGEN>` im Prompt; `garbage-` antwortet ohne JSON, `fail-` mit Fehler) und Meldungen mit dem heutigen Datum (`old-`: eine Meldung außerhalb jedes Zeitraums). `DigestRunProcessTest` prüft zusätzlich Aggregat und Aggregator-Kosten im Happy Path sowie den Fehlerpfad der Aggregation (`FAILED` ohne Retries, Betreiber informieren). `RunAggregationServiceTest` prüft ohne Engine `DUPLICATE_RUN` und Konsens mit zwei Modellen, `OUT_OF_PERIOD`, wiederholte und gescheiterte Aggregation; Fall 10 (Duplikate früherer Läufe) folgt mit Schritt 14. Unit-Tests: `UrlNormalizerTest`, `NewsItemCheckerTest`, `AggregateResponseParserTest` (Fixtures in `fixtures/aggregate/`), `AggregationServiceTest`, `IconKeysTest`.

Stand nach Roadmap-Schritt 8: Prozesstests laufen mit echtem Gotenberg und Mailpit als Testcontainers (`RenderAndMailContainers`, gleiche Images und Optionen wie im Compose); Mailpit nimmt nur Empfänger an, die mit `ok` beginnen, alle anderen lehnt es per SMTP ab. Fehlgeschlagene Jobs werden in den Prozesstests nach 1 s wiederholt (`R3/PT1S`). Der Job Executor läuft nur in Prozesstests und im Engine-Smoke-Test, sonst würden die offen gehaltenen Test-Kontexte um die Jobs konkurrieren. Abgedeckt sind jetzt auch 1 vollständig (Mail mit PDF im Postfach), 5 mit Betreiber-Mail, 7 (Empfänger scheitert: Retries, `FAILED`, Betreiber-Mail, `COMPLETED`; niemand erreicht: Incident) und 12 für Mails (der erreichte Empfänger bekommt bei drei Versuchen genau eine Mail). Unit-Tests: `TextShortenerTest`, `DigestViewFactoryTest`, `DigestRendererTest` (Escaping, keine externen Ressourcen, Links nur auf geprüfte Quellen), `LocalDigestStorageTest`; `GotenbergRenderingTest` schreibt die Vorschauen nach `target/pdf-preview/`.

## Bewertung von Prompts
- Beispielantworten echter Modelle (anonymisiert, ohne Keys) liegen als Fixtures in `src/test/resources/fixtures/research/` und `.../aggregate/`; automatische Tests prüfen Parsing, Prüfung und Rendering damit (seit Schritt 5: konstruierte Beispiele für gültige, umrahmte, kaputte und zu lange Antworten; echte Antworten kommen mit dem Eval in Schritt 9 dazu)
- Anbieter-Anbindung: `AnthropicNewsProviderTest` spricht gegen einen lokalen Stub der Messages-API (HTTP-Server des JDK) und prüft Anfrage (Modell, Max-Tokens, Temperatur nur wenn gesetzt, Websuche, API-Key) und Abbildung der Antwort (Text, Token, Websuchen, Stop-Grund), auch die Schätzung bei einer Antwort ohne Inhalt und `pause_turn`; keine neue Test-Abhängigkeit
- Eval-Profil `eval` (nur manuell, nie in der CI): führt die aktuellen Prompts gegen echte APIs für festgelegte Topics aus und schreibt Ergebnisse, Kosten und PDFs nach `data/eval/<prompt-version>/`, damit ich Versionen vergleichen kann. Umsetzung: `EvalService` startet die Topics (`newsdigest.eval.topics`) über den normalen Prozess, `EvalExporter` schreibt `summary.md`, `run.json`, Rohantworten, Suchen mit Treffern (`searches-<modell>.md`), Aggregat, Mail und PDF; Ablauf in `betrieb/lokaler-testlauf.md`. `EvalServiceTest` prüft das mit dem Fake-Anbieter
- Vor einer neuen Prompt-Version: Eval laufen lassen und Ergebnis mit der vorherigen Version vergleichen

## Coverage (Build schlägt darunter fehl)
| Bereich | Mindestwert |
|---|---|
| Backend gesamt (JaCoCo) | 80 % Zeilen, 70 % Branches |
| `service`, `process`, `scheduling`, `research`, `cost`, `aggregate` | 85 % Zeilen |
| Frontend `src/` ohne generierten Client (Vitest v8) | 80 % Zeilen |

- Ausnahmen nur für Records, reine Konfigurationsklassen, `main`-Klasse, generierten Code; jede Ausnahme explizit
- Berichte als CI-Artefakt und in der Job-Zusammenfassung
- Schwellen werden nicht ohne Rückfrage gesenkt
- Kein externer Dienst (Codecov, SonarCloud); Auswertung nur über die CI-Berichte. Nachrüsten später möglich

Schritt 14 (Prüfung der Meldungen): `NewsItemCheckerTest` (Reihenfolge der Status), `UrlCheckPolicyTest` (gesperrte und erlaubte Adressen nur als IP-Literale, keine DNS-Anfrage), `UrlCheckerTest` (lokaler Stub auf Loopback mit Test-Resolver: HEAD, GET mit Range, 404/410, 403 und 5xx erreichbar, Weiterleitungen bis zum Limit, Weiterleitung auf interne Adressen und Metadaten gesperrt, Timeout, geschlossener Port, Parallelität), `NewsCheckProcessTest` (nicht erreichbare Quelle und Meldung eines früheren Laufs erreichen den Aggregator nicht, Überschriften des vorigen Laufs im Prompt, Lauf nur mit alten Meldungen endet `NO_NEWS`). In allen Prozesstests ersetzt `FakeUrlChecker` die echte Prüfung (Host `unreachable.*` antwortet 404).
