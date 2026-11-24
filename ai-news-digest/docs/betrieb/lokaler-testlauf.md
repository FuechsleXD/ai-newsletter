# Runbook: Lokaler Testlauf und Prompt-Eval

Erster echter Lauf auf dem eigenen Rechner (Roadmap Schritt 9). Echte KI-Aufrufe kosten Geld; ein Lauf mit einem Recherche- und einem Aggregator-Modell liegt je nach Modell bei wenigen Cent bis etwa 0,50 USD. Das Monatsbudget (Default 10 USD) wird vor jedem Lauf geprüft.

## Einmalig vorbereiten
1. Lokale Dienste starten: `docker compose up -d` (PostgreSQL, Mailpit, Gotenberg)
2. `.env.example` nach `.env` kopieren, Passwörter setzen und den eigenen Anthropic-API-Key bei `NEWSDIGEST_PROD_AI_ANTHROPIC` eintragen. `.env` wird nie eingecheckt
3. App einmal starten (siehe unten), damit Flyway das Schema anlegt; dann wieder stoppen
4. Modelle anlegen: `src/main/resources/db/dev-data/models.example.sql` kopieren (z. B. nach `my-models.sql` außerhalb des Repos), Modell-IDs und aktuelle Preise von der Modell- und Preisseite des Anbieters eintragen und ausführen. Welches Modell recherchiert und welches aggregiert, legst du hier fest (ADR-013). Recherche-Modelle brauchen `supports_web_search = true` und einen Preis pro Websuche
5. Topic anlegen: `src/main/resources/db/dev-data/topic.example.sql` genauso kopieren, ausfüllen und ausführen. Der Empfänger `empfaenger@example.test` landet in Mailpit

```powershell
Get-Content my-models.sql | docker compose exec -T postgres psql -U postgres -d newsdigest
```

## App starten
Spring Boot liest `.env` nicht selbst. In PowerShell die Variablen für die aktuelle Sitzung setzen und dann starten:

```powershell
Get-Content .env | Where-Object { $_ -match '^[A-Z_]+=' } | ForEach-Object { $name, $value = $_ -split '=', 2; Set-Item "env:$name" $value }
```

```powershell
./mvnw spring-boot:run "-Dspring-boot.run.profiles=dev"
```

## Lauf starten (Profil `dev`)
Topic-ID nachsehen und den Lauf über den Dev-Endpunkt starten (nur im Profil `dev` vorhanden):

```powershell
docker compose exec postgres psql -U postgres -d newsdigest -c "SELECT id, name FROM app.topic"
```

```powershell
Invoke-RestMethod -Method Post http://localhost:8080/dev/topics/1/runs
```

Antwort: `runId` und `processInstanceId` (HTTP 202). Ein zweiter Start desselben Topics während des Laufs wird mit 409 abgelehnt, ein unbekanntes Topic mit 404.

## Ergebnis ansehen
- Mail mit PDF-Anhang: Mailpit unter http://localhost:8025
- PDF: `data/pdfs/runs/<runId>/digest.pdf`
- Status, Fehler und Kosten:

```powershell
docker compose exec postgres psql -U postgres -d newsdigest -c "SELECT r.id, r.status, r.error_message, p.status AS recherche, p.cost_usd, a.cost_usd AS aggregation FROM app.digest_run r LEFT JOIN app.provider_result p ON p.digest_run_id = r.id LEFT JOIN app.aggregation_result a ON a.digest_run_id = r.id ORDER BY r.id DESC LIMIT 5"
```

## Prompt-Eval (Profil `eval`)
Für den Vergleich von Prompt-Versionen. Läuft durch den normalen Prozess (Kosten und Budget wie bei jedem Lauf), wartet auf das Ende und schreibt alles nach `target/eval/<prompt-version>/<zeit>-<topic>/`:

| Datei | Inhalt |
|---|---|
| `summary.md` | Status, Zeitraum, Kosten und Meldungen je Modell |
| `run.json` | Alles strukturiert: Token, Websuchen, Kosten, Fehler, Meldungen mit Prüfstatus, Zustellstatus (ohne Mailadressen) |
| `research-<modell>.txt` | Rohantwort jedes Recherche-Modells |
| `aggregate.json`, `mail.html`, `digest.pdf` | Ergebnis des Aggregators und daraus erzeugte Mail und PDF |

1. Topic-Namen eintragen, am besten in einer nicht eingecheckten `application-local.yml` oder als Umgebungsvariable:

```powershell
$env:NEWSDIGEST_EVAL_TOPICS = "Java"
```

2. Starten; die App beendet sich nach dem letzten Topic von selbst:

```powershell
./mvnw spring-boot:run "-Dspring-boot.run.profiles=eval"
```

Hinweise:
- Ein Eval-Lauf ist ein normaler Lauf: Die Empfänger des Topics bekommen die Mail, und der Zeitraum des nächsten Laufs beginnt danach. Eval-Topics deshalb nur mit Mailpit- oder eigener Adresse anlegen
- Wenn derselbe Zeitraum erneut bewertet werden soll: `last_successful_run_at` des Topics in `app.topic_state` zurücksetzen oder die Zeile löschen
- Nach einer neuen Prompt-Version (z. B. `research/v2.txt`) die Version in der Konfiguration umstellen (`newsdigest.prompts.research`), Eval erneut laufen lassen und die Ordner `v1` und `v2` vergleichen
- Rohantworten, die als Fixtures ins Repo sollen, vorher anonymisieren (keine Keys, keine persönlichen Daten)
