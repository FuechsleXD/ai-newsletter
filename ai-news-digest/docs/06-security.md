# 06 – Security

## Grundsätze
- Default deny: alle `/api/**`- und Engine-Endpunkte nur mit Authentifizierung
- Actuator (Health, Metriken) nur intern erreichbar (eigener Management-Port, nicht über den Reverse Proxy veröffentlicht)
- Secrets ausschließlich im AWS Secrets Manager (siehe Abschnitt "Secrets")

## Secrets (AWS Secrets Manager als Key Vault)
| Secret | Inhalt |
|---|---|
| `newsdigest/prod/ai/<provider-code>` | API-Key je KI-Anbieter, z. B. `newsdigest/prod/ai/anthropic` |
| `newsdigest/prod/db/app` | DB-Benutzer der App (nur Datenzugriff) |
| `newsdigest/prod/db/migration` | DB-Benutzer für Flyway (Eigentümer der Schemas) |
| `newsdigest/prod/mail/smtp` | SES-SMTP-Zugangsdaten |

Regeln:
- Model- bzw. API-Keys liegen NIE in Code, Repo, `application.yml`, Umgebungsvariablen des Images, Datenbank, Logs oder API-Antworten
- In der Datenbank steht je Anbieter nur der Name des Secrets (`ai_provider.secret_name`), nie der Wert
- Laufzeit-Secrets (KI-Keys): Die App liest sie über die IAM-Rolle der Instanz mit dem AWS SDK (`AwsSecretsManagerSecretProvider`, Profil `prod`) und hält sie mit kurzer Lebensdauer im Speicher (Default siehe `12-defaults.md`), damit ein rotierter Key ohne Neustart wirkt
- Start-Secrets (DB-Passwörter, SES-SMTP): Das Deploy-Skript auf dem Server liest sie aus dem Secrets Manager und übergibt sie als Umgebungsvariablen an `docker-compose.prod.yml`; sie liegen nie im Repo oder im Image, Rotation per Neustart (ADR-015)
- Die IAM-Rolle darf nur `secretsmanager:GetSecretValue` auf `newsdigest/prod/*`
- Fehlt ein Secret für einen aktiven Anbieter, schlagen dessen Aufrufe mit klarer Meldung fehl; die App startet trotzdem
- Lokal (`dev`): Keys aus einer nicht eingecheckten Datei `.env` (in `.gitignore`); eine `.env.example` ohne Werte liegt im Repo. Ohne Profil `prod` liest `EnvironmentSecretProvider` ein Secret aus der Umgebungsvariablen, deren Name aus dem Secret-Namen entsteht (Großbuchstaben, alles außer Buchstaben und Ziffern wird `_`): `newsdigest/prod/ai/anthropic` → `NEWSDIGEST_PROD_AI_ANTHROPIC`
- In Tests werden keine echten Keys verwendet
- Rotation: KI-Keys bei Verdacht sofort, sonst regelmäßig manuell; DB-Passwörter bei jedem Wechsel der Betreuung

## Datenbank-Benutzer
- `newsdigest_migration`: Eigentümer der Schemas `app` und `bpm`, wird nur von Flyway verwendet (`spring.flyway.user`)
- `newsdigest_app`: nur Lesen und Schreiben von Daten in `app` und `bpm`, keine DDL-Rechte
- Lokal und in Tests legt das Init-Skript `docker/postgres/init/01-create-users.sh` beide Benutzer an (Passwörter aus `.env`); der Superuser des Containers wird von der App nicht verwendet

## Authentifizierung mit Keycloak und JWT (siehe ADR-009)
Keycloak ist der Identity Provider. Er verwaltet Benutzer, Passwörter und Rollen und stellt die JWTs aus. Das Backend stellt selbst keine Tokens aus und speichert keine Passwörter; es prüft nur.

### Keycloak
- Eigener Container auf der EC2 (Version gepinnt), im Produktionsmodus (`start`, nicht `start-dev`), hinter Caddy unter `https://<domain>/auth`
- Eigene Datenbank `keycloak` in der vorhandenen PostgreSQL-Instanz, eigener DB-Benutzer, im täglichen Backup enthalten
- Realm `newsdigest` als Code: `infra/keycloak/realm-newsdigest.json` (ohne Benutzer und Secrets), beim Start importiert; Änderungen am Realm nur über diese Datei
- Clients:
  - `newsdigest-ui`: öffentlicher Client, Authorization Code Flow mit PKCE (S256), Redirect-URIs nur die eigene Domain (lokal `http://localhost:5173`)
  - Audience-Mapper, damit Access Tokens `aud = newsdigest-api` enthalten
- Realm-Rollen `ADMIN` und `VIEWER`; Benutzer lege ich in der Keycloak-Admin-Konsole an
- Admin-Konsole nicht öffentlich: Caddy veröffentlicht nur die Pfade für Login und Tokens (`/auth/realms/...`, `/auth/resources/...`); die Konsole erreiche ich per SSM-Portweiterleitung
- Brute-Force-Schutz an, Passwort-Richtlinie gesetzt, TOTP (Zwei-Faktor) für `ADMIN` Pflicht
- Keycloak-Admin-Zugang und DB-Passwort im AWS Secrets Manager

### JWT-Prüfung im Backend
- Spring Security als OAuth2 Resource Server, konfiguriert über `issuer-uri` (Schlüssel per JWKS, automatische Schlüsselrotation)
- Geprüft werden: Signatur (RS256), Issuer, Audience `newsdigest-api`, Ablaufzeit (`exp`) und `nbf` mit höchstens 30 s Toleranz
- Nur Access Tokens werden akzeptiert, keine ID Tokens (Prüfung über `typ`/Audience)
- Rollen kommen aus dem Claim `realm_access.roles` und werden auf `ROLE_ADMIN` und `ROLE_VIEWER` abgebildet (eigener `JwtAuthenticationConverter`); unbekannte Rollen werden ignoriert
- Kein Session-Zustand im Backend (`STATELESS`) für `/api/**`; daher kein CSRF für die API
- Benutzerkennung für Audit-Felder und Logs: `sub` bzw. `preferred_username`, keine Mailadressen loggen

### Engine-UI (CIB seven)
- Eigene Security-Filterkette für `/bpm/**` und die Engine-REST-API: OAuth2 Login (Authorization Code) gegen Keycloak mit eigenem vertraulichem Client `newsdigest-bpm` (Client-Secret im AWS Secrets Manager), Cookie-Session mit CSRF-Schutz
- Plugin `cibseven-keycloak` als Identity Provider der Engine (nur lesend); dafür ein eigener Keycloak-Client mit minimalen Leserechten auf Benutzer und Gruppen
- Engine-Berechtigungen (`authorization.enabled`) an; nur Gruppe `ADMIN`
- Plugin-Version passend zu CIB seven wählen und Konfiguration in der Plugin-Doku prüfen; es ist eine Community-Erweiterung

### Tokens im Browser
- OIDC-Client-Bibliothek im Frontend (z. B. `oidc-client-ts` mit `react-oidc-context`; Auswahl beim Frontend-Grundgerüst bestätigen)
- Access Token nur im Speicher, nicht in `localStorage`; Erneuerung über Refresh Token (Rotation an)
- Lebensdauer siehe `12-defaults.md`
- Abmelden beendet auch die Keycloak-Sitzung

## Rollen
| Rolle | Rechte |
|---|---|
| `VIEWER` | Alles lesen, PDFs und Mail-Vorschau ansehen, Kosten sehen |
| `ADMIN` | Zusätzlich Konfiguration und Budget ändern, Läufe manuell starten, Engine-UI und Engine-REST |

Prüfung auf Request- und Methoden-Ebene.

## Transport und Header
- HTTPS über den Reverse Proxy (Caddy mit automatischem Let's-Encrypt-Zertifikat); das Backend lauscht nur lokal bzw. im Docker-Netz
- HSTS, CSP, `X-Content-Type-Options`; Einbettung in Frames überall verboten (`frame-ancestors 'none'`), die Engine-UI öffnet in einem eigenen Tab
- Frontend, API und Engine-UI unter derselben Domain, daher kein CORS
- CSRF-Schutz überall, wo Cookie-Sessions genutzt werden (z. B. Engine-Webapp)
- Server nur mit Ports 80/443 öffentlich; kein SSH aus dem Internet (Zugriff über AWS Systems Manager)

## URL-Prüfung (SSRF-Schutz)
Die Prüfung der Quellen ruft URLs auf, die ein LLM geliefert hat. Daher:
- Nur `http`/`https`, nur Standard-Ports
- DNS auflösen und private, Loopback-, Link-Local- und Metadaten-Adressen ablehnen (u. a. `10.0.0.0/8`, `172.16.0.0/12`, `192.168.0.0/16`, `127.0.0.0/8`, `169.254.0.0/16`, IPv6-Pendants); Prüfung auch nach jeder Weiterleitung
- Höchstens 3 Weiterleitungen, kurzer Timeout, nur Header bzw. erste Bytes lesen, keine Cookies, eigener User-Agent
- Der Inhalt der Seite wird nicht weiterverarbeitet

Umsetzung (Schritt 14): `UrlChecker` mit `UrlCheckPolicy.PUBLIC_ONLY` und dem JDK-`HttpClient` (Weiterleitungen aus, von Hand gefolgt). Gesperrt sind neben den genannten Bereichen auch `0.0.0.0/8`, `100.64.0.0/10` (CGNAT), `192.0.0.0/24`, `198.18.0.0/15`, `240.0.0.0/4`, Multicast, IPv6 `::`, `::1`, `fc00::/7`, `fe80::/10`, `fec0::/10` sowie in IPv6 eingebettete IPv4-Adressen (`::a.b.c.d`, `::ffff:a.b.c.d`, NAT64 `64:ff9b::/96`); hat ein Host mehrere Adressen, muss jede öffentlich sein. Anfragen: `HEAD`, bei 405/501 `GET` mit `Range: bytes=0-0`, Body wird ohne Lesen geschlossen, User-Agent `ai-news-digest-urlcheck/1.0`, keine Cookies. Restrisiko: Der HTTP-Client löst den Host beim Verbinden erneut auf (DNS-Rebinding zwischen Prüfung und Anfrage). Gelesen wird nur der Status, der Inhalt nie verwendet, und die Metadaten der Instanz verlangen ein Token (IMDSv2, `http_tokens = required`), das eine einfache Anfrage nicht bekommt. Tests laufen nur gegen einen lokalen Stub mit einer Richtlinie, die zusätzlich Loopback erlaubt; Prozesstests nutzen einen Fake ohne HTTP

## Gotenberg
- Nur im internen Docker-Netz erreichbar, nie über Caddy veröffentlicht
- Chromium darf keine externen URLs laden; alle Ressourcen kommen im Request mit
- Umgesetzt über die Startoptionen (Gotenberg 8.40.0, `docker-compose.yml` und Tests): `--chromium-deny-public-ips`, `--chromium-deny-private-ips`, `--chromium-disable-javascript`, `--api-disable-download-from`, `--webhook-disable`; die Standard-Sperrliste erlaubt nur die mitgeschickten Dateien unter `file:///tmp/`
- Texte aus KI-Antworten sind im HTML escaped, bevor sie an Gotenberg gehen

## Prompt-Injection
- Inhalte aus Recherche-Antworten gelten als nicht vertrauenswürdig: Der Aggregator-Prompt trennt Anweisungen klar von Daten
- HTML-Mail und PDF escapen alle Texte aus KI-Antworten; Links nur, wenn die URL die Prüfung bestanden hat

## Weitere Regeln
- Dev-Endpunkte (z. B. manueller Start im Durchstich: `POST /dev/topics/{id}/runs`, Abbruch: `POST /dev/runs/{id}/cancel`) gibt es nur im Profil `dev`; ein Test prüft, dass sie ohne das Profil nicht existieren
- Keine Stacktraces in Antworten, keine Secrets oder Mailadressen in Logs
- API-Keys tauchen in keiner API-Antwort auf
- Abhängigkeiten und Images werden in der CI auf Schwachstellen geprüft (Dependency Review, Trivy; siehe `11-deployment-cicd.md`)
- Server: kein SSH-Port, Zugang nur per SSM; IMDSv2 Pflicht; verschlüsselte Volumes; S3-Buckets privat, nur TLS, versioniert; Instanzrolle nur mit den Rechten aus `infra/terraform/security.tf`; GitHub nur per OIDC (`github-oidc.tf`, ADR-016)
- Container: App läuft als Nicht-Root-Benutzer, das Image enthält keine Secrets; PostgreSQL und Gotenberg liegen in einem Docker-Netz ohne Internetzugang, öffentlich ist nur Caddy (80/443)
