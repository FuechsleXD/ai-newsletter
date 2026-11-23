# 03 – Datenmodell (PostgreSQL, 3. Normalform)

## Konventionen
- Tabellen und Spalten `snake_case`, Tabellennamen im Singular
- Primärschlüssel `bigint generated always as identity`
- Zeitstempel `timestamptz` (UTC), Beträge `numeric(12,6)` in USD (Abrechnungswährung der Anbieter)
- Jeder Fremdschlüssel hat einen Index
- Status-Werte per `CHECK`-Constraint, `NOT NULL` wo möglich, Eindeutigkeit per `UNIQUE`
- Konfigurationstabellen (`topic`, `schedule`, `ai_provider`, `ai_model`, `recipient`) haben `version` (Optimistic Locking), `created_at`, `updated_at`

## Konfiguration
| Tabelle | Spalten | Schlüssel / Regeln |
|---|---|---|
| `schedule` | id, name, cron_expression, timezone | `name` UNIQUE; von mehreren Topics nutzbar |
| `topic` | id, name, description, initial_lookback_days NULL, aggregator_model_id NULL, schedule_id, active | `name` UNIQUE; FK `schedule`, FK `ai_model`; NULL = Default |
| `ai_provider` | id, code, display_name, secret_name, max_concurrent_calls NULL, active | `code` UNIQUE (z. B. `ANTHROPIC`, `OPENAI`, `GEMINI`); `secret_name` = Name des Secrets im AWS Secrets Manager, nie der Key selbst |
| `ai_model` | id, provider_id, model_name, display_name, supports_web_search, supports_structured_output, max_output_tokens NULL, temperature NULL, input_price_per_mtok, output_price_per_mtok, price_per_search_call NULL, active | UNIQUE (provider_id, model_name); Preise in USD pro 1 Mio. Token |
| `topic_model` | topic_id, model_id | PK (topic_id, model_id); Menge der Recherche-Modelle (parallel, keine Reihenfolge) |
| `recipient` | id, email, display_name, active | `email` UNIQUE, in Kleinschreibung |
| `topic_recipient` | topic_id, recipient_id | PK (topic_id, recipient_id) |
| `budget` | id, month_limit_usd, warn_percent, active | Höchstens eine aktive Zeile (Teilindex); ohne aktive Zeile gilt der Default |

## Zustand und Historie
| Tabelle | Spalten | Schlüssel / Regeln |
|---|---|---|
| `topic_state` | topic_id, last_successful_run_at | PK = FK `topic`; 1:1 |
| `digest_run` | id, topic_id, aggregator_model_id, process_instance_id NULL, prompt_version, period_from, period_to, status, started_at, finished_at NULL, error_message NULL, aggregate_json jsonb NULL, pdf_s3_key NULL | Status: `RUNNING`, `COMPLETED`, `NO_NEWS`, `FAILED`, `SKIPPED_BUDGET`, `CANCELLED`; Bedeutung siehe `04-prozess-bpmn.md`; Teilindex UNIQUE (topic_id) WHERE status = 'RUNNING' |
| `provider_result` | id, digest_run_id, model_id, status, raw_response NULL, duration_ms, input_tokens NULL, output_tokens NULL, search_calls NULL, cost_usd NULL, error_message NULL | UNIQUE (digest_run_id, model_id); Status `SUCCEEDED`/`FAILED` |
| `news_item` | id, provider_result_id, headline, summary, url, normalized_url, published_at NULL, status | Status: `VALID`, `OUT_OF_PERIOD`, `DUPLICATE_PREVIOUS`, `DUPLICATE_RUN`, `UNREACHABLE`; Index auf `normalized_url` |
| `aggregation_result` | digest_run_id, model_id, input_tokens, output_tokens, cost_usd, duration_ms | PK = FK `digest_run`; Kosten des Aggregator-Aufrufs |
| `mail_delivery` | id, digest_run_id, recipient_id, status, sent_at NULL, error_message NULL | UNIQUE (digest_run_id, recipient_id); Status `PENDING`, `SENT`, `FAILED` |

## Regeln
- `aggregate_json` ist eine bewusste Denormalisierung (Dokument-Ergebnis des Aggregators)
- `cost_usd` wird beim Lauf berechnet und gespeichert (Preis-Momentaufnahme), weil sich Modellpreise später ändern; das ist eine bewusste Ausnahme von der Ableitbarkeit
- Monatskosten = Summe aus `provider_result.cost_usd` und `aggregation_result.cost_usd` im Kalendermonat (`Europe/Berlin`); als Abfrage bzw. View, nicht gespeichert
- Duplikatprüfung über `news_item.normalized_url` zusammen mit Topic und Status `VALID` früherer erfolgreicher Läufe
- PDFs liegen in S3, in der DB nur der Schlüssel
- `topic_state.last_successful_run_at` wird nur bei `COMPLETED` und `NO_NEWS` auf `period_to` gesetzt
- Aufbewahrung: `raw_response` wird nach einer Frist geleert, Läufe samt Meldungen nach einer längeren Frist gelöscht, PDFs in S3 per Lifecycle-Regel (Fristen in `12-defaults.md`); ein nächtlicher Aufräumjob übernimmt das, Kostenwerte bleiben für die Monatsauswertung erhalten, bis der Lauf gelöscht wird
- Löschen heißt Deaktivieren, sobald Historie auf den Datensatz verweist
- Validierung, die das Schema nicht abbilden kann (Recherche-Modell braucht Web-Suche, Aggregator strukturierte Ausgabe), erfolgt im Service
- Seed-Daten: Standard-Zeitplan, Anbieter-Zeilen, Platzhalter; keine echten Mailadressen, keine geratenen Preise (Preise trage ich selbst ein). Umgesetzt in `V2__app_seed.sql`: Zeitplan „Wöchentlich montags 07:00“ (`0 0 7 * * MON`, `Europe/Berlin`) und Anbieter `ANTHROPIC` (Secret `newsdigest/prod/ai/anthropic`); keine Modelle, kein Budget
- Datumswerte: `digest_run.period_from`/`period_to`, `topic_state.last_successful_run_at` und `news_item.published_at` sind `date` (lokales Datum in der Zeitzone des Zeitplans), alle Zeitpunkte `timestamptz`
- Löschverhalten: Historie eines Laufs (`provider_result`, `news_item`, `aggregation_result`, `mail_delivery`) sowie `topic_model`, `topic_recipient` und `topic_state` werden mit ihrem Eltern-Datensatz gelöscht (`ON DELETE CASCADE`, für den Aufräumjob); Verweise auf Konfiguration (Modell, Zeitplan, Empfänger, Topic eines Laufs) verhindern das Löschen (Deaktivieren statt Löschen)
- Constraint- und Indexnamen: `pk_`, `fk_`, `uq_`, `ck_`, `ix_` + Tabelle + Bedeutung; Tests prüfen auf diese Namen

## Schemas
| Schema | Inhalt | Verwaltet durch |
|---|---|---|
| `app` | Alle eigenen Tabellen dieses Dokuments | Flyway |
| `bpm` | Tabellen von CIB seven | Flyway, mit den offiziellen SQL-Skripten von CIB seven |

Siehe `entscheidungen/ADR-006-engine-schema-per-flyway.md`.

## Engine-Tabellen (CIB seven)
- Die Engine legt keine Tabellen selbst an und ändert keine (Schema-Update der Engine abgeschaltet, z. B. `database-schema-update=false`)
- Die Engine nutzt das Schema `bpm` (Schema-Name und Tabellen-Präfix in der Engine-Konfiguration)
- Die Tabellen entstehen aus den offiziellen Create-Skripten für PostgreSQL aus der CIB-seven-Distribution der verwendeten Version, unverändert als Flyway-Migration übernommen; Herkunft (Version, Dateiname) steht als Kommentar in der Migration
- Versionswechsel von CIB seven: die offiziellen Upgrade-Skripte von alter zu neuer Version als neue Migrationen im selben Pull Request wie die Versionsänderung
- Engine-Migrationen werden nie von Hand angepasst; eigene Indizes oder Änderungen an Engine-Tabellen sind verboten
- Genaue Konfigurationsnamen und Skriptpfade in der CIB-seven-Doku prüfen, nicht raten
- Ein Test startet die Engine gegen das per Flyway erzeugte Schema und führt einen Prozess aus; damit fällt eine fehlende oder falsche Engine-Migration in der CI auf

## Flyway
Abhängigkeiten: `flyway-core` und `flyway-database-postgresql` (Pflicht ab Flyway 10), Versionen über das Spring-Boot-BOM.

Ordner und Benennung:
| Ordner | Inhalt | Profil |
|---|---|---|
| `src/main/resources/db/migration/app` | Versionierte Migrationen für `app` | alle |
| `src/main/resources/db/migration/bpm` | Offizielle CIB-seven-Skripte für `bpm` | alle |
| `src/main/resources/db/migration/views` | Repeatable Migrations (`R__`) für Views, z. B. Monatskosten | alle |
| `src/main/resources/db/dev-data` | Beispieldaten für lokale Entwicklung | nur `dev` |

- Versionierte Migrationen: `V<nummer>__<bereich>_<beschreibung>.sql`, z. B. `V1__app_initial_schema.sql`, `V2__app_seed.sql`, ab Schritt 4 `V3__bpm_create_engine_3_0.sql`; eine gemeinsame, fortlaufende Nummernfolge über alle Ordner und eine Historientabelle
- Repeatable Migrations (`R__<beschreibung>.sql`) nur für Views, nie für Tabellen
- Angewendete Migrationen werden nie verändert, Änderungen immer als neue Migration
- Eine Migration pro logischer Änderung
- Abwärtskompatibel zur vorherigen App-Version (erst hinzufügen, später entfernen), damit Rollbacks möglich bleiben

Einstellungen:
- `spring.flyway.schemas=app,bpm`, Historientabelle in `app` (bis Schritt 4 nur `app`)
- `clean-disabled=true` in allen Profilen
- `out-of-order=false`, `validate-on-migrate=true`
- Flyway läuft beim Start der App, bevor die Engine startet
- Flyway läuft mit dem Migrationsbenutzer (Eigentümer der Schemas), die App mit einem Benutzer ohne DDL-Rechte (siehe `06-security.md`)
- Die Rechte des App-Benutzers vergibt `V1` per `ALTER DEFAULT PRIVILEGES` (nur `SELECT`, `INSERT`, `UPDATE`, `DELETE` bzw. Sequenzen); sein Name kommt über den Flyway-Platzhalter `${appUser}` (= `spring.datasource.username`), nie fest im SQL
- Die App-Verbindungen nutzen `currentSchema=app` (Hikari-Datenquelle), Hibernate `default_schema=app`
- Die Benutzer selbst legt Flyway nicht an: lokal und in Tests das Init-Skript `docker/postgres/init/01-create-users.sh`, in Produktion das Runbook

Tests:
- Alle Migrationen laufen mit Testcontainers auf einer leeren PostgreSQL-Datenbank durch (gleiches Image und gleiches Init-Skript wie im Compose; Basisklasse `PostgresTestSupport`)
- Constraints, Teilindizes, Löschverhalten, Rechte des App-Benutzers und der View `monthly_cost` sind in `SchemaMigrationTest` abgesichert, die Abbildung aller Entities in `EntityMappingTest`
- Hibernate-Validierung gegen `app` ist grün
- Engine startet gegen `bpm` und führt einen Testprozess aus
