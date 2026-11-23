# ADR-010: Engine-UI mit Keycloak-Single-Sign-On in neuem Tab

- **Status:** Angenommen
- **Datum:** 2026-11-23

## Kontext
Die Oberfläche von CIB seven soll aus dem React-UI erreichbar sein, ohne zweiten Login. Die Anmeldung läuft über Keycloak (ADR-009).

## Entscheidung
- Menüpunkt "Prozesse" öffnet `/bpm` in einem neuen Tab
- Die Engine-Webapp nutzt OAuth2 Login gegen denselben Keycloak-Realm; dank bestehender Keycloak-Sitzung erscheint keine zweite Anmeldung
- Das Community-Plugin `cibseven-keycloak` (laut Projektseite getestet mit CIB seven 2.2.0) stellt Benutzer und Gruppen aus Keycloak in der Engine bereit
- Zugriff nur für die Gruppe `ADMIN`

## Alternativen
- iframe-Einbettung: wirkt integrierter, erfordert aber gelockerte Frame- und CSP-Header und ist fehleranfällig bei Sitzung und Abmeldung
- Engine-eigene Benutzer: zweiter Login, doppelte Benutzerpflege

## Konsequenzen
- Zwei Security-Filterketten im Backend: zustandslos mit JWT für `/api/**`, Cookie-Session mit CSRF für `/bpm/**`
- Abhängigkeit von einer Community-Erweiterung; bei Versionswechseln von CIB seven die Kompatibilität prüfen
