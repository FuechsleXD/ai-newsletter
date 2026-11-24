# ADR-015: AWS-Anbindung der App, Region und Instanz

- **Status:** Angenommen
- **Datum:** 2026-11-24
- **Entscheidet:** offenen Punkt 3 (SES-Sandbox)

## Kontext
Für den Betrieb auf AWS (Roadmap Schritt 11) muss die App API-Keys aus dem AWS Secrets Manager lesen und PDFs in S3 ablegen. Außerdem fehlten Region, Instanzgröße und der Umgang mit der SES-Sandbox.

## Entscheidung
- **Bibliothek:** AWS SDK for Java v2 direkt (Module `secretsmanager` und `s3`, Version über die BOM gepinnt) mit dem schlanken `url-connection-client`; keine Spring-Cloud-AWS-Abhängigkeit, deren Unterstützung für Spring Boot 4.1 erst geprüft werden müsste
- **Umschaltung über Properties, gesetzt vom Profil `prod`:** `newsdigest.secrets.source` (`environment` | `aws`) und `newsdigest.storage.type` (`local` | `s3`). Lokal und in Tests bleibt alles wie bisher; ohne AWS-Einstellungen werden keine AWS-Clients angelegt
- **Zwei Arten von Secrets:**
  - Start-Secrets (DB-Passwörter, SES-SMTP-Zugangsdaten): liest ein Deploy-Skript auf dem Server aus dem Secrets Manager und übergibt sie als Umgebungsvariablen; PostgreSQL braucht die DB-Passwörter ohnehin beim Init, Spring liest Datenbank und SMTP nur beim Start. Rotation durch Neustart
  - Laufzeit-Secrets (KI-Keys): liest die App selbst über die IAM-Rolle der Instanz und hält sie 15 min im Speicher; ein rotierter Key wirkt ohne Neustart
- **Region:** `eu-central-1` (Frankfurt): EC2 Graviton und SES verfügbar, Daten in der EU
- **Instanz:** `t4g.medium` (2 vCPU, 4 GB), weil App, PostgreSQL, Gotenberg mit Chromium und später Keycloak in 2 GB knapp werden; Preis vor dem ersten Apply im AWS-Preisrechner prüfen, nach dem ersten Betrieb mit echten Werten neu bewerten. Zusätzlich ein AWS-Budget-Alarm für die Gesamtkosten (Schritt 11b)
- **SES:** Start in der Sandbox mit verifizierten Empfängeradressen; der Produktionszugang wird parallel beantragt

## Alternativen
- Spring Cloud AWS: Secrets als Property-Quelle und S3-Templates, aber zusätzliche Abhängigkeit mit unklarer Unterstützung für Spring Boot 4.1
- Alle Secrets aus der App lesen (eigener `EnvironmentPostProcessor` für Datenbank und SMTP): mehr Code, PostgreSQL bräuchte die Passwörter trotzdem von außen
- `t4g.small` (2 GB): etwa halber Preis, aber wenig Reserve für Chromium und Keycloak
- LocalStack für die Tests: realistischer, aber weitere Test-Abhängigkeit; die SDK-Clients werden stattdessen gemockt

## Konsequenzen
- Neue Abhängigkeiten: AWS SDK v2 (`secretsmanager`, `s3`, `url-connection-client`), Dependabot hält sie aktuell
- Das Deploy-Skript (Schritt 11b) ist die einzige Stelle, die Start-Secrets auf den Server holt
- Mit `prod` startet die App nicht ohne Bucket-Namen und SMTP-Zugangsdaten
