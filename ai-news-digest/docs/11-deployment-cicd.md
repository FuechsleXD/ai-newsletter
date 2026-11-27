# 11 – Docker, CI/CD, AWS und Monitoring

## Hosting ohne Load Balancer (entschieden, siehe ADR-005)
| Baustein | Lösung |
|---|---|
| Server | Eine EC2-Instanz (klein, ARM/Graviton) mit Docker Compose |
| HTTPS | Caddy als Reverse Proxy im Compose-Verbund, automatisches Let's-Encrypt-Zertifikat, Elastic IP + DNS-Eintrag |
| Frontend | Statische Dateien, von Caddy ausgeliefert |
| PDF | Gotenberg-Container (Version gepinnt), nur im internen Docker-Netz erreichbar |
| Anmeldung | Keycloak-Container (Version gepinnt) hinter Caddy unter `/auth`, Datenbank `keycloak` in derselben PostgreSQL-Instanz |
| Datenbank | PostgreSQL-Container auf der EC2 (Version fest gepinnt), Daten auf eigenem verschlüsseltem EBS-Volume, nicht nach außen veröffentlicht; Backups siehe unten |
| PDFs | S3 (privat), Zugriff über IAM-Rolle der Instanz |
| Mail | SES über die SMTP-Schnittstelle (`spring-boot-starter-mail`, Port 587, STARTTLS Pflicht, Profil `prod`); SES-SMTP-Zugangsdaten im Secrets Manager, vom Deploy-Skript als Umgebungsvariablen übergeben; Absender-Domain mit SPF, DKIM und DMARC verifiziert; Start in der SES-Sandbox mit verifizierten Empfängern, Produktionszugang parallel beantragt (ADR-015) |
| Secrets | AWS Secrets Manager (Key Vault), Zugriff nur über die IAM-Rolle der Instanz; Details in `06-security.md` |
| KI-Abrechnung | Eigenes Claude-Console-Konto mit Prepaid-Guthaben (getrennt vom Claude-Pro-Abo); API-Key nur im Secrets Manager |
| Zugriff auf den Server | AWS Systems Manager (kein offener SSH-Port) |
| Logs, Metriken, Alarme | CloudWatch |

- Genau eine App-Instanz: der Scheduler läuft nur einmal; der Teilindex in der DB schützt zusätzlich
- Öffentlich nur Ports 80/443 (Caddy); Backend, Actuator und DB nur im Docker-Netz bzw. privat
- Kosten vor dem Aufbau mit dem AWS-Preisrechner schätzen
- Solange die Security (Roadmap Schritt 20) nicht umgesetzt ist, veröffentlicht Caddy nur eine statische Platzhalterseite und den Health-Check; API, Engine-UI und Dev-Endpunkte sind von außen nicht erreichbar
- Container-Logs gehen per Docker-Log-Treiber `awslogs` nach CloudWatch (Aufbewahrung siehe Defaults)
- Betriebssystem-Updates der Instanz über AWS Systems Manager Patch Manager (wöchentliches Wartungsfenster)
- SES: Bounces und Beschwerden werden per SNS an den Betreiber gemeldet; betroffene Empfänger deaktiviere ich manuell

## Datenbank-Backups
- Täglich `pg_dump` (beide Schemas) per Zeitplan auf der Instanz, verschlüsselt nach S3; Aufbewahrung 30 Tage über eine S3-Lifecycle-Regel
- Zusätzlich tägliche EBS-Snapshots über den Data Lifecycle Manager, Aufbewahrung 7 Tage
- Wiederherstellung ist als Runbook in `docs/betrieb/wiederherstellung.md` beschrieben und wird einmal nach dem ersten Deployment ausprobiert
- Fehlgeschlagenes Backup löst einen CloudWatch-Alarm aus: `infra/server/backup.sh` meldet jeden Erfolg als Metrik `NewsDigest/BackupSucceeded`, fehlt sie 24 Stunden, alarmiert `newsdigest-prod-backup-missing`
- Umsetzung: systemd-Timer `newsdigest-backup.timer` (03:00 UTC), eingerichtet von `deploy.sh`; Snapshots um 02:00 UTC

## Docker
- `Dockerfile` im Projektordner, mehrstufig: Build mit `eclipse-temurin` JDK 21 (Alpine) und Maven-Wrapper ohne Tests (die laufen im CI-Job `backend`), Runtime mit `eclipse-temurin` JRE 21 (Alpine); beide Tags fest gepinnt und für amd64 und arm64 (Graviton) verfügbar
- Spring-Boot-Layered-Jar (`-Djarmode=tools extract --layers`), Nicht-Root-Benutzer `app` (UID 10001), keine Secrets im Image (`.dockerignore` lässt nur Wrapper, `pom.xml` und `src/main` durch), `HEALTHCHECK` gegen den internen Actuator, `-XX:MaxRAMPercentage=75`
- Frontend-Build als eigenes Image oder Artefakt für Caddy
- `docker-compose.yml` für lokal: PostgreSQL, Mailpit (lokaler Mailserver zum Ansehen der Mails), Gotenberg, Keycloak mit Realm-Import und Testbenutzern (nur lokal); S3-Ersatz nur nach Rückfrage
- `docker-compose.prod.yml` für den Server: App (Profil `prod`: KI-Keys aus dem Secrets Manager, PDFs in S3, SES-SMTP), Caddy, PostgreSQL, Gotenberg; Backup-Job und Log-Treiber `awslogs` folgen in Schritt 11b, Keycloak in Schritt 20
  - Werte aus einer nicht eingecheckten Env-Datei (Vorlage `.env.prod.example`), Pflichtwerte mit `${VAR:?}`; das App-Image kommt per `APP_IMAGE` mit festem Tag
  - Netze: `edge` (Caddy, App; Internet für KI-Anbieter und Mail) und `backend` (`internal: true`: App, PostgreSQL, Gotenberg ohne Internetzugang); veröffentlicht sind nur 80/443 von Caddy
  - Caddy (`docker/caddy/Caddyfile`): HTTPS für `DOMAIN`, `/health` auf `/actuator/health` der App, alles andere auf die Platzhalterseite `docker/caddy/site/`; Security-Header (HSTS, CSP, `nosniff`, `DENY`)
  - Lokal ausprobieren: `docs/betrieb/prod-compose-lokal.md`
- Instanzgröße: Speicherbedarf von App, Keycloak, PostgreSQL und Gotenberg zusammen beachten; nach dem ersten Deployment mit echten Werten prüfen
- Tags: Git-SHA, bei Releases zusätzlich Version; nie `latest`

## GitHub Actions (`.github/workflows/`)
Workflows und `dependabot.yml` liegen im Repo-Root (`.github/`), weil GitHub sie nur dort liest; das Projekt selbst liegt im Unterordner `ai-news-digest/`, die Jobs arbeiten mit `working-directory: ai-news-digest`.

### `ci.yml` – bei Pull Requests und Push auf `main`
Stand nach Roadmap-Schritt 2: Job `backend` (Punkt 1 und 5, Formatierung über Spotless in `verify`, Coverage-Tabelle in der Job-Zusammenfassung, Berichte als Artefakt `backend-reports`) und Job `dependency-review` (Punkt 4, nur bei Pull Requests, schlägt ab Schweregrad `high` fehl). Seit Schritt 10 zusätzlich Job `image` (Punkt 3): App-Image bauen (ohne Push), Trivy-Scan (fixbare `CRITICAL`-Funde im App-Image machen die CI rot; `HIGH` des App-Images und die fremden Images aus `docker-compose.prod.yml` nur als Bericht in der Job-Zusammenfassung, weil sie nur ein Image-Update behebt) und Smoke-Test `docker/smoke-test.sh` (Compose-Verbund mit `DOMAIN=localhost` starten, `/health` und Platzhalter erreichbar, Actuator, API und Dev-Endpunkte nicht). Frontend (Punkt 2) folgt in Schritt 22, CodeQL vorerst nicht.

1. Backend: JDK 21 mit Maven-Cache, `./mvnw -B clean verify`, JaCoCo-Prüfung, Berichte als Artefakt
2. Frontend: Node laut `.nvmrc`, `npm ci`, Lint, Typecheck, Tests mit Coverage, Build
3. Images bauen (ohne Push), Trivy-Scan; kritische Funde -> rot
4. Dependency Review, optional CodeQL
5. Formatierung (Spotless, Prettier) und Lint

### Abhängigkeiten aktuell halten
- `.github/dependabot.yml` für Maven, npm, Docker-Images (Dockerfile und Compose), GitHub Actions und Terraform-Provider, wöchentlich, gruppiert
- Eingerichtet sind bisher Maven, GitHub Actions, Compose (beide Compose-Dateien) und Dockerfile (ohne Sprünge der Java-Hauptversion); npm und Terraform kommen dazu, sobald es die Ordner gibt (Schritte 22, 11)
- Ignoriert werden (ADR-012): Spring-Boot-Sprünge über 4.1.x hinaus, solange CIB seven keine neuere Linie unterstützt, und Jackson 3.2+
- Ebenfalls ignoriert: neue Hauptversionen von PostgreSQL; ein Wechsel braucht Dump und Restore der Daten und wird bewusst geplant (Image-Tag in Compose, Testcontainers und Produktion gemeinsam ändern)

### `cd.yml` – nach grüner CI auf `main` bzw. bei Release-Tag
1. Images bauen und nach Amazon ECR pushen
2. Umgebung `prod` mit manueller Freigabe (GitHub Environments); es gibt keine Staging-Umgebung, getestet wird lokal mit Docker Compose
3. Deployment per AWS Systems Manager (Run Command) auf der EC2-Instanz: neue Image-Tags setzen, `docker compose pull` und `up -d`; der alte App-Container wird kontrolliert heruntergefahren (laufende Jobs dürfen fertig werden) und vor dem Start des neuen gestoppt (kein doppelter Scheduler)
4. Smoke-Test über HTTPS (öffentlicher Health-Check von Caddy bzw. Startseite) und intern per SSM gegen Actuator
5. Bei Fehlschlag Rollback auf die vorherigen Image-Tags

Stand nach Roadmap-Schritt 12: `cd.yml` startet nach grüner CI eines Pushes auf `main` (`workflow_run`) oder von Hand (`workflow_dispatch`, optional mit einem Image-Tag aus ECR für Redeploy und Rollback). Ein einziger Job in der Umgebung `prod` (Freigabe vor jedem Schritt mit AWS-Zugriff) auf einem nativen arm64-Runner: laufendes Tag per SSM vom Server lesen (gestoppte Instanz: Abbruch, kein automatischer Start), Image als kurzer Commit-SHA bauen und pushen (vorhandene Tags werden nicht neu gebaut, ECR-Tags sind unveränderlich), Deploy-Dateien nach `current/` und `releases/<tag>/`, `deploy.sh` per SSM, Smoke-Test über HTTPS (`infra/cd/smoke-test-prod.sh`), bei Fehlschlag Rollback auf das vorher laufende Tag mit dessen Deploy-Dateien. Die interne Prüfung gegen Actuator übernimmt `docker compose up --wait` über den Health-Check des App-Containers. Einstellungen als Repository-Variablen, Bedienung im Runbook `docs/betrieb/deployment.md`.

### Regeln
- AWS-Anmeldung nur per OIDC (IAM-Rolle mit minimalen Rechten), keine AWS-Keys in GitHub
- App-Secrets im AWS Secrets Manager
- Actions auf Commit-SHA pinnen, minimale `permissions:`, Concurrency-Gruppe gegen parallele Deployments
- Flyway läuft beim App-Start; Migrationen abwärtskompatibel für Rollbacks
- Die App-Pipelines (`ci.yml`, `cd.yml`) legen keine Infrastruktur an; dafür gibt es `infra.yml` (siehe unten)
- Branch-Schutz und GitHub Environments richte ich selbst ein

## Infrastruktur als Code (Terraform, siehe ADR-011)
- Ordner `infra/terraform/` (im Projektordner), Terraform 1.17 und AWS-Provider fest gepinnt (`required_version`, `.terraform.lock.hcl` mit Hashes für Linux und Windows eingecheckt)
- Remote State im S3-Bucket `newsdigest-tfstate-172033175227` (versioniert, verschlüsselt, natives S3-Locking über `use_lockfile`, kein DynamoDB); den Bucket lege ich einmalig von Hand an, alles andere entsteht per Terraform (`docs/betrieb/aws-bootstrap.md`)
- Inhalt: VPC bzw. Subnetz, Security Groups (nur 80/443 eingehend), EC2 mit Elastic IP und IAM-Rolle (minimale Rechte für S3, Secrets Manager, SES, CloudWatch, ECR, SSM), EBS-Volume für Daten, S3-Buckets (PDFs, Backups) mit Lifecycle-Regeln, ECR-Repositories, Secrets-Container, SES-Domain-Identität mit DKIM, Route 53 (Domain-Einträge), DLM-Snapshots, CloudWatch-Alarme und SNS, GitHub-OIDC-Provider mit Rollen für CD und Terraform
- Secrets: Terraform legt nur die leeren Secrets an; die Werte trage ich von Hand ein, damit sie nie im Terraform-State oder im Repo landen
- Kleine, lesbare Struktur: wenige Dateien nach Thema (`network.tf`, `compute.tf`, `storage.tf`, `security.tf`, `dns-mail.tf`, `monitoring.tf`, `github-oidc.tf`), Module erst, wenn sich etwas wiederholt
- Variablen ohne Secrets in `prod.tfvars` (eingecheckt), keine Zugangsdaten in Dateien
- Workflow `infra.yml`: bei Pull Requests mit Änderungen unter `infra/` `terraform fmt -check`, `validate`, `tflint` (AWS-Ruleset) und `shellcheck` für die Server-Skripte; `plan` mit der Leserolle, sobald die Variable `AWS_PLAN_ROLE_ARN` gesetzt ist (Ergebnis nur im Job-Log, weil das Repo öffentlich ist; sensible Werte sind verborgen); `apply` nur manuell (`workflow_dispatch`) mit Freigabe über die Umgebung `prod`
- Server (ADR-016): Amazon Linux 2023 arm64, IMDSv2 Pflicht, Datenvolume als `/var/lib/docker`, Docker und Compose-Plugin beim ersten Start (`templates/user-data.sh.tftpl`); Deploy-Dateien je Release im Deploy-Bucket (`infra/server/upload-deploy-files.sh`), `infra/server/deploy.sh <tag>` holt sie, liest die Start-Secrets nach `/run` und startet `docker-compose.prod.yml` mit dem Override `docker-compose.aws.yml` (Log-Treiber `awslogs`)
- AWS-Kosten: Budget 40 USD im Monat mit Meldung bei 80 % (tatsächlich) und 100 % (Prognose); Alarme und Budget-Meldungen an die Adresse aus `TF_VAR_alarm_email` bzw. dem GitHub-Secret `ALARM_EMAIL`, nie im Repo
- Ressourcen mit Daten (EBS, S3-Buckets) sind gegen versehentliches Löschen geschützt (`prevent_destroy`)

## Früh deployen
Ein minimales Deployment (App startet, HTTPS, DB, Health) wird direkt nach dem ersten Durchstich eingerichtet, nicht erst am Ende (siehe `13-roadmap.md`).

## Monitoring
Metriken mit Micrometer, Export nach CloudWatch:
| Metrik | Art | Tags |
|---|---|---|
| `digest.run` | Zähler | topic, status |
| `digest.run.skipped` | Zähler | topic, reason (`already_running`); seit Schritt 16 |
| `digest.run.duration` | Timer | topic |
| `ai.call.duration` | Timer | provider, model, phase (research/aggregate), outcome |
| `ai.tokens` | Zähler | provider, model, direction (input/output) |
| `ai.cost.usd` | Zähler | provider, model |
| `news.items` | Zähler | status (VALID, UNREACHABLE, ...) |
| `mail.delivery` | Zähler | outcome |
| `bpm.incidents.open` | Gauge | – |

Alarme (CloudWatch, Benachrichtigung per Mail über SNS):
- Offener Incident in der Engine
- Lauf mit Status `FAILED`
- Budget-Warnschwelle erreicht
- App nicht erreichbar (Health-Check)

Hinweis: Die Fehler-Mail aus dem Prozess ersetzt keinen Alarm, weil sie selbst fehlschlagen kann.
