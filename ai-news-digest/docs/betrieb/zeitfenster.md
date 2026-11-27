# Runbook: Instanz nur im Zeitfenster

Seit Roadmap-Schritt 16b läuft die EC2-Instanz nur in einem wöchentlichen Zeitfenster (ADR-019): EventBridge Scheduler startet sie vor dem Zeitplan der Topics und stoppt sie danach. Das spart etwa zwei Drittel der AWS-Kosten (etwa 12 statt 40 USD im Monat; es bleiben vor allem EBS-Volumes, Elastic IP, Secrets und Route 53).

Befehle in **Git Bash** im Ordner `ai-news-digest`, mit `AWS_PROFILE=newsdigest` und `INSTANCE` wie in `aws-bootstrap.md` §8.

## Wie es abläuft

| Zeit (Europe/Berlin) | Was passiert |
|---|---|
| Mo 06:30 | EventBridge startet die Instanz; Docker startet die Container von selbst (`restart: unless-stopped`, Einstellungen des letzten Deployments) |
| etwa 06:33 | App bereit; der Scheduler lädt die Zeitpläne. Liegt ein geplanter Start höchstens 6 h zurück und lief seitdem nichts, wird er nachgeholt |
| etwa 06:40 | Backup nach dem Start (`newsdigest-backup.timer`, 10 min nach dem Booten); Wartungsjob (Aufbewahrung, überfällige Läufe, Engine-Historie) |
| Mo 07:00 | Geplanter Lauf (Zeitplan aus der Tabelle `schedule`) |
| Mo 08:00 | EventBridge stoppt die Instanz; vorher läuft ein Backup (`newsdigest-backup-on-stop.service`) |
| Mo 10:00/11:00 | Wöchentlicher EBS-Snapshot des gestoppten Datenvolumes (09:00 UTC) |

- Ein Lauf, den der Stopp abschneidet, setzt beim nächsten Start fort (die Engine vergibt seine Jobs neu) oder wird vom Wartungsjob nach 3 h abgebrochen (`CANCELLED`, Mail an den Betreiber)
- Alarm `newsdigest-prod-backup-missing` meldet erst nach 7 Tagen ohne Backup; die Status-Check-Alarme haben bei gestoppter Instanz keine Daten und melden nichts
- Let's-Encrypt-Zertifikate erneuert Caddy beim Start, wenn sie in weniger als 30 Tagen ablaufen; eine Stunde pro Woche reicht dafür

## Zeitfenster ändern

In `infra/terraform/prod.tfvars`, Block `run_window`: `start` und `stop` sind EventBridge-Cron-Ausdrücke (Minute Stunde Tag Monat Wochentag Jahr) in `timezone`, `snapshot` ist der DLM-Cron-Ausdruck in UTC.

- Das Fenster muss vor dem frühesten Topic-Zeitplan beginnen oder höchstens 6 h danach, sonst fällt der Lauf aus
- Bis zum Stopp genug Puffer lassen: ein Lauf dauert einige Minuten, mit Wiederholungen nach Fehlern (3 × 5 min) länger
- Ändert sich der Zeitplan eines Topics (Tabelle `schedule`), das Fenster mit anpassen
- Danach `terraform plan` und `terraform apply` wie in `aws-bootstrap.md`

Dauerbetrieb: `run_window` aus `prod.tfvars` entfernen und anwenden. Dann gibt es keine Start-/Stopp-Zeitpläne mehr, der Snapshot wird wieder täglich und der Backup-Alarm meldet nach einem Tag.

## Instanz von Hand starten und stoppen

```bash
infra/cd/instance-power.sh start "$INSTANCE"   # wartet, bis SSM die Instanz erreicht
infra/cd/instance-power.sh stop "$INSTANCE"    # wartet bis zu 30 min auf laufende Läufe, dann Stopp
```

Ein Start außerhalb des Fensters holt keinen Lauf nach, wenn der geplante Zeitpunkt mehr als 6 h zurückliegt. Bleibt die Instanz versehentlich an, stoppt sie spätestens das nächste Fenster.

## Deployments

`cd.yml` startet eine gestoppte Instanz vor dem Deployment und stoppt sie danach wieder, auch wenn das Deployment fehlschlägt (Schritt „Stop the instance again“; er wartet bis zu 15 min auf laufende Läufe). Läuft die Instanz schon (im Fenster), bleibt sie an. Die CD-Rolle darf dafür nur diese Instanz starten und stoppen.

## Fehlerbilder

| Meldung / Beobachtung | Ursache und Abhilfe |
|---|---|
| Kein Digest am Montag, Instanz war aus | Schedule `newsdigest-prod-start` in EventBridge Scheduler prüfen (Konsole: Amazon EventBridge → Scheduler → Schedules); Instanz von Hand starten: der Lauf wird nachgeholt, wenn der Zeitplan höchstens 6 h zurückliegt, sonst manuell starten |
| Instanz läuft nach dem Fenster weiter | Schedule `newsdigest-prod-stop` prüfen; Instanz von Hand stoppen; das AWS-Budget meldet steigende Kosten |
| `backup-missing` im Alarm | eine Woche ohne Backup: Journal prüfen (`run-on-server.sh "$INSTANCE" 'journalctl -u newsdigest-backup -u newsdigest-backup-on-stop -n 30 --no-pager'`) |
| CD-Schritt „Start the instance“ scheitert mit `SSM does not reach it` | Instanz läuft, der SSM-Agent ist noch nicht verbunden; Workflow neu starten |
