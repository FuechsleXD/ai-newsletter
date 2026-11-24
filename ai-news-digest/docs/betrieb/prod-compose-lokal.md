# Runbook: Produktions-Compose lokal ausprobieren

`docker-compose.prod.yml` (App, Caddy, PostgreSQL, Gotenberg) auf dem eigenen Rechner starten, bevor es auf den Server geht (Roadmap Schritt 10). Mit `DOMAIN=localhost` stellt Caddy ein Zertifikat seiner internen CA aus; es gibt keinen Let's-Encrypt-Aufruf. Die Ports 80 und 443 müssen frei sein.

## Schnell: Smoke-Test
Derselbe Test wie in der CI. Er startet den Verbund mit Wegwerf-Werten, prüft, was von außen erreichbar ist, und räumt danach alles ab (auch die Volumes). In Git Bash im Ordner `ai-news-digest`:

```bash
docker build -t newsdigest:local .
docker/smoke-test.sh newsdigest:local
```

Erwartet: `/health` liefert `{"status":"UP"}`, `/` die Platzhalterseite, `/actuator/env`, `/api/...` und `/dev/...` ebenfalls nur die Platzhalterseite, HTTP leitet auf HTTPS um.

## Von Hand
1. Image bauen: `docker build -t newsdigest:local .`
2. `.env.prod.example` nach `.env.prod` kopieren (wird nicht eingecheckt) und ausfüllen: `DOMAIN=localhost`, `APP_IMAGE=newsdigest:local`, Passwörter, Mail-Werte (lokal genügen Platzhalter wie `mail.invalid`, verschickt wird nichts ohne Lauf)
3. Starten:

```powershell
docker compose -f docker-compose.prod.yml --env-file .env.prod up -d --wait
```

4. Prüfen: https://localhost/health (Browser warnt wegen der internen CA) und Logs:

```powershell
docker compose -f docker-compose.prod.yml --env-file .env.prod logs -f app
```

5. Stoppen; `-v` löscht auch Datenbank und Zertifikate:

```powershell
docker compose -f docker-compose.prod.yml --env-file .env.prod down -v
```

Hinweise:
- Der lokale Entwicklungs-Verbund (`docker-compose.yml`) und dieser Verbund haben verschiedene Projektnamen und Volumes; sie können nacheinander laufen, aber PostgreSQL des Entwicklungs-Verbunds belegt nur `127.0.0.1:5432`, hier wird kein DB-Port veröffentlicht
- Die API und die Engine-UI sind absichtlich nicht über Caddy erreichbar, bis die Security umgesetzt ist (Schritt 20)
