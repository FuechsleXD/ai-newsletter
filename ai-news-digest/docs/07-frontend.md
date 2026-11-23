# 07 – Frontend (Technik und Seiten)

Look & Feel steht separat in `08-ui-design.md`.

## Technik
- Ordner `frontend/`, React + TypeScript (strict) + Vite, Node-Version in `.nvmrc`
- Routing: React Router; Server-State: TanStack Query; Formulare: React Hook Form + Zod
- UI: shadcn/ui (Komponenten werden per CLI ins Projekt kopiert und gehören zum eigenen Code) + Tailwind CSS; Tabellen mit TanStack Table, Icons mit lucide-react, Drag & Drop nur falls nötig nach Rückfrage
- Komponenten aus shadcn/ui liegen in `src/components/ui/` und werden nur dort angepasst
- API-Client und Typen aus OpenAPI generiert, keine handgeschriebenen DTO-Typen
- Design-Tokens aus `08-ui-design.md` als CSS-Variablen in `src/theme/tokens.css`, in der Tailwind-Konfiguration referenziert; keine festen Farbwerte oder beliebigen Tailwind-Werte (`bg-[#...]`) in Komponenten
- Ausgeliefert als statische Dateien über den Reverse Proxy, unter derselben Domain wie API und Engine-UI

## Lokale Entwicklung
- `npm run dev` startet Vite; Anfragen an `/api` und `/auth` leitet der Vite-Proxy an Backend bzw. lokalen Keycloak weiter, damit lokal dieselben Pfade gelten wie in Produktion
- Lokaler Keycloak-Realm enthält Testbenutzer mit den Rollen `ADMIN` und `VIEWER`

## Struktur
```
frontend/src/
  api/          generierter Client + Query-Hooks
  app/          Routing, Layout, Auth-Provider
  components/   wiederverwendbare Komponenten
  features/     topics, schedules, models, recipients, runs, costs, processes, dashboard
  theme/        tokens.css (CSS-Variablen), Tailwind-Anbindung
  test/         Test-Setup, MSW-Handler
```

## Seiten
| Seite | Inhalt | Rolle |
|---|---|---|
| Anmeldung | Weiterleitung zum Keycloak-Login (Authorization Code + PKCE), Abmelden | – |
| Dashboard | Nächste geplante Läufe, letzte Läufe mit Status, offene Fehler, Kosten des Monats mit Budget-Balken | VIEWER |
| Topics | Liste; Formular mit Zeitplan, Recherche-Modellen (Mehrfachauswahl), Aggregator-Modell (Dropdown), Empfängern, aktiv/inaktiv; geschätzte Kosten pro Lauf aus den letzten Läufen | VIEWER / ADMIN |
| Zeitpläne | Cron + Zeitzone, Vorschau der nächsten 5 Ausführungen | VIEWER / ADMIN |
| Anbieter und Modelle | Anbieter aktivieren, max. parallele Aufrufe; Modelle anlegen/bearbeiten (Modell-ID, Anzeigename, Fähigkeiten, Temperatur, Max-Tokens, Preise) | VIEWER / ADMIN |
| Empfänger | Liste, anlegen, deaktivieren | VIEWER / ADMIN |
| Kosten | Budget einstellen; Kosten nach Monat, Topic, Modell | VIEWER / ADMIN |
| Läufe | Liste mit Filtern; "Abbrechen" für hängende Läufe (ADMIN); Detail mit Ergebnis je Modell, Meldungen mit Prüfstatus (gültig, außerhalb Zeitraum, Duplikat, nicht erreichbar), Tokens, Kosten, Mail-Status, Prompt-Version, PDF- und Mail-Vorschau; "Jetzt ausführen" | VIEWER / ADMIN |
| Prozesse | Einstieg in die Engine-UI | ADMIN |

- Auswahllisten zeigen nur passende Modelle (Web-Suche bzw. strukturierte Ausgabe)
- Hinweis im UI, wenn das Budget die Warnschwelle erreicht hat
- Lade-, Fehler- und Leerzustände überall; Bestätigungsdialog bei Deaktivieren und manuellem Lauf
- Fehler aus Problem Details verständlich anzeigen, Feldfehler am Feld
- Aktionen, die die Rolle nicht erlaubt, werden ausgeblendet (die API prüft trotzdem)

## Engine-UI (CIB seven)
- Ziel: Prozessinstanzen, Incidents und Jobs aus dem UI erreichbar
- Webapps und Engine-REST kommen aus dem passenden CIB-seven-Starter; Artefakte und Pfade in der Doku prüfen, nicht raten
- Stufe 1: Menüpunkt "Prozesse" öffnet die Engine-Webapp unter `/bpm` in einem neuen Tab (kein iframe)
- Gemeinsame Anmeldung über Keycloak (siehe ADR-010): die Engine-Webapp nutzt Spring Security OAuth2 Login (Single Sign-On) gegen denselben Realm, das Plugin `cibseven-keycloak` liefert Benutzer und Gruppen aus Keycloak an die Engine
- Nur Benutzer mit Realm-Rolle `ADMIN` haben in der Engine Zugriff (Berechtigungen in der Engine aktiviert, Gruppe `ADMIN` erhält die nötigen Rechte)
- Stufe 2 (später): eigene React-Ansichten für Instanzen und Incidents der `digestRun`-Läufe, verknüpft über `process_instance_id`
- Nur für `ADMIN`
