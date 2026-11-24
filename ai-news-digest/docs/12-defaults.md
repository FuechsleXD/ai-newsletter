# 12 – Defaults

Alle Werte stehen im Code an EINER Stelle (`@ConfigurationProperties`-Record `NewsDigestProperties` im Package `config`, Präfix `newsdigest`), sind über `application.yml` bzw. Umgebungsvariablen überschreibbar und per Test abgesichert. Wo eine DB-Spalte existiert, überschreibt sie den Default.

## Läufe
| Einstellung | Default | Überschreibbar in |
|---|---|---|
| Rückblick beim ersten Lauf | 7 Tage | `topic.initial_lookback_days` |
| Standard-Zeitplan (Seed) | Montags 07:00, `Europe/Berlin` | `schedule` |
| Konfiguration neu laden | alle 60 s (zusätzlich sofort nach Änderung per API) | – |
| Manueller Lauf | max. 3 pro Topic und Stunde | – |

## Modelle und Recherche
| Einstellung | Default | Überschreibbar in |
|---|---|---|
| Aggregator-Modell | kein Default, jedes Topic nennt sein Aggregator-Modell (ADR-013) | `topic.aggregator_model_id` (Pflicht) |
| Erster Anbieter | `ANTHROPIC` (Claude), auch für die Recherche; Modelle und Preise nur in der DB, keine Seed-Daten | `ai_provider`, `ai_model` |
| Temperatur | Anbieter-Standard (nicht gesendet, wenn NULL); nur pflegbar, wenn `ai_model.supports_temperature` (Default `false`, aktuelle Claude-Modelle lehnen sie ab) | `ai_model.temperature` |
| Max-Tokens | 16000 (`newsdigest.ai.default-max-output-tokens`) | `ai_model.max_output_tokens` |
| Websuchen pro Recherche-Aufruf | max. 10 (`newsdigest.ai.max-web-searches-per-call`, Kostenbremse). Im Eval lieferte Recherche `v3` mit 5 Suchen nur 1 Meldung, mit 10 Suchen 4 gültige aus echten Artikeln; Kosten mit Claude Sonnet 5.5 etwa 0,41 USD je Lauf (rund 1,80 USD im Monat für ein wöchentliches Topic) | – |
| Timeout pro Modellaufruf | 120 s | – |
| Gleichzeitige Aufrufe pro Anbieter | 2 | `ai_provider.max_concurrent_calls` |
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

## Kosten
| Einstellung | Default | Überschreibbar in |
|---|---|---|
| Monatsbudget | 10 USD (Startwert, nach den ersten Läufen anhand der echten Kosten anpassen) | `budget` |
| Warnschwelle | 80 % (`warn-percent`, ganze Prozent wie `budget.warn_percent`) | `budget` |
| Verhalten bei erreichtem Budget | Geplante und manuelle Läufe werden nicht gestartet | – |

## Prozess
| Einstellung | Default |
|---|---|
| Retries externer Tasks | 3 Versuche, 5 min Abstand (`R3/PT5M`) |
| Job-Executor-Threads | mindestens Summe der parallelen Aufrufe aller Anbieter + 2 |
| `historyTimeToLive` | 30 Tage (Engine-Default, gesetzt von `ProcessEngineSettings`) |
| Sperrzeit eines Jobs (`lockTimeInMillis`) | 10 min |
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
| Sprache | Deutsch |
| Themen-Karten | 4–6, je 2–3 Sätze (`newsdigest.output.min-topic-cards`, `.max-topic-cards`); weniger sind erlaubt, wenn die Meldungen nicht für mehr Themen reichen |
| Längengrenzen | Überschriften 60 Zeichen, Texte 320 Zeichen (`newsdigest.output.max-heading-chars`, `.max-text-chars`); stehen im Prompt, gekürzt wird vor dem Rendern |
| Icon-Schlüssel | feste Liste in `src/main/resources/pdf/icon-keys.txt`, unbekannte werden `default` |
| PDF | A4, eine Seite |
| Mail | HTML-Inhalt + PDF-Anhang |
| Mail-Betreff | `[News Digest] <Topic> – <Datum>` |
| Absender, Betreiber-Empfänger | nur über Umgebungsvariablen (`NEWSDIGEST_MAIL_FROM`, `NEWSDIGEST_MAIL_OPERATOR`); ohne sie startet die App nicht |
| SMTP | `SPRING_MAIL_HOST`/`SPRING_MAIL_PORT`, Default `localhost:1025` (Mailpit); der Mail-Health-Check ist aus, damit ein SMTP-Ausfall die App nicht als down meldet |
| Eval (Profil `eval`) | Topics `newsdigest.eval.topics` (leer), Wartezeit je Lauf 15 min, Ausgabe `data/eval` |
| Aktive Prompt-Version | Recherche `v3`, Aggregation `v2`, JSON-Reparatur `v1` (`newsdigest.prompts.research`, `.aggregate`, `.repair-json`); Begründung in `src/main/resources/prompts/README.md` |

## Aufbewahrung
| Daten | Frist |
|---|---|
| `provider_result.raw_response` | 90 Tage, danach geleert |
| Läufe mit Meldungen, Ergebnissen und Mail-Protokoll | 2 Jahre |
| PDFs in S3 | 2 Jahre (Lifecycle-Regel) |
| Datenbank-Backups in S3 | 30 Tage |
| Container-Logs in CloudWatch | 30 Tage |

## API
| Einstellung | Default |
|---|---|
| Seitengröße | 20, maximal 100 |
