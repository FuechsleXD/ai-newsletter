# 01 – Fachkonzept

## Ziel
Die App fragt mehrere KI-Modelle, für die ich Zugänge besitze, in einem einstellbaren Rhythmus nach aktuellen Trends und News zu frei definierbaren Themen (Topics). Alle Recherche-Modelle eines Topics arbeiten parallel. Jedes liefert die Meldungen seit dem letzten erfolgreichen Lauf. Die App prüft und bereinigt die Meldungen (Zeitraum, Erreichbarkeit der Quellen, Wiederholungen). Ein besonders leistungsfähiges Modell (der Aggregator) verdichtet alles. Daraus entstehen eine HTML-Mail und eine einseitige PDF-Infografik, die an die Empfänger des Topics gehen.

Lernziel: BPM (CIB seven) sinnvoll mit KI-Aufrufen verknüpfen.

## Begriffe
| Begriff | Bedeutung |
|---|---|
| Topic | Ein Thema, z. B. "KI in der Softwareentwicklung", "Java", "Spring Boot" |
| Anbieter (Provider) | KI-Hersteller mit eigener API, z. B. Anthropic, OpenAI, Google Gemini |
| Modell | Ein konkretes LLM eines Anbieters (exakte Modell-ID des Anbieters) |
| Recherche-Modell | Modell mit Web-Suche, das Meldungen zu einem Topic sammelt |
| Aggregator-Modell | Modell mit strukturierter Ausgabe, das alle geprüften Meldungen verdichtet |
| Meldung (News Item) | Einzelne Nachricht mit Überschrift, Kurztext, Quellen-URL und Veröffentlichungsdatum |
| Zeitplan | Cron-Ausdruck + Zeitzone, wiederverwendbar für mehrere Topics |
| Lauf (Run) | Eine Ausführung des Prozesses für ein Topic |
| Empfänger | E-Mail-Adresse, die das Ergebnis eines Topics erhält |
| Budget | Monatliche Kostengrenze für alle KI-Aufrufe |

## Funktionen
- Topics, Zeitpläne, Anbieter, Modelle (inkl. Preise) und Empfänger werden in der Datenbank gepflegt, über REST-API und React-UI
- Pro Topic: ein Zeitplan, eine Menge von Recherche-Modellen (auch mehrere desselben Anbieters), ein Aggregator-Modell, eine Liste von Empfängern
- Neue Modelle eines bereits angebundenen Anbieters ohne Code-Änderung anlegbar
- Konfigurationsänderungen wirken ohne Neustart
- Läufe automatisch nach Zeitplan oder manuell im UI; hängende Läufe kann ein Admin abbrechen
- Lauf-Historie: Meldungen je Modell mit Prüfstatus, Token, Kosten, Aggregat, PDF, Mail-Status je Empfänger, Prompt-Version
- Kostenübersicht pro Lauf, Topic und Monat; Monatsbudget stoppt neue Läufe
- Anmeldung mit Rollen; die Oberfläche der Process Engine ist aus dem UI erreichbar
- Metriken und Alarm bei Fehlern
- Ein aktives Topic braucht mindestens ein Recherche-Modell und mindestens einen aktiven Empfänger

## Ablauf eines Laufs
1. Lauf anlegen und Budget prüfen: ist das Monatsbudget erreicht, wird der Lauf mit `SKIPPED_BUDGET` beendet und der Betreiber informiert
2. Zeitraum bestimmen: vom letzten erfolgreichen Lauf bis jetzt (beim ersten Lauf Standard-Rückblick)
3. Alle Recherche-Modelle parallel mit demselben Prompt befragen; jedes liefert Meldungen als JSON (Überschrift, Kurztext, URL, Veröffentlichungsdatum)
4. Fällt ein Modell aus, wird das protokolliert; die übrigen laufen weiter
5. Erst wenn alle Recherchen fertig sind (erfolgreich oder fehlgeschlagen), geht es weiter
6. Meldungen prüfen: außerhalb des Zeitraums, Quelle nicht erreichbar, bereits in früheren Läufen gemeldet, doppelt innerhalb des Laufs -> aussortiert
7. Kein Modell hat geliefert: Lauf `FAILED`, Fehler-Mail an den Betreiber, Zeitpunkt des letzten erfolgreichen Laufs bleibt unverändert (der Zeitraum wird beim nächsten Lauf nachgeholt). Modelle haben geliefert, aber keine gültige Meldung: Lauf `NO_NEWS`, keine Mail an die Empfänger, Zeitpunkt wird gesetzt (es wurde nichts verpasst)
8. Aggregator verdichtet die gültigen Meldungen zu strukturiertem JSON; er bekommt die Überschriften des letzten Laufs mit dem Auftrag, nichts zu wiederholen
9. HTML-Mail und PDF aus dem JSON erzeugen, PDF in S3 ablegen
10. Mail (HTML + PDF-Anhang) an alle aktiven Empfänger senden, Versand pro Empfänger protokollieren
11. Erst nach erfolgreichem Versand den Zeitpunkt des letzten erfolgreichen Laufs setzen

## Nicht im Scope
- Keine öffentliche API für Dritte
- Keine Mandantenfähigkeit; Benutzer liegen im Identity Provider
- Kein eigener Web-Crawler; Aktualität kommt über die Web-Suche der Anbieter (die URL-Prüfung ruft nur die gelieferten Quellen auf)
- Kein BPMN-Editor im UI
- Kein Load Balancer
