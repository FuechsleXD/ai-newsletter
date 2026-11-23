# Offene Entscheidungen

Claude baut einen betroffenen Bereich erst, wenn der Punkt entschieden ist, und fragt sonst nach. Nach der Entscheidung: Punkt hier entfernen, ADR anlegen, betroffene Doku anpassen.

| # | Thema | Optionen / Vorschlag | Betrifft | Benötigt ab Schritt |
|---|---|---|---|---|
| 1 | Konkrete Claude-Modelle und Preise | Modell-IDs für Recherche und Aggregator in der Anthropic-Doku prüfen (Web-Suche unterstützt?), Preise selbst eintragen | Provider, Kosten | 5 |
| 2 | Domainname | Neue Domain wird registriert (entschieden); Name und Registrar noch offen. Empfehlung: Route 53, weil dort auch DNS für Elastic IP und SES-Verifizierung (DKIM, SPF, DMARC) liegt | Deployment | 11 |
| 3 | SES-Sandbox | Neue SES-Konten dürfen nur an verifizierte Adressen senden: Empfänger verifizieren oder Produktionszugang bei AWS beantragen | Deployment | 11 |
| 4 | Microsoft Copilot als zweiter Anbieter | Nur über die Microsoft 365 Copilot Chat API (Preview, M365-Copilot-Lizenz, Entra-Konto, nur delegierte Benutzerrechte); Eignung für einen zeitgesteuerten Backend-Prozess prüfen | Provider | später |

Außerdem offen, aber kein Entscheidungspunkt: UI-Design in `08-ui-design.md` ausfüllen (vor Roadmap-Schritt 21).
