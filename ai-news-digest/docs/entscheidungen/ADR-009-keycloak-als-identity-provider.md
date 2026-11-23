# ADR-009: Keycloak als Identity Provider, JWT-Prüfung im Backend

- **Status:** Angenommen
- **Datum:** 2026-11-23

## Kontext
UI und API brauchen eine Anmeldung mit den Rollen `ADMIN` und `VIEWER`. Das Backend soll zustandslos per JWT abgesichert sein.

## Entscheidung
Keycloak läuft als eigener Container auf der EC2 und stellt per OIDC die Tokens aus. Das Backend ist ein OAuth2 Resource Server und prüft die JWTs; die Rollen kommen aus den Keycloak-Realm-Rollen. Das React-UI meldet sich per Authorization Code Flow mit PKCE an.

## Alternativen
- Amazon Cognito: verwaltet, kein zusätzlicher Container, aber weniger Kontrolle und weniger Lerneffekt
- Microsoft Entra ID: sinnvoll nur bei vorhandenem Microsoft-Tenant
- Eigene Benutzerverwaltung mit selbst ausgestellten JWTs: mehr Code und Sicherheitsrisiko (Passwörter, Schlüssel, Rotation)

## Konsequenzen
- Zusätzlicher Container mit eigener Datenbank, Updates und Backups liegen bei mir
- Höherer Speicherbedarf auf der EC2
- Realm-Konfiguration ist als Datei versioniert
- Keycloak kann später auch die Anmeldung an der Engine-UI übernehmen (umgesetzt in ADR-010)
