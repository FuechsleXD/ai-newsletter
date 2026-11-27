# Offene Entscheidungen

Claude baut einen betroffenen Bereich erst, wenn der Punkt entschieden ist, und fragt sonst nach. Nach der Entscheidung: Punkt hier entfernen, ADR anlegen, betroffene Doku anpassen.

| # | Thema | Optionen / Vorschlag | Betrifft | Benötigt ab Schritt |
|---|---|---|---|---|
| 4 | Zweiter Anbieter (Schritt 13b) | Microsoft Copilot nur über die Microsoft 365 Copilot Chat API (Preview, M365-Copilot-Lizenz, Entra-Konto, nur delegierte Benutzerrechte), für einen zeitgesteuerten Backend-Prozess eher ungeeignet; ein Copilot-Abo enthält keinen API-Zugang. Alternativen mit Abrechnung nach Verbrauch: OpenAI API (am einfachsten mit Spring AI), Azure OpenAI, Google Gemini API (mit Grounding) | Provider | später |
| 5 | `cibseven-keycloak` mit CIB seven 3.0 | Neueste Version ist 2.1.0 (für CIB seven 2.x). Vor Schritt 25 prüfen, ob es eine Version für CIB seven 3.0 gibt oder ob 2.1.0 funktioniert; sonst SSO der Engine-UI anders lösen (ADR-010 anpassen) | Engine-UI, Security | 25 |
| 7 | Kosten abgebrochener Aufrufe | Seit Schritt 15 wird ein abgebrochener Aufruf geschätzt (Prompt-Token plus höchstmögliche Websuchen, `cost_estimated`, ADR-014 Nachtrag 2); offen ist nur der unbekannte Anteil der Suchinhalte. Optionen: Streaming (Token-Zahlen kommen schrittweise), Abgleich mit der Usage-/Cost-API des Anbieters (Admin-Key nötig) | Kosten, Provider | später |

Außerdem offen, aber kein Entscheidungspunkt: UI-Design in `08-ui-design.md` ausfüllen (vor Roadmap-Schritt 21).
