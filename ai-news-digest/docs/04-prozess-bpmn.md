# 04 – BPMN-Prozess `digestRun`

## Grundsatz: Ablauf im BPMN, Konfiguration in der Datenbank
Das BPMN-Modell beschreibt, WIE ein Lauf abläuft. WOMIT er läuft (welche Modelle, welches Aggregator-Modell, welche Empfänger), steht in der Datenbank und wird beim Start als Prozessvariable übergeben. Keine Modell-, Anbieter- oder Empfängernamen im BPMN-XML. Siehe `entscheidungen/ADR-002-modellwahl-in-der-datenbank.md`.

## Ablauf
```
Start (topicId)
 -> [Lauf vorbereiten]        Run anlegen (RUNNING), Zeitraum, Modell-IDs, Aggregator, Prompt-Version
 -> [Budget prüfen]
 -> <XOR: Budget ok?>
      nein -> [Betreiber informieren] -> Ende (SKIPPED_BUDGET)
      ja   -> <+ Parallel-Gateway (Fork)>
                 -> [Recherche]  parallele Multi-Instance über `models`, ein Zweig pro Modell
           -> <+ Parallel-Gateway (Join)>  wartet, bis ALLE Recherchen beendet sind
           -> [Meldungen prüfen]           Zeitraum, Duplikate, URL-Erreichbarkeit
           -> <XOR: Ergebnis der Recherche?>
                kein Modell erfolgreich -> [Betreiber informieren] -> Ende (FAILED)
                keine gültige Meldung   -> [Lauf abschließen]      -> Ende (NO_NEWS)
                gültige Meldungen       -> [Aggregieren]
                     Fehler (Boundary-Event AGGREGATION_FAILED) -> [Betreiber informieren] -> Ende (FAILED)
                     -> [HTML + PDF erzeugen, PDF in S3]
                          keine aktiven Empfänger (Boundary-Event NO_RECIPIENTS) -> [Betreiber informieren] -> Ende (FAILED)
                     -> [Mails versenden]
                     -> <XOR: Alle zugestellt?>
                          nein (mind. einer erreicht) -> [Betreiber informieren]
                     -> [Lauf abschließen]    last_successful_run_at setzen
                     -> Ende (COMPLETED)
```

## Parallele Recherche
Da die Anzahl der Modelle pro Topic variabel ist, entstehen die parallelen Zweige dynamisch über eine **parallele Multi-Instance** (`isSequential="false"`) über die Collection `models`. Ein statisches Parallel-Gateway mit festen Zweigen pro Modell wäre fest verdrahtet und widerspräche ADR-002.

Modellierung:
- Das Fork-/Join-Paar aus Parallel-Gateways umschließt die Multi-Instance-Aktivität und macht die Parallelität im Diagramm sichtbar. Es ist zugleich der Erweiterungspunkt für weitere parallele Zweige (z. B. später ein Zweig, der parallel zur Recherche Daten vorbereitet)
- Die Multi-Instance-Aktivität ist erst abgeschlossen, wenn ALLE Instanzen beendet sind; erst danach geht der Token über den Join zum Prüfen und zum Aggregator
- Ein fehlgeschlagenes Modell beendet seine Instanz regulär mit Status `FAILED` im `provider_result` (kein BPMN-Error, keine Abbruchbedingung `completionCondition`)

Technische Regeln (wichtig für echte Parallelität):
- Wo die Attribute stehen (in Camunda 7 / CIB seven leicht zu verwechseln): `async*` und `exclusive` am `serviceTask` gelten für den **Multi-Instance-Body**, an `multiInstanceLoopCharacteristics` für **jede innere Instanz**. Bis Schritt 13a standen sie am Task; dann gab es nur einen Job für den Body, der die Instanzen nacheinander im selben Thread ausführte
- Innere Instanzen (`multiInstanceLoopCharacteristics`): `asyncBefore="true"` und `camunda:exclusive="false"`, damit jede Instanz ein eigener, nicht exklusiver Job ist; exklusive Jobs würden die Instanzen derselben Prozessinstanz nacheinander ausführen
- Innere Instanzen zusätzlich `asyncAfter="true"`: Aufruf und Ergebnis sind festgeschrieben, bevor die Instanz abschließt. Beenden mehrere Instanzen gleichzeitig, kollidiert nur dieser kleine Abschluss-Job (`OptimisticLockingException`) und wird von der Engine wiederholt; das Modell wird nie ein zweites Mal aufgerufen (ohne `asyncAfter` rollte die Kollision den ganzen Recherche-Job zurück und der Aufruf wurde doppelt bezahlt)
- Multi-Instance-Body (`serviceTask`): `asyncAfter="true"`, damit das Zusammenführen in einer eigenen Transaktion läuft
- Die Kollision beim Abschluss ist erwartetes Verhalten und getestet (`ParallelResearchProcessTest`)
- Die Instanzen schreiben ihre Ergebnisse in eigene Tabellenzeilen, NIE in gemeinsame Prozessvariablen (kein Zähler, keine Liste, die parallel verändert wird)
- Das Ergebnis der Recherche (`researchOutcome`) wird nach dem Join aus der Datenbank ermittelt
- Pro Anbieter begrenzt eine faire Semaphore die gleichzeitigen Aufrufe (`ProviderCallLimiter`: `ai_provider.max_concurrent_calls`, sonst Default 2); jeder Aufruf belegt einen Platz, auch Reparaturversuch und Aggregation. Die Job-Executor-Threads müssen dazu passen (Defaults)
- Wartezeit auf einen freien Platz höchstens `max-wait-for-call-slot` (5 min); danach wird der Job wiederholt (`R3/PT5M`), die Recherche ist nicht `FAILED`, weil das Modell nicht versagt hat. Beim Start wird geprüft: Wartezeit + Timeout × (1 + Reparaturversuche) < Sperrzeit des Jobs (20 min; Defaults: 5 min + 2 × 300 s = 15 min); sonst könnte die Engine einen noch laufenden Job einem zweiten Thread geben und das Modell doppelt aufrufen
- Findet ein Reparaturversuch keinen Platz, scheitert nur dieser Versuch wie ein Anbieterfehler; der bereits bezahlte erste Aufruf bleibt gebucht

## Budgetwarnung
Ein End-Listener am Prozess (`budgetWarningListener`, `camunda:executionListener event="end"`) prüft nach jedem Ende eines Laufs, ob die Ausgaben des Monats die Warnschwelle erreicht haben, und schickt dann einmal pro Monat eine Mail an den Betreiber (`BudgetWarningService`, Tabelle `budget_warning`). Er wirft nie: Scheitert die Mail, wird das protokolliert und nach dem nächsten Lauf erneut versucht; der Status des Laufs bleibt unberührt. Ein Listener statt eines Tasks, weil er an allen vier Enden gilt, ohne den Ablauf zu verzweigen.

## Dateien
- Modell: `src/main/resources/processes/digest-run.bpmn`, Process-ID `digestRun`; wird beim Start automatisch deployt (`camunda.bpm.deployment-resource-pattern: classpath*:processes/*.bpmn`), eine neue Prozessversion entsteht nur bei geändertem Modell
- Delegates im Package `process`, als Spring-Beans über `camunda:delegateExpression="${...}"`

## Umsetzung (Stand Roadmap-Schritt 8)
| Task | Bean | Asynchron | Stand |
|---|---|---|---|
| Lauf vorbereiten | `prepareRunDelegate` | nein, läuft in der Transaktion von `DigestProcessStarter` | fertig |
| Budget prüfen | `checkBudgetDelegate` | nein | fertig (Monatskosten aus View `monthly_cost` gegen aktives Budget bzw. Default) |
| Recherche | `researchDelegate` | `asyncBefore`, nicht exklusiv; Multi-Instance-Body `asyncAfter` | fertig (ein Modell; Semaphore pro Anbieter folgt in Schritt 13) |
| Meldungen prüfen | `checkNewsDelegate` | `asyncBefore` | fertig für Zeitraum und Duplikate im Lauf (`NewsItemChecker`), setzt `researchOutcome`; Duplikate früherer Läufe und URL-Prüfung folgen in Schritt 14 |
| Aggregieren | `aggregateDelegate` | `asyncBefore` | fertig (`RunAggregationService`); scheitert das Aggregator-Modell, wirft der Delegate den BPMN-Fehler `AGGREGATION_FAILED` |
| HTML und PDF erzeugen | `renderDigestDelegate` | `asyncBefore` | fertig (`RenderDigestService`, einfaches Layout bis Schritt 17); ohne aktive Empfänger BPMN-Fehler `NO_RECIPIENTS` vor dem Rendern; PDF-Ablage bis Schritt 11 im lokalen Verzeichnis (`newsdigest.storage.local-directory`), danach S3 |
| Mails versenden | `sendMailsDelegate` | `asyncBefore` | fertig (`DigestMailService`), setzt `mailOutcome` |
| Betreiber informieren | `notifyOperatorDelegate` (Grund als Input-Parameter `notificationReason`: `BUDGET_EXHAUSTED`, `NO_MODEL_SUCCEEDED`, `AGGREGATION_FAILED`, `NO_RECIPIENTS`, `MAIL_PARTIALLY_FAILED`) | `asyncBefore` | fertig (`OperatorNotificationService`, Klartext-Mail an `NEWSDIGEST_MAIL_OPERATOR`); scheitert die Mail, Retries und danach Incident, ohne Auswirkung auf den Status des Laufs |
| Lauf abschließen | `finishRunDelegate` (Status als Input-Parameter `runStatus`, je Ende ein Task) | nein | fertig |

Seit Schritt 8 gibt es keine Platzhalter mehr (`PendingStepDelegates` ist entfernt).

Hinweis: Die Recherche läuft innerhalb der Job-Transaktion der Engine und hält dabei eine DB-Verbindung, solange der Modellaufruf dauert. Bei mehreren parallelen Modellen (Schritt 13) Verbindungs-Pool und Job-Executor-Threads aufeinander abstimmen.

## Prozessvariablen
| Variable | Typ | Gesetzt von |
|---|---|---|
| `topicId` | Long | Starter |
| `runId` | Long | Lauf vorbereiten |
| `periodFrom`, `periodTo` | String `YYYY-MM-DD` (Datum in der Zeitzone des Zeitplans) | Lauf vorbereiten |
| `models` | String mit kommagetrennten Modell-IDs, z. B. `3,17`; die Multi-Instance liest sie über `${digestRunVariables.modelIds(models)}` | Lauf vorbereiten |
| `model` | Long (Element der Multi-Instance, lokal) | Multi-Instance |
| `aggregatorModelId` | Long | Lauf vorbereiten |
| `budgetOk` | Boolean | Budget prüfen |
| `researchOutcome` | String: `NONE_SUCCEEDED`, `NO_VALID_NEWS`, `VALID_NEWS` | Meldungen prüfen (nach dem Join) |
| `mailOutcome` | String: `ALL_SENT`, `PARTIALLY_SENT` | Mails versenden |

Große Inhalte stehen nie in Prozessvariablen, sondern in Tabellen bzw. S3.

Alle Variablen sind einfache Werte oder Strings, damit die Engine keine Java-Serialisierung braucht (diese ist aus Sicherheitsgründen abgeschaltet). Namen und Kodierung stehen in `DigestRunVariables`.

## Zeitraum eines Laufs
- Ende: heute in der Zeitzone des Zeitplans
- Anfang: der Tag nach `last_successful_run_at`; beim ersten Lauf heute minus `initial_lookback_days` (Default 7)
- Lag der letzte erfolgreiche Lauf schon heute, umfasst der Zeitraum nur heute
- Umsetzung: `RunPeriodCalculator`

## Fehlerverhalten
- Externe Aufrufe (KI, URL-Prüfung, S3, Mail) mit `asyncBefore` und Retries (siehe `12-defaults.md`); nach Ausschöpfen der Retries entsteht ein Incident und ein Alarm (siehe `11-deployment-cicd.md`)
- Bei der Recherche: Fehler des Modells werden im Delegate gefangen und als `FAILED` gespeichert; Retries nur für technische Fehler vor dem eigentlichen Aufruf
- Bei der Aggregation: Fehler des Modells, Ablehnung oder auch nach dem Reparaturversuch ungültiges JSON -> Kosten und Grund werden gespeichert, BPMN-Fehler `AGGREGATION_FAILED` -> Boundary-Event -> Betreiber informieren -> `FAILED`, `last_successful_run_at` unverändert. Keine Job-Retries, weil sie nur denselben bezahlten Fehlversuch wiederholen würden; technische Fehler außerhalb des Modellaufrufs (z. B. Datenbank) laufen weiter über Retries
- Kein Modell erfolgreich -> `FAILED`, Fehler-Mail an den Betreiber, `last_successful_run_at` bleibt unverändert
- Keine gültige Meldung -> `NO_NEWS`, keine Mail an Empfänger, `last_successful_run_at` wird gesetzt
- Mailversand: Retries senden nur an Empfänger, deren `mail_delivery` noch nicht `SENT` ist; scheitert ein Empfänger endgültig, wird er als `FAILED` protokolliert, der Lauf gilt als `COMPLETED`, wenn mindestens ein Empfänger die Mail erhalten hat, und der Betreiber wird informiert; erreicht keiner die Mail, entsteht ein Incident
- Umsetzung Mailversand: Jede Zustellung wird direkt nach dem Versuch in eigener Transaktion gespeichert (`SENT` mit Zeitpunkt oder `PENDING` mit Fehlertext). Scheitert ein Empfänger und hat der Job noch Versuche, wirft der Delegate eine Exception (Job-Retry). Im letzten Versuch (Job hat nur noch 1 Retry) werden offene Empfänger `FAILED`; bei mindestens einem `SENT` geht es über das Gateway "Alle zugestellt?" weiter, sonst Exception und Incident. Das setzt einen Retry-Zyklus mit mindestens zwei Wiederholungen voraus (Default `R3/PT5M`)
- Jede Digest-Mail geht an genau einen Empfänger, damit sich Empfänger nicht gegenseitig sehen
- Ein Topic ohne aktive Empfänger endet vor dem Rendern über den BPMN-Fehler `NO_RECIPIENTS` als `FAILED` mit Betreiber-Mail
- Fehler in Delegates laufen über die BPMN-Fehlerbehandlung, nicht über den `@RestControllerAdvice`

## Status eines Laufs
| Status | Bedeutung | `last_successful_run_at` |
|---|---|---|
| `RUNNING` | Prozess läuft | unverändert |
| `COMPLETED` | Mail an mindestens einen Empfänger verschickt | auf `period_to` gesetzt |
| `NO_NEWS` | Modelle haben geliefert, aber keine gültige Meldung | auf `period_to` gesetzt |
| `FAILED` | Kein Modell erfolgreich oder Incident nicht behebbar | unverändert |
| `SKIPPED_BUDGET` | Monatsbudget erreicht, keine KI-Aufrufe | unverändert |
| `CANCELLED` | Von einem Admin oder nach Überschreiten der Höchstdauer abgebrochen | unverändert |

## Idempotenz
Jeder asynchrone Schritt kann von der Engine wiederholt werden (Retry, abgelaufene Job-Sperre nach Neustart). Deshalb:
- Recherche: vorhandenes `provider_result` für (Lauf, Modell) wird überschrieben, nicht doppelt angelegt (UNIQUE-Constraint); die Meldungen werden ersetzt, Token, Websuchen, Kosten und Dauer des früheren Versuchs werden zu den neuen addiert. Das Ergebnis wird direkt nach dem Modellaufruf in einer eigenen Transaktion gespeichert, damit ein bezahlter Aufruf auch dann erfasst bleibt, wenn der Job danach scheitert (`RunResearchService`)
- Meldungen prüfen: beginnt jedes Mal von vorn und kommt zum selben Ergebnis
- Aggregieren: Inhalt (`aggregate_json`) wird ersetzt, Token und Kosten werden in `aggregation_result` addiert (`RunAggregationService`)
- Rendern, S3-Upload: Ergebnis wird ersetzt; S3-Schlüssel ist deterministisch (`runs/<runId>/digest.pdf`, steht in `digest_run.pdf_s3_key`; bis Schritt 11 derselbe Schlüssel im lokalen Verzeichnis)
- Mail-Inhalt: HTML und Klartext werden beim Versand aus `aggregate_json` neu erzeugt; das Erstellungsdatum ist der Start des Laufs, deshalb ist der Inhalt bei jedem Versuch gleich
- Mail: bereits `SENT` wird nie erneut verschickt
- Kosten werden pro Aufruf gespeichert; ein wiederholter Aufruf ersetzt die Kosten nicht, sondern wird zusätzlich erfasst, weil er tatsächlich bezahlt wurde

## Job-Executor
- Sperrzeit eines Jobs (`lockTimeInMillis`) muss länger sein als der längste Task (Recherche inkl. Reparaturversuch); Default siehe `12-defaults.md`
- Beim Herunterfahren (Deployment) wartet die App kontrolliert auf laufende Jobs (`server.shutdown=graceful`, ausreichende Stop-Zeit im Container); nicht beendete Jobs übernimmt die Engine nach Ablauf der Sperre beim nächsten Start

## Abbrechen hängender Läufe
- Ein Admin kann einen Lauf mit Status `RUNNING` abbrechen (API und UI ab Schritt 19/23, bis dahin `POST /dev/runs/{id}/cancel` im Profil `dev`); `RunCancellationService` beendet die Prozessinstanz (offene Jobs und Incidents verschwinden mit ihr, Custom-Listener wie die Budgetwarnung laufen nicht) und setzt den Lauf auf `CANCELLED` mit Grund in `error_message`
- Die Zeile des Laufs wird dabei gesperrt (`SELECT … FOR UPDATE`), ebenso beim regulären Beenden (`DigestRunService.finish`); ein abgebrochener Lauf bleibt `CANCELLED`. Ein Job, der beim Abbruch noch läuft (z. B. ein Modellaufruf), scheitert beim Commit, weil seine Prozessinstanz fehlt; der bezahlte Aufruf bleibt in `provider_result` erfasst
- Automatisch: Läuft ein Lauf länger als `newsdigest.run.max-duration` (3 h), bricht ihn der Wartungsjob ab und schickt dem Betreiber eine Mail (`RUN_TIMED_OUT`). Grund: Die Instanz läuft nur zum Lauf, ein hängender Lauf (meist ein Incident) fiele sonst niemandem auf und blockierte jeden weiteren Lauf des Topics
- Danach kann für das Topic wieder ein Lauf starten (Teilindex gilt nur für `RUNNING`)
- Fällt ein geplanter Start aus, weil noch ein Lauf aktiv ist, wird das geloggt und als Metrik `digest.run.skipped` (Tags `topic`, `reason`) gezählt

## Zeitplan
- `DigestScheduler` (Package `scheduling`) liest die aktiven Topics mit Cron-Ausdruck (Spring-Format mit Sekunden) und Zeitzone ihres `schedule`, alle `newsdigest.run.config-reload-interval` (60 s) neu; geänderte Zeitpläne wirken ohne Neustart, deaktivierte Topics fallen heraus
- Ungültiger Cron-Ausdruck oder unbekannte Zeitzone: Fehler im Log, nur dieses Topic startet nicht nach Plan
- Nachholen (ADR-019): Beim Start der App wird für jedes Topic der letzte geplante Zeitpunkt innerhalb von `newsdigest.scheduler.misfire-grace` (6 h) gesucht; ist seitdem kein Lauf gestartet, startet er einmal. Ein zweiter Neustart startet ihn nicht erneut
- Budget, Zeitraum und alles Weitere prüft der Prozess wie bei einem manuellen Start
- Nur aktiv mit `newsdigest.scheduler.enabled=true` (Profil `prod`); lokal, im Eval und in Tests startet nichts von selbst

## Start und Parallelität zwischen Läufen
- Prozessstart ausschließlich über `DigestProcessStarter` (Scheduler und manueller Lauf); Business Key ist die Topic-ID
- Pro Topic höchstens ein aktiver Lauf (Prüfung im Starter + Teilindex in `digest_run`); weil „Lauf vorbereiten“ synchron in der Start-Transaktion läuft, scheitert ein gleichzeitiger zweiter Start direkt am Teilindex und wird als `RunAlreadyActiveException` gemeldet
- `process_instance_id` wird in `digest_run` gespeichert

## Weitere Regeln
- `historyTimeToLive` setzen, Wert siehe Defaults
- BPMN mit einem externen Modeler bearbeitbar halten; keine Skripte oder Fachlogik im XML
