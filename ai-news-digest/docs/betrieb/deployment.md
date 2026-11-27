# Runbook: Deployment, Redeploy und Rollback

Ausgerollt wird mit `.github/workflows/cd.yml` (docs/11-deployment-cicd.md). Der Weg von Hand aus `aws-bootstrap.md` §8 bleibt als Rückfallebene.

## Einmalig einrichten
Settings → Secrets and variables → Actions → **Variables** (keine Secrets; Werte aus `terraform -chdir=infra/terraform output`):

| Variable | Wert |
|---|---|
| `AWS_CD_ROLE_ARN` | `github_role_arns.cd` (seit Schritt 11c vorhanden) |
| `AWS_INSTANCE_ID` | `instance_id` |
| `DEPLOY_BUCKET` | `buckets.deploy` |
| `ECR_REPOSITORY_URL` | `ecr_repository_url` |
| `DOMAIN` | `fuechslexd.com` |

Die Umgebung `prod` mit "Required reviewers" gibt es seit Schritt 11c. Nur Jobs in dieser Umgebung dürfen die Rolle `newsdigest-prod-cd` annehmen.

## Normaler Ablauf
1. Pull Request nach `main` mergen; die CI läuft auf `main`
2. Nach grüner CI startet `CD` und wartet auf die Freigabe: Actions → CD → Review deployments → `prod` → Approve
3. Der Job
   - liest das gerade laufende Tag vom Server (Ziel für einen Rollback); ist die Instanz gestoppt (außerhalb des Zeitfensters, `zeitfenster.md`), startet er sie vorher und stoppt sie am Ende wieder
   - baut das Image nativ auf einem arm64-Runner und pusht es als `<kurzer Commit-SHA>` nach ECR (gibt es das Tag schon, wird nicht neu gebaut)
   - lädt die Deploy-Dateien nach `current/` und `releases/<tag>/`
   - startet `deploy.sh <tag>` per SSM; `docker compose up --wait` gelingt erst, wenn der Health-Check der App (Actuator) grün ist
   - prüft über HTTPS, dass `/health` `UP` meldet und die Startseite antwortet (bis zu 2 Minuten)
4. Ergebnis in der Job-Zusammenfassung: welches Tag läuft und welches vorher lief

## Rollback
- **Automatisch:** Schlägt das Deployment oder der Smoke-Test fehl, rollt der Job das vorher laufende Tag wieder aus (mit dessen Deploy-Dateien aus `releases/<tag>/`, für ältere Releases aus `current/`) und prüft erneut. Der Lauf bleibt rot; die Zusammenfassung sagt, ob der Rollback geklappt hat
- **Von Hand:** Actions → CD → Run workflow, im Feld "Image-Tag" das gewünschte Tag aus ECR (`aws ecr describe-images --repository-name newsdigest --query "imageDetails[].imageTags"`), Freigabe wie oben. Es wird nichts gebaut und `current/` nicht verändert
- **Datenbank:** Flyway-Migrationen sind abwärtskompatibel (docs/11); ein älteres Image startet auch mit neueren Migrationen, weil Flyway später angewendete Versionen standardmäßig ignoriert. Daten zurückspielen nur über `wiederherstellung.md`

## Wenn es rot wird
| Meldung | Ursache und Abhilfe |
|---|---|
| `Repository variable for ... is missing` | Variable aus der Tabelle oben anlegen |
| `not reachable via SSM. Is it stopped?` | Instanz starten (`infra/cd/instance-power.sh start <id>`), Lauf neu starten |
| `SSM does not reach it after 5 minutes` | Instanz läuft, der SSM-Agent ist noch nicht verbunden; Lauf neu starten |
| `Could not assume role` | Lauf nicht in der Umgebung `prod`, oder der OIDC-Subject passt nicht zur Trust Policy: CloudTrail → Event `AssumeRoleWithWebIdentity` zeigt den gesendeten Subject (GitHub schreibt die IDs von Besitzer und Repo hinein, `repo:<owner>@<id>/<name>@<id>:...`); `github_repository` und `github_repository_ids` in `prod.tfvars` prüfen und lokal applyen (die Apply-Rolle in GitHub ist dann ebenfalls betroffen) |
| `... is not in ECR` | Beim Rollback von Hand ein Tag gewählt, das es nicht gibt; Tag prüfen |
| `deploy.sh` schlägt fehl, Rollback grün | Logs des fehlgeschlagenen Containers in CloudWatch (`/newsdigest/prod`); häufig ein Secret im falschen Format (`aws-bootstrap.md` §5) |
| Rollback auch rot | Von Hand nach `aws-bootstrap.md` §8 prüfen und ausrollen |

## Hinweise
- Läuft gerade ein Digest-Lauf, wartet `docker compose` bis zu 60 s (`stop_grace_period`) auf das Ende der laufenden Jobs; ein unterbrochener Job wird von CIB seven nach dem Start wiederholt
- Es läuft nie mehr als ein Deployment gleichzeitig (Concurrency-Gruppe `cd-prod`)
- Das Image wird für CD nicht erneut mit Trivy geprüft; das macht die CI für denselben Commit
