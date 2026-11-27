# 12 – Defaults

Alle Werte stehen im Code an EINER Stelle (`@ConfigurationProperties`-Record `NewsDigestProperties` im Package `config`, Präfix `newsdigest`), sind über `application.yml` bzw. Umgebungsvariablen überschreibbar und per Test abgesichert. Wo eine DB-Spalte existiert, überschreibt sie den Default.

## Läufe
| Einstellung | Default | Überschreibbar in |
|---|---|---|
| Rückblick beim ersten Lauf | 7 Tage | `topic.initial_lookback_days` |
| Standard-Zeitplan (Seed) | Montags 07:00, `Europe/Berlin` | `schedule` |
| Konfiguration neu laden | alle 60 s (zusätzlich sofort nach Änderung per API) | – |
| Manueller Lauf | max. 3 pro Topic und Stunde | – |
| Start nach Zeitplan | nur im Profil `prod` (`newsdigest.scheduler.enabled`) | – |
| Verpassten Start nachholen | innerhalb von 6 h nach dem geplanten Zeitpunkt, einmal beim Start der App (`newsdigest.scheduler.misfire-grace`) | – |
| Höchstdauer eines Laufs | 3 h, danach Abbruch (`CANCELLED`) mit Betreiber-Mail (`newsdigest.run.max-duration`) | – |
| Wartungsjob | 10 min nach dem Start der App, dann alle 24 h (`newsdigest.maintenance.initial-delay`, `.interval`; aus mit `.enabled=false`) | – |

## Modelle und Recherche
| Einstellung | Default | Überschreibbar in |
|---|---|---|
| Aggregator-Modell | kein Default, jedes Topic nennt sein Aggregator-Modell (ADR-013) | `topic.aggregator_model_id` (Pflicht) |
| Erster Anbieter | `ANTHROPIC` (Claude), auch für die Recherche; Modelle und Preise nur in der DB, keine Seed-Daten | `ai_provider`, `ai_model` |
| Temperatur | Anbieter-Standard (nicht gesendet, wenn NULL); nur pflegbar, wenn `ai_model.supports_temperature` (Default `false`, aktuelle Claude-Modelle lehnen sie ab) | `ai_model.temperature` |
| Max-Tokens | 16000 (`newsdigest.ai.default-max-output-tokens`) | `ai_model.max_output_tokens` |
| Websuchen pro Recherche-Aufruf | max. 15 (`newsdigest.ai.max-web-searches-per-call`, Kostenbremse). Im Eval lieferte Recherche `v3` mit 5 Suchen nur 1 Meldung, mit 10 Suchen 4 gültige aus echten Artikeln; Lauf 8 zeigte, dass 10 Suchen für einen breiten Schwerpunkt mit 6-10 Aspekten nicht reichen, deshalb ab `v7` 15. Kosten mit Claude Sonnet 5.5 bei 10 Suchen etwa 0,41-0,59 USD je Lauf, bei 15 geschätzt 0,55-0,80 USD (rund 2,50-3,50 USD im Monat für ein wöchentliches Topic) | – |
| Timeout pro Modellaufruf | 300 s (`newsdigest.ai.call-timeout`); Recherche mit 15 Websuchen dauerte mit Claude Sonnet 5.5 bis 203 s (Eval-Lauf 9), mit 120 s brach Lauf 10 ab. Keine automatischen Wiederholungen im SDK (`maxRetries` 0, ADR-014) | – |
| Gleichzeitige Aufrufe pro Anbieter | 2 (`newsdigest.ai.max-concurrent-calls-per-provider`); gilt für Recherche, Reparatur und Aggregation; der Wert der Zeile wird beim ersten Aufruf gelesen, Änderung wirkt nach Neustart | `ai_provider.max_concurrent_calls` |
| Wartezeit auf einen freien Aufrufplatz | 5 min (`newsdigest.ai.max-wait-for-call-slot`), danach Wiederholung des Jobs; zusammen mit den Aufrufen eines Jobs unter der Sperrzeit (beim Start geprüft) | – |
| Meldungen pro Modell | max. 10 | – |
| Reparaturversuche bei unlesbarem JSON | 1 | – |
| Duplikatprüfung gegen frühere Läufe | letzte 90 Tage | – |
| Überschriften des letzten Laufs an den Aggregator | ja | – |

## Anmeldung (Keycloak)
| Einstellung | Default |
|---|---|
| Lebensdauer Access Token | 5 min |
| Lebensdauer Keycloak-Sitzung (Leerlauf / maximal) | 30 min / 10 h |
| Toleranz Uhrzeitabweichung bei der Prüfung | 30 s |

## Secrets
| Einstellung | Default |
|---|---|
| Quelle | Umgebungsvariablen (`newsdigest.secrets.source=environment`); Profil `prod`: AWS Secrets Manager (`aws`) |
| Lebensdauer gecachter Secrets im Speicher | 15 min (`newsdigest.secrets.cache-ttl`) |

## URL-Prüfung
| Einstellung | Default |
|---|---|
| Timeout | 5 s |
| Max. Weiterleitungen | 3 |
| Gleichzeitige Prüfungen | 5 |
| Als nicht erreichbar gilt | DNS-Fehler, keine Verbindung, Timeout, gesperrtes Ziel (SSRF), zu viele Weiterleitungen, HTTP 404/410; alle anderen Status (auch 401/403/429/5xx) gelten als erreichbar |
| User-Agent | `ai-news-digest-urlcheck/1.0` |

## Kosten
| Einstellung | Default | Überschreibbar in |
|---|---|---|
| Monatsbudget | 10 USD (Startwert, nach den ersten Läufen anhand der echten Kosten anpassen) | `budget` |
| Warnschwelle | 80 % (`warn-percent`, ganze Prozent wie `budget.warn_percent`) | `budget` |
| Verhalten bei erreichtem Budget | Geplante und manuelle Läufe werden nicht gestartet | – |
| Verhalten bei erreichter Warnschwelle | Einmal pro Kalendermonat eine Mail an den Betreiber mit Ausgaben, Grenze und den drei teuersten Topics; geprüft nach jedem Lauf | – |
| Preisstufe für lange Prompts | keine; pro Modell pflegbar (Schwelle in Input-Token eines Aufrufs, darüber höhere Preise für den ganzen Aufruf), z. B. Claude Haiku 5.5: 0,10/0,50 USD bis 100.000 Token, darüber 0,50/2,50 | `ai_model.long_context_*` |
| Kosten abgebrochener Aufrufe | geschätzt: Input-Token des Prompts plus höchstmögliche Websuchen, markiert als `cost_estimated`; Suchinhalte unbekannt, echte Kosten können höher sein | – |

## Prozess
| Einstellung | Default |
|---|---|
| Retries externer Tasks | 3 Versuche, 5 min Abstand (`R3/PT5M`) |
| Job-Executor-Threads | mindestens Summe der parallelen Aufrufe aller Anbieter + 2; derzeit `core-pool-size` 4, `max-pool-size` 10 (`camunda.bpm.job-execution` in `application.yml`) |
| `historyTimeToLive` | 30 Tage (Engine-Default, gesetzt von `ProcessEngineSettings`) |
| Sperrzeit eines Jobs (`lockTimeInMillis`) | 20 min (`newsdigest.process.job-lock-time`); muss länger sein als Wartezeit auf einen Aufrufplatz plus alle Aufrufe eines Jobs (beim Start geprüft). Wirkt nur nach einem Absturz: dann wird ein verwaister Job nach dieser Zeit erneut vergeben |
| Stop-Zeit beim Herunterfahren | 3 min |

## Rendering
| Einstellung | Default |
|---|---|
| Gotenberg-URL | `http://localhost:3000` (`newsdigest.render.gotenberg-url`), in Produktion der interne Docker-Name |
| Timeout Gotenberg | 30 s |
| Nachkürzen bei mehr als einer Seite | 1 Versuch (ab Schritt 17) |
| PDF-Ablage | lokal `data/pdfs` (`newsdigest.storage.type=local`, `.local-directory`); Profil `prod`: S3 (`type=s3`, Bucket `newsdigest.storage.s3-bucket` aus Terraform) |
| AWS-Region | `eu-central-1` (`newsdigest.aws.region`, ADR-015) |

## Ausgabe
| Einstellung | Default |
|---|---|
| Sprache | Englisch (`newsdigest.output.language=en`; `de` möglich, feste Texte aus `messages/digest*.properties`, ADR-017) |
| Themen-Karten | 4–6, je 2–3 Sätze (`newsdigest.output.min-topic-cards`, `.max-topic-cards`); weniger sind erlaubt, wenn die Meldungen nicht für mehr Themen reichen |
| Längengrenzen | Überschriften 60 Zeichen, Texte 320 Zeichen (`newsdigest.output.max-heading-chars`, `.max-text-chars`); stehen im Prompt, gekürzt wird vor dem Rendern |
| Icon-Schlüssel | feste Liste in `src/main/resources/pdf/icon-keys.txt`, unbekannte werden `default` |
| PDF | A4, eine Seite |
| Mail | HTML-Inhalt + PDF-Anhang |
| Mail-Betreff | `[News Digest] <Topic> – <Datum>` |
| Absender, Betreiber-Empfänger | nur über Umgebungsvariablen (`NEWSDIGEST_MAIL_FROM`, `NEWSDIGEST_MAIL_OPERATOR`); ohne sie startet die App nicht |
| SMTP | `SPRING_MAIL_HOST`/`SPRING_MAIL_PORT`, Default `localhost:1025` (Mailpit); der Mail-Health-Check ist aus, damit ein SMTP-Ausfall die App nicht als down meldet |
| Eval (Profil `eval`) | Topics `newsdigest.eval.topics` (leer), Wartezeit je Lauf 15 min, Ausgabe `data/eval` |
| Aktive Prompt-Version | Recherche `v7`, Aggregation `v4`, JSON-Reparatur `v2` (englisch, ADR-017) (`newsdigest.prompts.research`, `.aggregate`, `.repair-json`); Begründung in `src/main/resources/prompts/README.md` |

## Aufbewahrung
| Daten | Frist |
|---|---|
| `provider_result.raw_response` | 90 Tage, danach geleert |
| Läufe mit Meldungen, Ergebnissen und Mail-Protokoll | 2 Jahre |
| PDFs in S3 | 2 Jahre (Lifecycle-Regel) |
| Datenbank-Backups in S3 | 30 Tage |
| Budgetwarnungen (`budget_warning`) | wie Läufe, 2 Jahre |
| Engine-Historie | `historyTimeToLive` (30 Tage); der Wartungsjob startet den History-Cleanup der Engine |
| Container-Logs in CloudWatch | 30 Tage (`log_retention_days`) |
| EBS-Snapshots des Datenvolumes | täglich 02:00 UTC, 7 Stück (`snapshot_retention_count`) |
| AWS-Budget (Infrastruktur) | 40 USD im Monat (`monthly_budget_usd`), Meldung bei 80 % und Prognose 100 % |

## API
| Einstellung | Default |
|---|---|
| Seitengröße | 20, maximal 100 |
