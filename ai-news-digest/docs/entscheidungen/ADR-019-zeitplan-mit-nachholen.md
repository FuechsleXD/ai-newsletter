# ADR-019: Zeitplan mit Nachholen statt fester Nachtzeiten

- **Status:** Angenommen
- **Datum:** 2026-11-27

## Kontext
Um Kosten zu sparen, soll die EC2-Instanz nur zum Lauf laufen (Roadmap-Schritt 16b, festes Zeitfenster per EventBridge). Ein Start zur exakten Cron-Zeit geht verloren, wenn die App ein paar Minuten zu spät bereit ist; Aufräumarbeiten zu einer festen Nachtzeit liefen auf einer gestoppten Instanz nie. Ein hängender Lauf (Incident) fiele niemandem auf und blockierte alle weiteren Läufe des Topics.

## Entscheidung
- `DigestScheduler` startet Läufe nach den Zeitplänen aus der DB und lädt sie alle 60 s neu
- Beim Start der App wird ein verpasster geplanter Start nachgeholt, wenn er höchstens 6 h zurückliegt (`newsdigest.scheduler.misfire-grace`) und seitdem kein Lauf des Topics gestartet ist; höchstens einmal je Zeitpunkt
- Der Wartungsjob läuft 10 min nach dem Start der App und dann alle 24 h, nicht zu einer festen Uhrzeit: Abbruch von Läufen über 3 h (`CANCELLED`, Betreiber-Mail), Aufbewahrungsfristen, History-Cleanup der Engine. Alle Aufgaben sind idempotent
- Der Scheduler ist nur im Profil `prod` an (`newsdigest.scheduler.enabled`), damit lokal und in Tests nichts von selbst startet

## Alternativen
- Spring `@Scheduled` mit festen Cron-Zeiten: einfach, aber Zeitpläne nicht aus der DB und kein Nachholen
- Timer-Startereignis im BPMN: Zeitpläne stünden im Modell (Goldene Regel 1)
- Quartz mit Misfire-Handling: neue Dependency und eigene Tabellen für eine Instanz mit wenigen Topics
- Nur melden statt automatisch abbrechen: der Lauf blockierte das Topic, bis jemand eingreift

## Konsequenzen
- Das Startfenster der Instanz muss vor dem frühesten Zeitplan beginnen oder höchstens 6 h danach; sonst fällt der Lauf aus (16b)
- Der Zeitraum eines nachgeholten Laufs ergibt sich wie immer aus `last_successful_run_at`, es fehlen keine Meldungen
- Ein Lauf, der wirklich länger als 3 h braucht, wird abgebrochen; der Wert ist einstellbar (`newsdigest.run.max-duration`)
- Angepasst: `02-architektur.md`, `03-datenmodell.md`, `04-prozess-bpmn.md`, `05-api.md`, `10-tests.md`, `11-deployment-cicd.md`, `12-defaults.md`

## Nachtrag (Roadmap-Schritt 16b): festes Zeitfenster der Instanz
- EventBridge Scheduler startet und stoppt die Instanz über das EC2-API (Universal Target, keine Lambda), montags 06:30-08:00 Europe/Berlin, einstellbar mit `run_window` in `prod.tfvars`
- Verworfen: die App stoppt die Instanz selbst, wenn nichts mehr läuft (spart mehr, aber die Instanzrolle bräuchte das Recht, sich zu stoppen, und ein Fehler in der App ließe die Instanz laufen); als spätere Ergänzung möglich
- Backups 10 min nach dem Start und vor dem Herunterfahren, Backup-Alarm nach 7 Tagen, EBS-Snapshot wöchentlich nach dem Stopp
- CD startet eine gestoppte Instanz für das Deployment und stoppt sie danach wieder; vor dem Stopp wartet es auf laufende Läufe
- Runbook: `docs/betrieb/zeitfenster.md`
