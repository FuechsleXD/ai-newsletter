# CIB seven 3.0.0 – offizielle Create-Skripte (PostgreSQL)

Unverändert übernommen aus `org.cibseven.bpm:cibseven-engine:3.0.0`, Pfad im Jar
`org/cibseven/bpm/engine/db/create/activiti.postgres.create.*.sql`.

- Nie von Hand ändern (siehe `docs/03-datenmodell.md`, ADR-006)
- Ausgeführt von der Java-Migration `V3__bpm_create_engine_3_0` im Schema `bpm`; deren Prüfsumme wird aus dem Inhalt dieser Dateien berechnet
- Versionswechsel: Upgrade-Skripte aus `org/cibseven/bpm/engine/db/upgrade/` in einen neuen Ordner `cibseven-<version>-upgrade/` kopieren und mit einer neuen Migration ausführen
