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
| Mail | SES über die SMTP-Schnittstelle (`spring-boot-starter-mail`); SES-SMTP-Zugangsdaten im Secrets Manager; Absender-Domain mit SPF, DKIM und DMARC verifiziert |
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
- Fehlgeschlagenes Backup löst einen CloudWatch-Alarm aus

## Docker
- `Dockerfile` im Root, mehrstufig: Build mit JDK 21 + Maven-Wrapper, Runtime mit schlankem, fest gepinntem JRE-21-Image
- Spring-Boot-Layered-Jar, Nicht-Root-Benutzer, keine Secrets im Image, `HEALTHCHECK` gegen den internen Actuator
- Frontend-Build als eigenes Image oder Artefakt für Caddy
- `docker-compose.yml` für lokal: PostgreSQL, Mailpit (lokaler Mailserver zum Ansehen der Mails), Gotenberg, Keycloak mit Realm-Import und Testbenutzern (nur lokal); S3-Ersatz nur nach Rückfrage
- `docker-compose.prod.yml` für den Server: App, Caddy, PostgreSQL, Gotenberg, Keycloak, Backup-Job
- Instanzgröße: Speicherbedarf von App, Keycloak, PostgreSQL und Gotenberg zusammen beachten; nach dem ersten Deployment mit echten Werten prüfen
- Tags: Git-SHA, bei Releases zusätzlich Version; nie `latest`

## GitHub Actions (`.github/workflows/`)
### `ci.yml` – bei Pull Requests und Push auf `main`
1. Backend: JDK 21 mit Maven-Cache, `./mvnw -B clean verify`, JaCoCo-Prüfung, Berichte als Artefakt
2. Frontend: Node laut `.nvmrc`, `npm ci`, Lint, Typecheck, Tests mit Coverage, Build
3. Images bauen (ohne Push), Trivy-Scan; kritische Funde -> rot
4. Dependency Review, optional CodeQL
5. Formatierung (Spotless, Prettier) und Lint

### Abhängigkeiten aktuell halten
- `.github/dependabot.yml` für Maven, npm, Docker-Images (Dockerfile und Compose), GitHub Actions und Terraform-Provider, wöchentlich, gruppiert

### `cd.yml` – nach grüner CI auf `main` bzw. bei Release-Tag
1. Images bauen und nach Amazon ECR pushen
2. Umgebung `prod` mit manueller Freigabe (GitHub Environments); es gibt keine Staging-Umgebung, getestet wird lokal mit Docker Compose
3. Deployment per AWS Systems Manager (Run Command) auf der EC2-Instanz: neue Image-Tags setzen, `docker compose pull` und `up -d`; der alte App-Container wird kontrolliert heruntergefahren (laufende Jobs dürfen fertig werden) und vor dem Start des neuen gestoppt (kein doppelter Scheduler)
4. Smoke-Test über HTTPS (öffentlicher Health-Check von Caddy bzw. Startseite) und intern per SSM gegen Actuator
5. Bei Fehlschlag Rollback auf die vorherigen Image-Tags

### Regeln
- AWS-Anmeldung nur per OIDC (IAM-Rolle mit minimalen Rechten), keine AWS-Keys in GitHub
- App-Secrets im AWS Secrets Manager
- Actions auf Commit-SHA pinnen, minimale `permissions:`, Concurrency-Gruppe gegen parallele Deployments
- Flyway läuft beim App-Start; Migrationen abwärtskompatibel für Rollbacks
- Die App-Pipelines (`ci.yml`, `cd.yml`) legen keine Infrastruktur an; dafür gibt es `infra.yml` (siehe unten)
- Branch-Schutz und GitHub Environments richte ich selbst ein

## Infrastruktur als Code (Terraform, siehe ADR-011)
- Ordner `infra/terraform/`, Terraform- und Provider-Versionen fest gepinnt (`required_version`, `.terraform.lock.hcl` eingecheckt)
- Remote State in einem S3-Bucket (versioniert, verschlüsselt, mit State-Locking); den Bucket lege ich einmalig von Hand an (Bootstrap), alles andere entsteht per Terraform
- Inhalt: VPC bzw. Subnetz, Security Groups (nur 80/443 eingehend), EC2 mit Elastic IP und IAM-Rolle (minimale Rechte für S3, Secrets Manager, SES, CloudWatch, ECR, SSM), EBS-Volume für Daten, S3-Buckets (PDFs, Backups) mit Lifecycle-Regeln, ECR-Repositories, Secrets-Container, SES-Domain-Identität mit DKIM, Route 53 (Domain-Einträge), DLM-Snapshots, CloudWatch-Alarme und SNS, GitHub-OIDC-Provider mit Rollen für CD und Terraform
- Secrets: Terraform legt nur die leeren Secrets an; die Werte trage ich von Hand ein, damit sie nie im Terraform-State oder im Repo landen
- Kleine, lesbare Struktur: wenige Dateien nach Thema (`network.tf`, `compute.tf`, `storage.tf`, `security.tf`, `dns-mail.tf`, `monitoring.tf`, `github-oidc.tf`), Module erst, wenn sich etwas wiederholt
- Variablen ohne Secrets in `prod.tfvars` (eingecheckt), keine Zugangsdaten in Dateien
- Workflow `infra.yml`: bei Pull Requests mit Änderungen unter `infra/` `terraform fmt -check`, `validate`, `tflint` und `plan` (Ergebnis als Kommentar bzw. Job-Zusammenfassung, nur Leserechte); `apply` nur manuell gestartet und mit Freigabe über die Umgebung `prod`
- Ressourcen mit Daten (EBS, S3-Buckets) sind gegen versehentliches Löschen geschützt (`prevent_destroy`)

## Früh deployen
Ein minimales Deployment (App startet, HTTPS, DB, Health) wird direkt nach dem ersten Durchstich eingerichtet, nicht erst am Ende (siehe `13-roadmap.md`).

## Monitoring
Metriken mit Micrometer, Export nach CloudWatch:
| Metrik | Art | Tags |
|---|---|---|
| `digest.run` | Zähler | topic, status |
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
