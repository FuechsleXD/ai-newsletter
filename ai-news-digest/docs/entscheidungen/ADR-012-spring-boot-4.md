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

## Konsequenzen
- In Roadmap-Schritt 1 wird die Kompatibilität (Spring Boot, CIB seven, `cibseven-keycloak`, Spring AI, springdoc) geprüft und die Versionen werden in der POM festgeschrieben
- Beispiele und Doku zu Spring Boot 3 im Netz passen nicht immer (modulare Starter, Jackson 3)
