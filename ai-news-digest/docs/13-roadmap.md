# 13 – Roadmap

Status: `[ ]` offen, `[~]` in Arbeit, `[x]` erledigt. Immer nur einen Schritt gleichzeitig. Jeder Schritt endet mit grüner CI und aktualisierter Doku.

Leitidee: erst ein schmaler Durchstich von Anfang bis Ende, dann früh deployen, dann in die Breite. Das größte Risiko ist die Qualität der KI-Ergebnisse, nicht die Technik.

## Phase 1 – Fundament
- [x] 1. Versionen prüfen und festschreiben (Spring Boot 4.x, passende CIB-seven-Starter mit Suffix `-4`, `cibseven-keycloak`, Spring AI 2.x, springdoc; siehe ADR-012); Grundgerüst: Spring-Boot-Projekt, Maven-Wrapper, `.gitignore`, `.editorconfig`, `.env.example`, Spotless, `docker-compose.yml` (zunächst PostgreSQL und Mailpit; Gotenberg kommt in Schritt 8, Keycloak in Schritt 20), Defaults-Klasse
- [x] 2. GitHub-Actions-CI (`ci.yml`): Build, Tests, JaCoCo-Schwellen, Formatierung; `dependabot.yml`
- [x] 3. Flyway V1: komplettes Datenmodell + Migrations-Tests
- [x] 4. CIB seven startet leer neben dem Flyway-Schema

## Phase 2 – Durchstich (ein Topic, ein Modell, lokal)
- [ ] 5. Ein Anbieter + ein Modell, Recherche-JSON, Prompt v1, Kostenberechnung
- [ ] 6. BPMN `digestRun` vollständig modelliert (inkl. Parallel-Gateways und paralleler Multi-Instance), zunächst mit einem Modell
- [ ] 7. Einfache Prüfung (Zeitraum, Duplikate im Lauf), Aggregator mit JSON-Vertrag
- [ ] 8. Gotenberg im Compose, einfaches PDF und HTML-Mail (noch ohne fertiges Design), Versand über Mailpit
- [ ] 9. Manueller Start per Dev-Endpunkt (nur Profil `dev`) oder Testlauf; erstes echtes Ergebnis ansehen und Prompts nachschärfen (Eval-Profil)

## Phase 3 – Früh deployen
- [ ] 10. Dockerfile, `docker-compose.prod.yml` mit Caddy, Trivy in der CI
- [ ] 11. Terraform-Grundlage (State-Bootstrap, `infra.yml`) und minimaler Betrieb auf AWS per Terraform (EC2, HTTPS, DB, Keycloak, Secrets, S3, SES, Domain); Caddy veröffentlicht bis Schritt 20 nur Platzhalter und Health-Check; Backup-Job; erster echter Mailversand; Wiederherstellung einmal üben
- [ ] 12. CD-Pipeline: ECR, SSM-Deployment, Freigabe, Smoke-Test, Rollback

## Phase 4 – Breite im Backend
- [ ] 13. Weitere Anbieter/Modelle, echte Parallelität (nicht-exklusive Jobs, Semaphore pro Anbieter), Parallelitäts-Tests
- [ ] 14. URL-Prüfung mit SSRF-Schutz, Duplikate gegen frühere Läufe, letzte Überschriften an den Aggregator
- [ ] 15. Budget-Gateway, Kostenauswertung
- [ ] 16. Scheduler aus der DB mit Reload; Abbrechen hängender Läufe; nächtlicher Aufräumjob (Aufbewahrungsfristen)
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
