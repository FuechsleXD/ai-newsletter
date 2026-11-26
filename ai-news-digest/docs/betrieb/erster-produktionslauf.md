# Runbook: Erster echter Lauf in Produktion

Bis es Scheduler (Schritt 16) und API (Schritt 19) gibt, wird ein Lauf in Produktion über das Eval-Profil gestartet: ein zusätzlicher, kurzlebiger App-Container startet den Lauf durch den normalen Prozess, wartet auf das Ende und beendet sich. Die laufende App bleibt an. Kosten wie lokal (Claude Sonnet 5.5, 10 Websuchen: etwa 0,41 USD).

Voraussetzungen: Deployment nach `aws-bootstrap.md` §8, SES-Domain verifiziert, Empfängeradresse in SES verifiziert (Sandbox), Anthropic-Key im Secret `newsdigest/prod/ai/anthropic`. Befehle in **Git Bash** im Ordner `ai-news-digest`, Variablen wie in `aws-bootstrap.md` §8.

## 1. Modell und Topic anlegen
Die SQL-Dateien aus dem lokalen Testlauf (`lokaler-testlauf.md`) verwenden, außerhalb des Repos. In der Topic-Datei statt `empfaenger@example.test` die eigene, in SES verifizierte Adresse eintragen (sie steht dann nur in der Produktionsdatenbank, nie im Repo).

Über den Deploy-Bucket auf den Server bringen und einspielen:

```bash
aws s3 cp ../../newsdigest-local/my-models.sql "s3://$DEPLOY_BUCKET/tmp/my-models.sql"
aws s3 cp ../../newsdigest-local/my-topic-prod.sql "s3://$DEPLOY_BUCKET/tmp/my-topic.sql"
PG='docker exec -i $(docker ps -q --filter label=com.docker.compose.project=ai-news-digest-prod --filter label=com.docker.compose.service=postgres) psql -U postgres -d newsdigest -v ON_ERROR_STOP=1'
infra/server/run-on-server.sh "$INSTANCE" \
  "aws s3 cp s3://$DEPLOY_BUCKET/tmp/my-models.sql - | $PG" \
  "aws s3 cp s3://$DEPLOY_BUCKET/tmp/my-topic.sql - | $PG"
aws s3 rm "s3://$DEPLOY_BUCKET/tmp/" --recursive
```

Erwartet: `INSERT 0 1` für das Modell, beim Topic dreimal `INSERT 0 1`. Der Deploy-Bucket ist versioniert und privat; die gelöschten Dateien verschwinden nach 30 Tagen endgültig (Lifecycle-Regel).

## 2. Lauf starten
Ohne das AWS-Override, damit die Ausgabe in der SSM-Antwort landet statt in CloudWatch:

```bash
infra/server/run-on-server.sh "$INSTANCE" \
  'cd /opt/newsdigest && docker compose -f docker-compose.prod.yml --env-file /run/newsdigest/prod.env run --rm -e SPRING_PROFILES_ACTIVE=prod,eval -e NEWSDIGEST_EVAL_TOPICS=KI-News app 2>&1 | grep -E "Started process instance|Eval run|ERROR" '
```

Erwartet: `Eval run 1 of topic 'KI-News' ended with COMPLETED` (nach etwa einer Minute). Der Export des Eval-Profils bleibt im kurzlebigen Container und wird nicht gebraucht; alle Daten stehen in der Datenbank.

## 3. Prüfen
- Mail mit PDF-Anhang im eigenen Postfach (Absender `digest@fuechslexd.com`); im Mail-Header `dkim=pass` und `spf=pass`
- PDF im PDF-Bucket unter `runs/<runId>/digest.pdf`
- Status und Kosten:

```bash
infra/server/run-on-server.sh "$INSTANCE" \
  "$PG -c \"SELECT r.id, r.status, r.error_message, p.cost_usd, a.cost_usd AS aggregation FROM app.digest_run r LEFT JOIN app.provider_result p ON p.digest_run_id = r.id LEFT JOIN app.aggregation_result a ON a.digest_run_id = r.id ORDER BY r.id DESC LIMIT 3\""
```

## Wenn etwas schiefgeht
- `FAILED` mit Fehlertext beim Secret: Wert im Secrets Manager prüfen (nach dem Eintragen wirkt er spätestens nach 15 min, ein neuer Eval-Container liest ihn sofort)
- Mail nicht angekommen: `app.mail_delivery` prüfen; in der SES-Sandbox muss der Empfänger verifiziert sein; SES → Account dashboard zeigt Bounces
- Hängender Lauf: wie lokal (`lokaler-testlauf.md`, Abschnitt Hinweise)
