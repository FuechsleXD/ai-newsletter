# ADR-016: Aufbau des Servers und Ablauf eines Deployments

- **Status:** Angenommen
- **Datum:** 2026-11-24
- **Entscheidet:** offenen Punkt 2 (Domainname)

## Kontext
Für die Infrastruktur als Code (Roadmap 11b) waren offen: Domain, wo die Daten auf der Instanz liegen, wie Compose-Dateien und Start-Secrets auf den Server kommen und welche Rechte die GitHub-Rollen bekommen.

## Entscheidung
- **Domain:** `fuechslexd.com`, registriert in Route 53. Die Registrierung legt die Hosted Zone an; Terraform liest sie nur und legt die Einträge an (A, CAA, DKIM, MAIL FROM, DMARC)
- **Daten:** eigenes verschlüsseltes EBS-Volume, eingehängt als `/var/lib/docker`; damit liegen Datenbank, Zertifikate und Images auf einem Volume, das täglich per DLM gesichert wird. Das Root-Volume enthält nur das Betriebssystem (Amazon Linux 2023, arm64)
- **Deploy-Dateien:** Compose-Dateien, Caddyfile, Init-Skript und Server-Skripte liegen je Release in einem eigenen S3-Bucket (`current/`); `deploy.sh` holt sie, kein Git auf dem Server
- **Start-Secrets:** `deploy.sh` liest sie aus dem Secrets Manager und schreibt die Env-Datei nach `/run` (nur im Arbeitsspeicher); Container behalten ihre Umgebung bei Neustarts
- **Logs:** Log-Treiber `awslogs` über das Override `docker-compose.aws.yml`, das nur auf dem Server verwendet wird (Smoke-Test und CI bleiben ohne AWS)
- **Backup:** systemd-Timer mit `pg_dump` nach S3; jeder Erfolg wird als Metrik gemeldet, fehlt sie einen Tag, gibt es einen Alarm
- **GitHub-Rollen:** `terraform-plan` nur lesend (ohne Zugriff auf PDFs, Dumps und Secret-Werte), `cd` minimal (ECR-Push, Deploy-Bucket, `SendCommand` auf die Instanz), `terraform-apply` mit `AdministratorAccess`, aber nur in der Umgebung `prod` mit meiner Freigabe
- **Alarmadresse:** nie im Repo; Terraform-Variable `sensitive`, lokal `TF_VAR_alarm_email`, in der CI das Secret `ALARM_EMAIL` (das Repo ist öffentlich, Logs sind lesbar)

## Alternativen
- Hosted Zone in Terraform anlegen: kollidiert mit der Zone, die die Registrierung anlegt
- Nur das PostgreSQL-Verzeichnis auf ein eigenes Volume: Zertifikate und Images lägen dann auf dem Root-Volume, das nicht gesichert wird
- Git auf dem Server: braucht Zugangsdaten zum Repo oder ein öffentliches Klonen ohne Integritätsprüfung
- Minimale Rechte für `terraform-apply`: viel Pflege, bricht bei jeder neuen Ressource

## Konsequenzen
- Die Domain muss registriert sein, bevor `plan` läuft
- Ein neues AMI oder ein geänderter Bootstrap ersetzt die Instanz nicht (`ignore_changes`); ein Austausch ist eine bewusste Entscheidung
- Wiederherstellung aus Snapshot: neues Volume aus dem Snapshot statt des alten einhängen (`docs/betrieb/wiederherstellung.md`)
