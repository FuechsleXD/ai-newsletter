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
                     -> [HTML + PDF erzeugen, PDF in S3]
                     -> [Mails versenden]
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
- Recherche-Task mit `asyncBefore="true"` und `camunda:exclusive="false"`; exklusive Jobs würden die Instanzen derselben Prozessinstanz nacheinander ausführen
- Multi-Instance-Body mit `asyncAfter="true"`, damit das Zusammenführen in einer eigenen Transaktion läuft
- Gleichzeitiges Beenden mehrerer Instanzen führt zu `OptimisticLockingException` beim Join; die Engine wiederholt den Job automatisch. Das ist erwartetes Verhalten und muss getestet werden
- Die Instanzen schreiben ihre Ergebnisse in eigene Tabellenzeilen, NIE in gemeinsame Prozessvariablen (kein Zähler, keine Liste, die parallel verändert wird)
- Das Ergebnis der Recherche (`researchOutcome`) wird nach dem Join aus der Datenbank ermittelt
- Pro Anbieter begrenzt eine Semaphore die gleichzeitigen Aufrufe (Default siehe Defaults); die Job-Executor-Threads müssen dazu passend konfiguriert sein

## Dateien
- Modell: `src/main/resources/processes/digest-run.bpmn`, Process-ID `digestRun`
- Delegates im Package `process`, als Spring-Beans über `camunda:delegateExpression="${...}"`

## Prozessvariablen
| Variable | Typ | Gesetzt von |
|---|---|---|
| `topicId` | Long | Starter |
| `runId` | Long | Lauf vorbereiten |
| `periodFrom`, `periodTo` | Datum | Lauf vorbereiten |
| `models` | Liste von Modell-IDs (Long) | Lauf vorbereiten |
| `model` | Long (Element der Multi-Instance, lokal) | Multi-Instance |
| `aggregatorModelId` | Long | Lauf vorbereiten |
| `budgetOk` | Boolean | Budget prüfen |
| `researchOutcome` | String: `NONE_SUCCEEDED`, `NO_VALID_NEWS`, `VALID_NEWS` | Meldungen prüfen (nach dem Join) |

Große Inhalte stehen nie in Prozessvariablen, sondern in Tabellen bzw. S3.

## Fehlerverhalten
- Externe Aufrufe (KI, URL-Prüfung, S3, Mail) mit `asyncBefore` und Retries (siehe `12-defaults.md`); nach Ausschöpfen der Retries entsteht ein Incident und ein Alarm (siehe `11-deployment-cicd.md`)
- Bei der Recherche: Fehler des Modells werden im Delegate gefangen und als `FAILED` gespeichert; Retries nur für technische Fehler vor dem eigentlichen Aufruf
- Kein Modell erfolgreich -> `FAILED`, Fehler-Mail an den Betreiber, `last_successful_run_at` bleibt unverändert
- Keine gültige Meldung -> `NO_NEWS`, keine Mail an Empfänger, `last_successful_run_at` wird gesetzt
- Mailversand: Retries senden nur an Empfänger, deren `mail_delivery` noch nicht `SENT` ist; scheitert ein Empfänger endgültig, wird er als `FAILED` protokolliert, der Lauf gilt als `COMPLETED`, wenn mindestens ein Empfänger die Mail erhalten hat, und der Betreiber wird informiert; erreicht keiner die Mail, entsteht ein Incident
- Fehler in Delegates laufen über die BPMN-Fehlerbehandlung, nicht über den `@RestControllerAdvice`

## Status eines Laufs
| Status | Bedeutung | `last_successful_run_at` |
|---|---|---|
| `RUNNING` | Prozess läuft | unverändert |
| `COMPLETED` | Mail an mindestens einen Empfänger verschickt | auf `period_to` gesetzt |
| `NO_NEWS` | Modelle haben geliefert, aber keine gültige Meldung | auf `period_to` gesetzt |
| `FAILED` | Kein Modell erfolgreich oder Incident nicht behebbar | unverändert |
| `SKIPPED_BUDGET` | Monatsbudget erreicht, keine KI-Aufrufe | unverändert |
| `CANCELLED` | Von einem Admin abgebrochen | unverändert |

## Idempotenz
Jeder asynchrone Schritt kann von der Engine wiederholt werden (Retry, abgelaufene Job-Sperre nach Neustart). Deshalb:
- Recherche: vorhandenes `provider_result` für (Lauf, Modell) wird überschrieben, nicht doppelt angelegt (UNIQUE-Constraint)
- Aggregieren, Rendern, S3-Upload: Ergebnis wird ersetzt; S3-Schlüssel ist deterministisch (`runs/<runId>/digest.pdf`)
- Mail: bereits `SENT` wird nie erneut verschickt
- Kosten werden pro Aufruf gespeichert; ein wiederholter Aufruf ersetzt die Kosten nicht, sondern wird zusätzlich erfasst, weil er tatsächlich bezahlt wurde

## Job-Executor
- Sperrzeit eines Jobs (`lockTimeInMillis`) muss länger sein als der längste Task (Recherche inkl. Reparaturversuch); Default siehe `12-defaults.md`
- Beim Herunterfahren (Deployment) wartet die App kontrolliert auf laufende Jobs (`server.shutdown=graceful`, ausreichende Stop-Zeit im Container); nicht beendete Jobs übernimmt die Engine nach Ablauf der Sperre beim nächsten Start

## Abbrechen hängender Läufe
- Ein Admin kann einen Lauf mit Status `RUNNING` abbrechen (API und UI); dabei wird die Prozessinstanz beendet und der Lauf auf `CANCELLED` gesetzt
- Danach kann für das Topic wieder ein Lauf starten (Teilindex gilt nur für `RUNNING`)
- Fällt ein geplanter Start aus, weil noch ein Lauf aktiv ist, wird das geloggt und als Metrik gezählt

## Start und Parallelität zwischen Läufen
- Prozessstart ausschließlich über `DigestProcessStarter` (Scheduler und manueller Lauf)
- Pro Topic höchstens ein aktiver Lauf (Prüfung im Starter + Teilindex in `digest_run`)
- `process_instance_id` wird in `digest_run` gespeichert

## Weitere Regeln
- `historyTimeToLive` setzen, Wert siehe Defaults
- BPMN mit einem externen Modeler bearbeitbar halten; keine Skripte oder Fachlogik im XML
