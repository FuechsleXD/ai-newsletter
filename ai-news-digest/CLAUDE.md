# Projekt: AI News Digest

Backend + React-UI, das mehrere KI-Modelle parallel und in einstellbarem Rhythmus nach News zu frei definierbaren Topics fragt, die Meldungen prüft (Zeitraum, Quellen, Duplikate), sie von einem Aggregator-LLM zusammenfassen lässt und daraus eine HTML-Mail mit einseitiger PDF-Infografik erzeugt. Der Ablauf ist ein BPMN-Prozess in CIB seven. Kosten werden erfasst und durch ein Monatsbudget begrenzt. Läuft auf AWS ohne Load Balancer.

Lernziel: BPM (CIB seven) sinnvoll mit KI-Aufrufen verknüpfen.

## Dokumentation – vor jeder Arbeit lesen
Die Details stehen in `docs/`. Lies vor einer Aufgabe die passende Datei, nicht alle auf einmal. Einstieg: `docs/README.md`.

| Bereich | Datei |
|---|---|
| Ziel, Scope, Ablauf | `docs/01-fachkonzept.md` |
| Architektur, Packages, KI-Anbindung | `docs/02-architektur.md` |
| Datenmodell, Flyway | `docs/03-datenmodell.md` |
| BPMN-Prozess | `docs/04-prozess-bpmn.md` |
| REST-API, Fehlerbehandlung | `docs/05-api.md` |
| Security | `docs/06-security.md` |
| Frontend (Technik, Seiten, Engine-UI) | `docs/07-frontend.md` |
| UI-Design (Look & Feel) | `docs/08-ui-design.md` |
| PDF- und Mail-Design | `docs/09-pdf-design.md` |
| Tests, Coverage | `docs/10-tests.md` |
| Docker, CI/CD, AWS | `docs/11-deployment-cicd.md` |
| Defaults | `docs/12-defaults.md` |
| Umsetzungsplan / Status | `docs/13-roadmap.md` |
| Offene Entscheidungen | `docs/offene-punkte.md` |
| Getroffene Entscheidungen (ADRs) | `docs/entscheidungen/` |
| Prompts und Platzhalter | `src/main/resources/prompts/README.md` |
| Betrieb (Runbooks) | `docs/betrieb/` |

## Tech-Stack (verbindlich)
- Java 21 (LTS), Spring Boot 4.x (bevorzugt 4.1, sonst die neueste 4.x-Linie, die CIB seven unterstützt; Spring Boot 3.5 ist seit Juni 2026 ohne kostenlose Sicherheitsupdates) – Versionswechsel nur nach Rückfrage, siehe ADR-012
- Maven ausschließlich über den Wrapper (`./mvnw`)
- CIB seven eingebettet mit den Spring-Boot-4-Startern (ab CIB seven 3.0 die Artefakte ohne Suffix, z. B. `cibseven-bpm-spring-boot-starter`; die `-4`-Artefakte sind dort nur Relocations), Version passend zur Spring-Boot-Version laut Kompatibilitätstabelle (NICHT Camunda 7 CE, End of Life); Maven-Koordinaten in der CIB-seven-Doku prüfen, nicht raten; siehe ADR-012
- PostgreSQL + Flyway, Hibernate `ddl-auto=validate`
- Spring Data JPA, Spring Web, Spring Security (OAuth2 Resource Server, JWT), springdoc-openapi, Spring AI 2.x (setzt Spring Boot 4 voraus), Spring Mail (SMTP); Jackson 3
- Keycloak als Identity Provider (eigener Container)
- Frontend: React + TypeScript (strict) + Vite + shadcn/ui + Tailwind in `frontend/`
- Docker, GitHub Actions, AWS ohne Load Balancer (EC2 + Docker Compose + Caddy, S3, SES, Secrets Manager, CloudWatch), Infrastruktur mit Terraform in `infra/terraform/`
- Micrometer für Metriken
- PDF: Thymeleaf-HTML, gerendert von Gotenberg (Chromium-Container)
- Neue Dependencies nur nach Rückfrage

## Befehle
- Lokale Dienste (PostgreSQL, Mailpit, Gotenberg, Keycloak): `docker compose up -d` (Mails ansehen im Mailpit-Webinterface)
- Build + Tests + Coverage: `./mvnw clean verify` (Docker muss laufen, Testcontainers)
- Nur Tests: `./mvnw test`
- Lokal starten: `./mvnw spring-boot:run -Dspring-boot.run.profiles=dev`
- Frontend (in `frontend/`): `npm ci`, `npm run dev`, `npm run lint`, `npm test`, `npm run build`
- Image lokal: `docker build -t newsdigest:local .`

## Goldene Regeln (gelten immer)
1. BPMN beschreibt den Ablauf, die Datenbank die Konfiguration. Modelle, Reihenfolgen, Empfänger und Zeitpläne werden NIE im BPMN oder im Code fest verdrahtet.
2. Recherche läuft parallel (Parallel-Gateways + parallele Multi-Instance); der Aggregator startet erst, wenn alle Recherchen beendet sind. Parallele Zweige schreiben nie in gemeinsame Prozessvariablen.
3. Alles, was von einem LLM kommt, ist nicht vertrauenswürdig: URLs nur mit SSRF-Schutz aufrufen, Texte in HTML/PDF escapen.
4. API-Keys und andere Secrets nur im AWS Secrets Manager; in der DB steht nur der Secret-Name.
5. Kein KI-Aufruf ohne Kostenerfassung; das Monatsbudget wird vor jedem Lauf geprüft.
6. Prompts nur als neue Version ändern, nie bestehende Versionen überschreiben.
7. DTOs sind Java Records; Entities werden nie direkt ausgegeben.
8. Fehler zentral über einen `@RestControllerAdvice` als RFC-9457 Problem Details.
9. Keine Secrets, API-Keys oder echten Mailadressen in Code, Repo, Logs oder DB-Migrationen.
10. Keine echten KI-, Mail-, S3- oder externen HTTP-Aufrufe in automatischen Tests (echte Aufrufe nur im Profil `eval`, manuell).
11. Coverage-Schwellen dürfen nicht gesenkt werden, ohne mich zu fragen.
12. Angewendete Flyway-Migrationen werden nie verändert.
13. Code und Kommentare auf Englisch, Doku und UI-Texte auf Deutsch.
14. Constructor Injection, kein Field Injection.

## Arbeitsweise
- Bei unklaren Anforderungen oder Punkten aus `docs/offene-punkte.md` erst nachfragen, dann bauen
- Nach `docs/13-roadmap.md` vorgehen, immer genau einen Schritt pro Auftrag, in diesem Ablauf:
  1. Ersten offenen Schritt (`[ ]`) nennen, passende Doku lesen, auf `[~]` setzen
  2. Kurzen Plan zeigen (Dateien, Tests, offene Fragen) und auf mein OK warten; offene Punkte aus `docs/offene-punkte.md`, die den Schritt betreffen, vorher klären
  3. Umsetzen inkl. Tests; alle Prüfungen laufen lassen und grün bekommen
  4. Doku anpassen, Schritt auf `[x]` setzen
  5. Zusammenfassung: was gebaut wurde, wie ich es ausprobieren kann, was offen ist
  6. STOPP: nicht mit dem nächsten Schritt beginnen, bis ich ihn ausdrücklich freigebe
- Jede Änderung mit Tests; vor Abschluss `./mvnw clean verify` (und bei Frontend-Änderungen die npm-Prüfungen) grün
- Wenn sich eine Entscheidung ändert: passende Datei in `docs/` aktualisieren und bei Architekturfragen ein ADR in `docs/entscheidungen/` anlegen
- Kleine, nachvollziehbare Schritte; nichts löschen oder umbenennen, ohne es zu erwähnen
- Git: Arbeit auf Feature-Branches, Merge nach `main` nur per Pull Request mit grüner CI; Commit-Nachrichten nach Conventional Commits (`feat:`, `fix:`, `docs:` ...); committen und pushen nur, wenn ich darum bitte
- Code-Stil wird automatisch geprüft (siehe `docs/02-architektur.md`, Abschnitt "Code-Stil"); Formatierungsfehler lassen die CI fehlschlagen
