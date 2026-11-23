# ADR-012: Spring Boot 4 statt 3.5

- **Status:** Angenommen
- **Datum:** 2026-11-23

## Kontext
Ursprünglich war Spring Boot 3.5 vorgesehen. Die kostenlosen Sicherheitsupdates für Spring Boot 3.5 endeten am 30. Juni 2026. Spring AI 2.x setzt Spring Boot 4 voraus. CIB seven bietet ab 2.2 eigene Starter für Spring Boot 4 (Suffix `-4`).

## Entscheidung
- Spring Boot 4.x, bevorzugt 4.1; die konkrete Version richtet sich nach der neuesten Linie, die CIB seven und das Plugin `cibseven-keycloak` unterstützen
- Spring AI 2.x, Jackson 3
- Java 21 bleibt (Java 25 ist möglich, aber nicht nötig)

## Alternativen
- Spring Boot 3.5 mit kostenpflichtigem Support: nicht sinnvoll für ein neues privates Projekt
- Spring Boot 4.0: kostenloser Support endet laut Spring bereits im Dezember 2026

## Festgeschriebene Versionen (Roadmap-Schritt 1, geprüft am 2026-11-23 gegen Maven Central)
| Komponente | Version | Begründung |
|---|---|---|
| Spring Boot | 4.1.2, seit Dependabot-PR #3 4.1.3 | CIB seven 3.0.0 ist gegen 4.1.2 gebaut; die CIB-Kompatibilitätstabelle nennt für 3.0.x Spring Boot 4.1.x, Patch-Updates innerhalb 4.1.x sind erlaubt |
| CIB seven | 3.0.0 (veröffentlicht 2026-11-20) | Erste Linie für Spring Boot 4.1; ab 3.0 sind die Artefakte ohne Suffix die Spring-Boot-4-Variante, die Artefakte mit `-4` (z. B. `org.cibseven.bpm.springboot:cibseven-bpm-spring-boot-starter-4`) sind Relocations darauf. Eingebunden wird deshalb `cibseven-bpm-spring-boot-starter` ohne Suffix; die Konfiguration heißt weiterhin `camunda.bpm.*` |
| Jackson 3 | von Spring Boot verwaltet, muss 3.1.x bleiben | CIB seven 3.0 ist mit 3.1.7 getestet, 3.2 wird nicht unterstützt |
| Spring AI | 2.0.3 | 2.0.x unterstützt Spring Boot 4.0.x und 4.1.x |
| springdoc-openapi | 3.1.1 | 3.1.x ist die Linie für Spring Boot 4.1 (3.2.x setzt 4.2 voraus) |
| Gotenberg (Docker-Image) | `gotenberg/gotenberg:8.40.0-chromium` (Schritt 8) | Letzte Version der 8er-Linie (2026-11-05), Chromium-Variante ohne LibreOffice; die 9er-Linie erscheint seit 2026-11-05 fast täglich und wird später geprüft |
| jsoup, PDFBox | 1.24.1, 3.0.8 (nur Tests, Schritt 8) | Prüfung des Mail-HTML und der PDF-Seitenzahl |
| `cibseven-keycloak` | 2.1.0 (vorläufig) | Neueste Version unter `org.cibseven.bpm.extension`; ob sie mit CIB seven 3.0 läuft, ist offen (siehe `offene-punkte.md`), benötigt erst ab Schritt 25 |

Die Versionen stehen als Properties in der `pom.xml`; Bibliotheken, die erst später gebraucht werden, kommen in ihrem Roadmap-Schritt als Abhängigkeit dazu.

## Konsequenzen
- CIB seven 2.2 hätte nur Spring Boot 4.0.x unterstützt (kostenloser Support bis Dezember 2026); mit 3.0.0 ist 4.1 möglich. CIB seven 3.0.0 ist bei der Festlegung erst drei Tage alt
- Spring Boot nicht über die von CIB seven unterstützte Linie (4.1.x) hinaus aktualisieren; Dependabot-Updates von Spring Boot und Jackson entsprechend prüfen
- Beispiele und Doku zu Spring Boot 3 im Netz passen nicht immer (modulare Starter, Jackson 3)
