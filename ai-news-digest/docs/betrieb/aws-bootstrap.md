# Runbook: AWS einrichten (Bootstrap und erster Apply)

Einmalige Schritte vor dem ersten Deployment (Roadmap 11c). Alles, was Geld kostet oder Zugangsdaten braucht, mache ich selbst; Terraform legt den Rest an. Befehle in PowerShell im Ordner `ai-news-digest/infra/terraform`, AWS CLI mit einem Administrator-Profil angemeldet (z. B. `aws configure sso`).

## 1. Vorbereitung
- AWS-Konto mit MFA für den Root-Benutzer; für die Arbeit einen eigenen Admin-Zugang (IAM Identity Center), nicht den Root-Benutzer
- Kosten im AWS-Preisrechner prüfen (`t4g.medium`, 2 EBS-Volumes, Elastic IP, Secrets, S3, SES) und mit dem Budget (40 USD, `variables.tf`) vergleichen
- Region überall `eu-central-1`

## 2. Domain registrieren
- Route 53 → Registered domains → `fuechslexd.com` registrieren (Zahlung, Kontaktdaten mit Datenschutz)
- Route 53 legt dabei die Hosted Zone automatisch an; Terraform liest sie nur (`dns-mail.tf`). Ohne sie schlägt `plan` fehl

## 3. State-Bucket anlegen (einmalig von Hand)

```powershell
aws s3api create-bucket --bucket fuechslexd-newsdigest-tfstate --region eu-central-1 --create-bucket-configuration LocationConstraint=eu-central-1
```

```powershell
aws s3api put-bucket-versioning --bucket fuechslexd-newsdigest-tfstate --versioning-configuration Status=Enabled
```

```powershell
aws s3api put-public-access-block --bucket fuechslexd-newsdigest-tfstate --public-access-block-configuration BlockPublicAcls=true,IgnorePublicAcls=true,BlockPublicPolicy=true,RestrictPublicBuckets=true
```

S3 verschlüsselt neue Objekte standardmäßig (SSE-S3); `backend.hcl` setzt zusätzlich `encrypt = true`. Locking über `use_lockfile`, kein DynamoDB nötig.

## 4. Erster Apply (lokal)
Die Alarmadresse nur in der Sitzung setzen, nie in eine Datei:

```powershell
$env:TF_VAR_alarm_email = "<meine Adresse>"
```

```powershell
terraform init -backend-config=backend.hcl
```

```powershell
terraform plan -var-file=prod.tfvars -out=prod.tfplan
```

Plan lesen, dann:

```powershell
terraform apply prod.tfplan
```

Danach:
- Mail "AWS Notification - Subscription Confirmation" bestätigen (SNS-Alarme)
- `terraform output` notieren: Instanz, IP, ECR, Buckets, Rollen
- Der Alarm `newsdigest-prod-backup-missing` steht bis zum ersten Backup auf ALARM; das ist erwartet

## 5. Secret-Werte eintragen
Terraform legt die Secrets leer an (`terraform output secret_names`). Passwörter nur aus Buchstaben und Ziffern, damit sie in der Env-Datei nicht maskiert werden müssen. Für die drei DB-Passwörter, je einmal mit `db/postgres`, `db/migration` und `db/app`:

```powershell
aws secretsmanager put-secret-value --secret-id newsdigest/prod/db/postgres --secret-string (aws secretsmanager get-random-password --password-length 40 --exclude-punctuation --query RandomPassword --output text)
```

Die übrigen Werte in der AWS-Konsole (Secrets Manager → Secret → Retrieve secret value → Edit), damit sie nicht in der Shell-Historie landen:

| Secret | Wert |
|---|---|
| `newsdigest/prod/ai/anthropic` | Anthropic-API-Key (eigener Key für Produktion, nicht der lokale) |
| `newsdigest/prod/mail/smtp` | `{"username": "...", "password": "..."}` aus SES → SMTP settings → Create SMTP credentials |
| `newsdigest/prod/mail/operator` | Meine Adresse für Fehler-Mails |

## 6. SES
- Domain-Identität `fuechslexd.com` wird per DKIM automatisch verifiziert, sobald die DNS-Einträge aus Terraform greifen (SES → Identities, Status "Verified")
- Sandbox: Empfängeradressen unter Identities → Create identity → Email address verifizieren (mindestens die Operator-Adresse)
- Produktionszugang parallel beantragen: SES → Account dashboard → Request production access (Anwendungsfall: wöchentlicher Newsletter an wenige eingetragene Empfänger, Bounces und Beschwerden werden bearbeitet)

## 7. GitHub einrichten
- Settings → Environments → `prod` anlegen, "Required reviewers": ich selbst
- Settings → Secrets and variables → Actions:
  - Secret `ALARM_EMAIL` (Alarmadresse; Secret, weil die Logs des öffentlichen Repos lesbar sind)
  - Variables `AWS_PLAN_ROLE_ARN`, `AWS_APPLY_ROLE_ARN`, `AWS_CD_ROLE_ARN` aus `terraform output github_role_arns`
- Ab dann läuft bei Pull Requests unter `infra/` ein `plan`; `apply` über Actions → Infra → Run workflow mit "apply"

## 8. Erstes Deployment
Folgt in Roadmap 11c (Image für arm64 nach ECR, Deploy-Dateien hochladen, `deploy.sh` per SSM, HTTPS und erste Mail prüfen, Wiederherstellung üben) und wird hier ergänzt.
