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
aws s3api create-bucket --bucket newsdigest-tfstate-172033175227 --region eu-central-1 --create-bucket-configuration LocationConstraint=eu-central-1
```

```powershell
aws s3api put-bucket-versioning --bucket newsdigest-tfstate-172033175227 --versioning-configuration Status=Enabled
```

```powershell
aws s3api put-public-access-block --bucket newsdigest-tfstate-172033175227 --public-access-block-configuration BlockPublicAcls=true,IgnorePublicAcls=true,BlockPublicPolicy=true,RestrictPublicBuckets=true
```

S3 verschlüsselt neue Objekte standardmäßig (SSE-S3); `backend.hcl` setzt zusätzlich `encrypt = true`. Locking über `use_lockfile`, kein DynamoDB nötig.

## 4. Erster Apply (lokal)
Die Alarmadresse nur in der Sitzung setzen, nie in eine Datei:

```powershell
$env:TF_VAR_alarm_email = "<meine Adresse>"
```

```powershell
terraform init "-backend-config=backend.hcl"
```

```powershell
terraform plan "-var-file=prod.tfvars" "-out=prod.tfplan"
```

Plan lesen, dann:

```powershell
terraform apply prod.tfplan
```

Danach:
- Mail "AWS Notification - Subscription Confirmation" bestätigen (SNS-Alarme)
- `terraform output` notieren: Instanz, IP, ECR, Buckets, Rollen
- Der Alarm `newsdigest-prod-backup-missing` steht bis zum ersten Backup auf ALARM; das ist erwartet
- Mit `run_window` in `prod.tfvars` läuft die Instanz nur im Zeitfenster (Runbook `zeitfenster.md`); für den Aufbau außerhalb des Fensters die Instanz mit `infra/cd/instance-power.sh start` starten

## 5. Secret-Werte eintragen
Terraform legt die Secrets leer an (`terraform output secret_names`). Passwörter nur aus Buchstaben und Ziffern, damit sie in der Env-Datei nicht maskiert werden müssen. Für die drei DB-Passwörter, je einmal mit `db/postgres`, `db/migration` und `db/app`:

```powershell
aws secretsmanager put-secret-value --secret-id newsdigest/prod/db/postgres --secret-string (aws secretsmanager get-random-password --password-length 40 --exclude-punctuation --query RandomPassword --output text)
```

Die übrigen Werte in der AWS-Konsole (Secrets Manager → Secret → Retrieve secret value → Edit), damit sie nicht in der Shell-Historie landen:

| Secret | Wert |
|---|---|
| `newsdigest/prod/ai/anthropic` | Anthropic-API-Key als Plaintext, nicht als Key/value (eigener Key für Produktion, nicht der lokale) |
| `newsdigest/prod/mail/smtp` | SES → SMTP settings → Create SMTP credentials (legt den IAM-User `ses-smtp-user.<Datum>` an; nicht Mail Manager → Ingress endpoints, das ist für eingehende Mails); im Reiter Key/value `username` und `password` aus den CSV-Spalten **ohne** "(Base64)" (Spring Mail kodiert selbst): SMTP user name `AKIA…` (20 Zeichen) und SMTP password (44 Zeichen, nicht der Secret access key) |
| `newsdigest/prod/mail/operator` | Meine Adresse für Fehler-Mails |

## 6. SES
- Domain-Identität `fuechslexd.com` wird per DKIM automatisch verifiziert, sobald die DNS-Einträge aus Terraform greifen (SES → Identities, Status "Verified")
- Sandbox: Empfängeradressen unter Identities → Create identity → Email address verifizieren (mindestens die Operator-Adresse). Die Adresse **in Kleinbuchstaben** eintragen: die App speichert Empfänger klein geschrieben, und SES prüft die Identität mit genau dieser Schreibweise (`554 Email address is not verified`)
- Produktionszugang parallel beantragen: SES → Account dashboard → Request production access (Anwendungsfall: wöchentlicher Newsletter an wenige eingetragene Empfänger, Bounces und Beschwerden werden bearbeitet)

## 7. GitHub einrichten
- Settings → Environments → `prod` anlegen, "Required reviewers": ich selbst
- Settings → Secrets and variables → Actions:
  - Secret `ALARM_EMAIL` (Alarmadresse; Secret, weil die Logs des öffentlichen Repos lesbar sind)
  - Variables `AWS_PLAN_ROLE_ARN`, `AWS_APPLY_ROLE_ARN`, `AWS_CD_ROLE_ARN` aus `terraform output github_role_arns`
- Ab dann läuft bei Pull Requests unter `infra/` ein `plan`; `apply` über Actions → Infra → Run workflow mit "apply"

## 8. Erstes Deployment (von Hand; seit Schritt 12 über `cd.yml`, siehe `deployment.md`, dieser Weg bleibt als Rückfallebene)
In **Git Bash** im Ordner `ai-news-digest` (die Skripte sind Bash, die Anführungszeichen für SSM sind dort einfacher als in Windows PowerShell). Werte aus Terraform holen:

```bash
export AWS_PROFILE=newsdigest AWS_REGION=eu-central-1
# Git Bash: no path conversion of arguments; UTF-8 output of the AWS CLI (umlauts in logs)
export MSYS_NO_PATHCONV=1 PYTHONUTF8=1 PYTHONIOENCODING=utf-8
ECR=$(terraform -chdir=infra/terraform output -raw ecr_repository_url)
INSTANCE=$(terraform -chdir=infra/terraform output -raw instance_id)
DEPLOY_BUCKET=$(terraform -chdir=infra/terraform output -json buckets | python -c "import json,sys; print(json.load(sys.stdin)['deploy'])")
TAG=$(git rev-parse --short HEAD)
```

1. Prüfen, ob der erste Start der Instanz fertig ist (Docker, Compose, Datenvolume):

```bash
infra/server/run-on-server.sh "$INSTANCE" 'cloud-init status --wait' 'docker compose version' 'df -h /var/lib/docker'
```

2. Image für arm64 bauen und nach ECR pushen. Tags sind unveränderlich; ein zweiter Push mit demselben Tag schlägt fehl, dann z. B. `TAG=$TAG-2`:

```bash
aws ecr get-login-password | docker login --username AWS --password-stdin "${ECR%%/*}"
docker buildx build --platform linux/arm64 -t "$ECR:$TAG" --push .
```

3. Deploy-Dateien hochladen und `deploy.sh` auf dem Server starten:

```bash
infra/server/upload-deploy-files.sh "$DEPLOY_BUCKET"
infra/server/run-on-server.sh "$INSTANCE" \
  "aws s3 cp s3://$DEPLOY_BUCKET/current/infra/server/deploy.sh /tmp/deploy.sh --only-show-errors" \
  "bash /tmp/deploy.sh $TAG"
```

4. Prüfen: https://fuechslexd.com zeigt die Platzhalterseite mit gültigem Zertifikat, https://fuechslexd.com/health liefert `{"status":"UP"}`. Logs in CloudWatch unter `/newsdigest/prod`, oder direkt:

```bash
infra/server/run-on-server.sh "$INSTANCE" 'docker ps --format "{{.Names}} {{.Status}}"'
```

5. Backup einmal von Hand starten; danach liegt ein Dump im Backup-Bucket und der Alarm `newsdigest-prod-backup-missing` geht innerhalb eines Tages auf OK:

```bash
infra/server/run-on-server.sh "$INSTANCE" 'systemctl start newsdigest-backup.service' 'journalctl -u newsdigest-backup -n 5 --no-pager'
```

Hinweise:
- Die Env-Datei mit den Start-Secrets liegt nur in `/run` und ist nach einem Neustart der Instanz weg. Die Container laufen trotzdem weiter (sie behalten ihre Umgebung); für Compose-Befehle danach `deploy.sh` mit dem Tag aus `/etc/newsdigest/current-tag` erneut ausführen
- Erster echter Lauf: `erster-produktionslauf.md`; Wiederherstellung üben: `wiederherstellung.md`
