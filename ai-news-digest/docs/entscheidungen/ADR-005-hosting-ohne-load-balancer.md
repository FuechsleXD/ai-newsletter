# ADR-005: Hosting ohne Load Balancer

- **Status:** Angenommen (2026-11-23); Datenbank als Container auf derselben Instanz (Annahme, bis ich sie bestätige)
- **Datum:** 2026-11-22

## Kontext
Kein Load Balancer gewünscht, auch wegen der Fixkosten. Die App läuft mit genau einer Instanz, weil der Scheduler nur einmal laufen darf. HTTPS ist trotzdem Pflicht.

## Entscheidung (Vorschlag)
Eine EC2-Instanz mit Docker Compose. Caddy übernimmt HTTPS (Let's Encrypt), liefert das React-Frontend aus und leitet `/api` und `/bpm` an das Backend weiter. Deployment per AWS Systems Manager, kein offener SSH-Port.

## Alternativen
- ECS Fargate mit Load Balancer: komfortabel, aber höhere Fixkosten, vom Nutzer nicht gewünscht
- ECS Fargate mit öffentlicher IP ohne Load Balancer: wechselnde IP und TLS müsste in der App gelöst werden
- CloudFront vor der Instanz: möglich, zusätzliche Komplexität

## Konsequenzen
- Betriebssystem-Updates der Instanz liegen bei mir
- Kurze Ausfallzeit bei Deployments (alter Container stoppt vor dem neuen), dafür kein doppelter Scheduler
- PostgreSQL läuft als Container auf der Instanz; Backups per täglichem `pg_dump` nach S3 und EBS-Snapshots (Alternative RDS: verwaltet, aber feste Zusatzkosten)
- Secrets im AWS Secrets Manager, Zugriff über die IAM-Rolle der Instanz
