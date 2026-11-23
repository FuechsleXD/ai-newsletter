# ADR-004: Parallele Recherche, Aggregator nach Abschluss aller Zweige

- **Status:** Angenommen
- **Datum:** 2026-11-22

## Kontext
Die Recherche-Modelle sollen gleichzeitig abgefragt werden. Der Aggregator darf erst starten, wenn alle Recherchen beendet sind. Die Anzahl der Modelle ist pro Topic variabel.

## Entscheidung
- Fork- und Join-Parallel-Gateway umschließen die Recherche und machen die Parallelität im Modell sichtbar
- Innerhalb erzeugt eine parallele Multi-Instance (`isSequential="false"`) über `models` einen Zweig pro Modell
- Der Aggregator folgt nach dem Join; er läuft erst, wenn alle Instanzen beendet sind (erfolgreich oder `FAILED`)
- Recherche-Jobs sind nicht exklusiv (`camunda:exclusive="false"`), der Multi-Instance-Body hat `asyncAfter`
- Ergebnisse werden in Tabellen geschrieben, nicht in gemeinsame Prozessvariablen
- Gleichzeitige Aufrufe pro Anbieter werden begrenzt

## Alternativen
- Statisches Parallel-Gateway mit einem Zweig pro Modell: nicht dynamisch, widerspricht ADR-002
- Sequenzielle Multi-Instance: einfacher, aber langsamer und didaktisch weniger ergiebig

## Konsequenzen
- `OptimisticLockingException` beim Zusammenführen ist erwartet und wird durch Job-Wiederholung aufgelöst; dafür gibt es einen Test
- Job-Executor-Threads müssen zur gewünschten Parallelität passen
- Rate-Limits der Anbieter werden durch die Begrenzung pro Anbieter berücksichtigt
