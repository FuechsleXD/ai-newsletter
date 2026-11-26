# Runbook: Datenbank wiederherstellen

Gesichert wird zweifach (docs/11-deployment-cicd.md): täglich ein `pg_dump` der ganzen Datenbank (Schemas `app` und `bpm`) im Backup-Bucket (30 Tage) und tägliche EBS-Snapshots des Datenvolumes (7 Stück). Befehle in **Git Bash** im Ordner `ai-news-digest`, Variablen wie in `aws-bootstrap.md` §8, zusätzlich:

```bash
BACKUP_BUCKET=$(terraform -chdir=infra/terraform output -json buckets | python -c "import json,sys; print(json.load(sys.stdin)['backups'])")
PSQL='docker exec -i $(docker ps -q --filter label=com.docker.compose.project=ai-news-digest-prod --filter label=com.docker.compose.service=postgres)'
```

## Wann
- Datenverlust oder defekte Datenbank auf der Instanz
- Umzug auf eine neue Instanz

## Üben ohne Ausfall
Den neuesten Dump in eine Wegwerf-Datenbank im selben Container einspielen und mit der echten vergleichen. Die App läuft dabei weiter.

Der Server darf Backups nur schreiben, nicht lesen oder auflisten (Rolle `newsdigest-prod-server`: nur `s3:PutObject` auf den Backup-Bucket; ein kompromittierter Server kann die Backups so weder lesen noch gezielt ersetzen). Den Dump wählt deshalb der Betreiber lokal aus und gibt dem Server eine vorsignierte URL, die nach 15 Minuten abläuft:

```bash
KEY=$(aws s3 ls "s3://$BACKUP_BUCKET/postgres/" | sort | tail -1 | awk '{print $4}'); echo "Dump: $KEY"
URL=$(aws s3 presign "s3://$BACKUP_BUCKET/postgres/$KEY" --expires-in 900)
infra/server/run-on-server.sh "$INSTANCE" \
  "$PSQL psql -U postgres -c 'CREATE DATABASE newsdigest_restore_test'" \
  "time (curl -fsS '$URL' | $PSQL pg_restore -U postgres -d newsdigest_restore_test --no-owner --exit-on-error)" \
  "for db in newsdigest newsdigest_restore_test; do $PSQL psql -U postgres -d \$db -tAc \"SELECT '\$db', (SELECT count(*) FROM app.digest_run), (SELECT count(*) FROM app.news_item), (SELECT count(*) FROM app.mail_delivery), (SELECT count(*) FROM bpm.act_re_procdef), (SELECT max(version) FROM app.flyway_schema_history)\"; done" \
  "$PSQL psql -U postgres -c 'DROP DATABASE newsdigest_restore_test'"
```

Erwartet: beide Zeilen mit denselben Zahlen (Läufe, die nach dem Dump entstanden sind, fehlen in der Kopie). Dauer und Ergebnis unten ins Protokoll.

## Variante A: aus `pg_dump` in S3
1. App stoppen, damit kein Lauf schreibt:

```bash
infra/server/run-on-server.sh "$INSTANCE" 'cd /opt/newsdigest && docker compose -f docker-compose.prod.yml -f docker-compose.aws.yml --env-file /run/newsdigest/prod.env stop app'
```

2. Datenbank leeren und neu anlegen; die Benutzer `newsdigest_migration` und `newsdigest_app` bleiben (sie gehören zum Cluster, nicht zur Datenbank):

```bash
infra/server/run-on-server.sh "$INSTANCE" \
  "$PSQL psql -U postgres -c 'DROP DATABASE newsdigest WITH (FORCE)'" \
  "$PSQL psql -U postgres -c 'CREATE DATABASE newsdigest'" \
  "$PSQL psql -U postgres -c 'GRANT CONNECT, CREATE ON DATABASE newsdigest TO newsdigest_migration; GRANT CONNECT ON DATABASE newsdigest TO newsdigest_app; REVOKE ALL ON DATABASE newsdigest FROM PUBLIC'"
```

3. Gewünschten Dump einspielen (`aws s3 ls s3://$BACKUP_BUCKET/postgres/` zeigt alle, lokal ausführen); der Server bekommt ihn über eine vorsignierte URL (siehe oben). Eigentümer und Rechte kommen aus dem Dump:

```bash
KEY=newsdigest-<zeitpunkt>.dump
URL=$(aws s3 presign "s3://$BACKUP_BUCKET/postgres/$KEY" --expires-in 900)
infra/server/run-on-server.sh "$INSTANCE" "curl -fsS '$URL' | $PSQL pg_restore -U postgres -d newsdigest --exit-on-error"
```

4. App starten; Flyway prüft den Stand und spielt fehlende Migrationen ein:

```bash
infra/server/run-on-server.sh "$INSTANCE" 'cd /opt/newsdigest && docker compose -f docker-compose.prod.yml -f docker-compose.aws.yml --env-file /run/newsdigest/prod.env up -d --wait app'
```

5. Prüfen: https://fuechslexd.com/health, letzte Läufe (Abfrage aus `erster-produktionslauf.md` §3), offene Incidents in `bpm.act_ru_incident`

## Variante B: aus EBS-Snapshot
Wenn das ganze Datenvolume verloren oder defekt ist (es enthält alle Docker-Daten: Datenbank, Zertifikate, Images).
1. Snapshot wählen: EC2 → Snapshots, Tag `Backup = newsdigest-prod-daily`
2. Instanz stoppen (EC2-Konsole; Beenden ist gesperrt)
3. Neues Volume aus dem Snapshot in derselben Availability Zone erzeugen (verschlüsselt, gp3)
4. Altes Volume abhängen, neues als `/dev/sdf` anhängen
5. In Terraform das neue Volume übernehmen: `terraform state rm aws_ebs_volume.data aws_volume_attachment.data`, dann `terraform import aws_ebs_volume.data <neue-volume-id>` und `terraform import aws_volume_attachment.data /dev/sdf:<neue-volume-id>:<instanz-id>`; `plan` darf danach nur noch Tags ändern
6. Instanz starten; `/etc/fstab` hängt das Volume über die UUID ein (gleiche UUID, weil das Dateisystem aus dem Snapshot stammt), Docker startet die Container von selbst
7. `deploy.sh` mit dem Tag aus `/etc/newsdigest/current-tag` ausführen (die Env-Datei in `/run` ist nach dem Neustart weg), dann wie oben prüfen

## Nach der Wiederherstellung
- Läufe, die zum Zeitpunkt des Ausfalls aktiv waren, prüfen und bei Bedarf neu starten
- Ergebnis und Dauer hier notieren

## Protokoll
| Datum | Anlass | Variante | Dauer | Ergebnis |
|---|---|---|---|---|
| 2026-11-26 | Übung nach dem ersten Deployment | Wegwerf-Datenbank aus `pg_dump` (282 KB) | 1,6 s | gleiche Zahlen in beiden Datenbanken (1 Lauf, 3 Meldungen, 1 Zustellung, 1 Prozessdefinition, Flyway V6); Runbook korrigiert: der Server kann Backups nicht lesen, deshalb vorsignierte URL |
