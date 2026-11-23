# ADR-011: Terraform für die AWS-Infrastruktur

- **Status:** Angenommen
- **Datum:** 2026-11-23

## Kontext
Die AWS-Infrastruktur (EC2, Netzwerk, IAM, S3, Secrets, SES, DNS, Alarme) soll reproduzierbar und versioniert sein.

## Entscheidung
Terraform mit Remote State in S3; Pläne laufen automatisch bei Pull Requests, Änderungen werden nur manuell mit Freigabe angewendet. Secret-Werte werden nicht über Terraform gesetzt.

## Alternativen
- AWS CDK in Java: gleiche Sprache wie das Backend, aber an AWS gebunden
- Manuell in der Konsole: schnell, aber nicht reproduzierbar

## Konsequenzen
- Infrastruktur entsteht im selben Schritt wie das erste Deployment (Roadmap Schritt 11)
- Einmaliger manueller Bootstrap des State-Buckets
- Eigene GitHub-OIDC-Rolle für Terraform mit getrennten Rechten für Plan (lesen) und Apply
