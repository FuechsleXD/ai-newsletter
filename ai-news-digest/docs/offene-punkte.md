# Offene Entscheidungen

Claude baut einen betroffenen Bereich erst, wenn der Punkt entschieden ist, und fragt sonst nach. Nach der Entscheidung: Punkt hier entfernen, ADR anlegen, betroffene Doku anpassen.

| # | Thema | Optionen / Vorschlag | Betrifft | Benötigt ab Schritt |
|---|---|---|---|---|
| 4 | Microsoft Copilot als zweiter Anbieter | Nur über die Microsoft 365 Copilot Chat API (Preview, M365-Copilot-Lizenz, Entra-Konto, nur delegierte Benutzerrechte); Eignung für einen zeitgesteuerten Backend-Prozess prüfen | Provider | später |
| 5 | `cibseven-keycloak` mit CIB seven 3.0 | Neueste Version ist 2.1.0 (für CIB seven 2.x). Vor Schritt 25 prüfen, ob es eine Version für CIB seven 3.0 gibt oder ob 2.1.0 funktioniert; sonst SSO der Engine-UI anders lösen (ADR-010 anpassen) | Engine-UI, Security | 25 |

Außerdem offen, aber kein Entscheidungspunkt: UI-Design in `08-ui-design.md` ausfüllen (vor Roadmap-Schritt 21).
