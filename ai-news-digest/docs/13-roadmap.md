# 13 – Roadmap

Status: `[ ]` offen, `[~]` in Arbeit, `[x]` erledigt. Immer nur einen Schritt gleichzeitig. Jeder Schritt endet mit grüner CI und aktualisierter Doku.

Leitidee: erst ein schmaler Durchstich von Anfang bis Ende, dann früh deployen, dann in die Breite. Das größte Risiko ist die Qualität der KI-Ergebnisse, nicht die Technik.

## Phase 1 – Fundament
- [x] 1. Versionen prüfen und festschreiben (Spring Boot 4.x, passende CIB-seven-Starter mit Suffix `-4`, `cibseven-keycloak`, Spring AI 2.x, springdoc; siehe ADR-012); Grundgerüst: Spring-Boot-Projekt, Maven-Wrapper, `.gitignore`, `.editorconfig`, `.env.example`, Spotless, `docker-compose.yml` (zunächst PostgreSQL und Mailpit; Gotenberg kommt in Schritt 8, Keycloak in Schritt 20), Defaults-Klasse
- [x] 2. GitHub-Actions-CI (`ci.yml`): Build, Tests, JaCoCo-Schwellen, Formatierung; `dependabot.yml`
- [x] 3. Flyway V1: komplettes Datenmodell + Migrations-Tests
- [x] 4. CIB seven startet leer neben dem Flyway-Schema

## Phase 2 – Durchstich (ein Topic, ein Modell, lokal)
- [x] 5. Ein Anbieter + ein Modell, Recherche-JSON, Prompt v1, Kostenberechnung
- [x] 6. BPMN `digestRun` vollständig modelliert (inkl. Parallel-Gateways und paralleler Multi-Instance), zunächst mit einem Modell
- [x] 7. Einfache Prüfung (Zeitraum, Duplikate im Lauf), Aggregator mit JSON-Vertrag
- [x] 8. Gotenberg im Compose, einfaches PDF und HTML-Mail (noch ohne fertiges Design), Versand über Mailpit
- [x] 9. Manueller Start per Dev-Endpunkt (nur Profil `dev`) oder Testlauf; erstes echtes Ergebnis ansehen und Prompts nachschärfen (Eval-Profil)
  - [x] 9a. Werkzeug: Kosten bei Antworten ohne Inhalt geschätzt, `pause_turn` abgefangen (offener Punkt 6, ADR-014), Dev-Endpunkt, Eval-Profil mit Export, Vorlagen und Runbook `betrieb/lokaler-testlauf.md`
  - [x] 9b. Erster echter Lauf (durch mich), Auswertung, Prompts als `v2` nachschärfen, anonymisierte echte Antworten als Fixtures
  - [x] 9c. Prompts und Ausgabe auf Englisch (ADR-017): Recherche `v4`, Aggregation `v3`, Reparatur `v2`; PDF und Mail in der Ausgabesprache (Default `en`); nach Lauf 5 Recherche `v5` und Aggregation `v4` für bessere Quellen (Lauf 6: nur Primärquellen, aber 3 Meldungen, 1,49 USD mit Sonnet und Opus), dann Recherche `v6` (Lauf 7: Primärquellen, 0,59 USD, aber weiter nur 3 Meldungen); offen: Diagnose der Suchen im Eval-Export, danach mehr Abdeckung
    - Vier Läufe mit Claude Sonnet 5.5 (Topic "KI-News", 17.–24.11.2026), Vergleich in `src/main/resources/prompts/README.md`: Recherche `v1` 0 Meldungen, `v2` 3 gültige nur von Trackern, `v3` mit 5 Suchen 1, `v3` mit 10 Suchen 4 gültige aus echten Artikeln (0,41 USD)
    - Neue Defaults: Recherche `v3`, Aggregation `v2`, 10 Websuchen pro Aufruf
    - Gefunden und behoben: Der Eval-Runner blockierte den Start des Job Executors; Eval-Export nach `data/eval` (überlebt `mvn clean`); Startmeldung mit Prozessinstanz im Log
    - Fixtures `research/real-claude-v3.json` und `aggregate/real-claude-v2.json` (nur öffentliche URLs und Nachrichten)
- [x] 10. Dockerfile, `docker-compose.prod.yml` mit Caddy, Trivy in der CI
- [x] 11. Terraform-Grundlage (State-Bootstrap, `infra.yml`) und minimaler Betrieb auf AWS per Terraform (EC2, HTTPS, DB, Secrets, S3, SES, Domain); Caddy veröffentlicht bis Schritt 20 nur Platzhalter und Health-Check; Backup-Job; erster echter Mailversand; Wiederherstellung einmal üben. Keycloak kommt erst mit Schritt 20 auf den Server (ohne JWT-Prüfung kostet er nur Speicher und vergrößert die Angriffsfläche)
  - [x] 11a. App an AWS anbinden: AWS SDK v2, Secrets aus dem Secrets Manager mit Cache, PDFs in S3, Profil `prod` mit SES-SMTP (STARTTLS), Region `eu-central-1` (ADR-015)
  - [x] 11b. Infrastruktur als Code: `infra/terraform/` (Netz, EC2 `t4g.medium`, EBS, S3, ECR, leere Secrets, SES mit DKIM, Route 53, DLM, SNS, Budget-Alarm, GitHub-OIDC), `infra.yml`, Deploy-Skript für Start-Secrets, Backup-Job, Log-Treiber `awslogs`, Runbook Bootstrap
  - [x] 11c. Erstes Deployment (durch mich, begleitet): State-Bucket, Apply, Secrets eintragen, Image nach ECR, Start per SSM, HTTPS, erster echter Mailversand, Wiederherstellung üben
- [x] 12. CD-Pipeline: ECR, SSM-Deployment, Freigabe, Smoke-Test, Rollback


## Phase 4 – Breite im Backend
- [~] 13. Weitere Anbieter/Modelle, echte Parallelität (nicht-exklusive Jobs, Semaphore pro Anbieter), Parallelitäts-Tests
  - [x] 13a. Echte Parallelität mit einem zweiten Anthropic-Modell (Claude Haiku 5.5): Semaphore pro Anbieter (`ProviderCallLimiter`), Job-Executor passend konfiguriert, Parallelitäts-Tests. Gefunden und behoben: die Async-Attribute standen am Task statt an der Multi-Instance, die Recherche lief deshalb bisher nacheinander; ohne `asyncAfter` der Instanzen wurde bei der Kollision am Join das Modell doppelt aufgerufen; Eval-Lauf 10 (Sonnet und Haiku gleichzeitig) zeigte versteckte SDK-Wiederholungen nach dem Timeout: jetzt `maxRetries` 0, Timeout 300 s, Sperrzeit 20 min (ADR-014, Nachtrag)
  - [ ] 13b. Zweiter Anbieter (Auswahl offen, offener Punkt 4)
- [ ] 14. URL-Prüfung mit SSRF-Schutz, Duplikate gegen frühere Läufe, letzte Überschriften an den Aggregator
- [ ] 15. Budget-Gateway, Kostenauswertung
- [ ] 16. Scheduler aus der DB mit Reload; Abbrechen hängender Läufe; nächtlicher Aufräumjob (Aufbewahrungsfristen); Instanz nur zum Lauf hochfahren (EventBridge-Schedule in Terraform startet die EC2-Instanz vor dem frühesten Topic-Zeitplan und stoppt sie nach dem Lauf, Backup beim Start, Alarm `backup-missing` ohne Fehlalarm bei gestoppter Instanz, Runbook; Ziel etwa 12 statt 40 USD im Monat)
- [ ] 17. PDF im finalen Infografik-Design (Gotenberg, Icon-Satz und Schrift festlegen), HTML-Mail im gleichen Stil
- [ ] 18. Metriken, CloudWatch-Alarme, SES-Bounce-Meldungen

## Phase 5 – API und Security
- [ ] 19. REST-API mit Records, Validierung, `GlobalExceptionHandler`
- [ ] 20. Security: Keycloak (Compose lokal und Produktion, Realm-Datei), JWT-Prüfung, Rollen, Engine-Endpunkte sperren, Actuator intern, Security-Tests; danach API über Caddy freigeben

## Phase 6 – Frontend
- [ ] 21. UI-Design in `08-ui-design.md` ausgefüllt (durch mich)
- [ ] 22. React-Grundgerüst: Routing, Theme aus Design-Tokens, Auth, generierter API-Client, Frontend-CI, Auslieferung über Caddy
- [ ] 23. Konfigurationsseiten: Topics, Zeitpläne, Anbieter und Modelle, Empfänger, Budget
- [ ] 24. Dashboard, Läufe, Kosten
- [ ] 25. Engine-UI Stufe 1: neuer Tab, Single Sign-On über Keycloak mit `cibseven-keycloak`

## Später
- Engine-UI Stufe 2 (eigene React-Ansichten)
- Weitere Anbieter
