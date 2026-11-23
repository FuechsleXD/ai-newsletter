# Runbook: Datenbank wiederherstellen

> Status: Gerüst. Wird beim ersten Deployment (Roadmap Schritt 11) mit den echten Befehlen gefüllt und einmal ausprobiert.

## Wann
- Datenverlust oder defekte Datenbank auf der EC2-Instanz
- Umzug auf eine neue Instanz

## Variante A: aus `pg_dump` in S3
1. App-Container stoppen (Scheduler darf nicht laufen)
2. Gewünschten Dump aus S3 laden
3. Leere Datenbank mit den Benutzern `newsdigest_migration` und `newsdigest_app` anlegen
4. Dump einspielen (beide Schemas `app` und `bpm`)
5. App starten; Flyway prüft den Stand und spielt fehlende Migrationen ein
6. Prüfen: Health, letzte Läufe im UI, offene Incidents in der Engine-UI

## Variante B: aus EBS-Snapshot
1. App- und DB-Container stoppen
2. Neues Volume aus dem Snapshot erzeugen und statt des alten einhängen
3. Container starten und wie oben prüfen

## Nach der Wiederherstellung
- Läufe, die zum Zeitpunkt des Ausfalls aktiv waren, im UI prüfen und bei Bedarf manuell neu starten
- Ergebnis und Dauer hier notieren

## Protokoll
| Datum | Anlass | Variante | Dauer | Ergebnis |
|---|---|---|---|---|
