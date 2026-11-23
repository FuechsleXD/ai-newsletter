# Offene Entscheidungen

Claude baut einen betroffenen Bereich erst, wenn der Punkt entschieden ist, und fragt sonst nach. Nach der Entscheidung: Punkt hier entfernen, ADR anlegen, betroffene Doku anpassen.

| # | Thema | Optionen / Vorschlag | Betrifft | Benötigt ab Schritt |
|---|---|---|---|---|
| 2 | Domainname | Neue Domain wird registriert (entschieden); Name und Registrar noch offen. Empfehlung: Route 53, weil dort auch DNS für Elastic IP und SES-Verifizierung (DKIM, SPF, DMARC) liegt | Deployment | 11 |
| 3 | SES-Sandbox | Neue SES-Konten dürfen nur an verifizierte Adressen senden: Empfänger verifizieren oder Produktionszugang bei AWS beantragen | Deployment | 11 |
| 4 | Microsoft Copilot als zweiter Anbieter | Nur über die Microsoft 365 Copilot Chat API (Preview, M365-Copilot-Lizenz, Entra-Konto, nur delegierte Benutzerrechte); Eignung für einen zeitgesteuerten Backend-Prozess prüfen | Provider | später |
| 5 | `cibseven-keycloak` mit CIB seven 3.0 | Neueste Version ist 2.1.0 (für CIB seven 2.x). Vor Schritt 25 prüfen, ob es eine Version für CIB seven 3.0 gibt oder ob 2.1.0 funktioniert; sonst SSO der Engine-UI anders lösen (ADR-010 anpassen) | Engine-UI, Security | 25 |
| 6 | Kosten bei Antworten ohne Inhalt | Spring AI 2.0.3 gibt bei einer Anthropic-Antwort ohne Inhalt (z. B. Ablehnung) weder Stop-Grund noch Token-Zahlen zurück; die berechneten Input-Token fehlen dann in den Kosten. Optionen: `AnthropicNewsProvider` direkt auf dem offiziellen Anthropic-Java-SDK (kommt mit Spring AI ohnehin mit) oder auf eine Spring-AI-Version mit Korrektur warten | Provider, Kosten | 9 |

Außerdem offen, aber kein Entscheidungspunkt: UI-Design in `08-ui-design.md` ausfüllen (vor Roadmap-Schritt 21).
