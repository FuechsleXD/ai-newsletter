-- Seed data (docs/03-datenmodell.md, docs/12-defaults.md).
-- No models (prices are entered manually), no budget row (default applies), no mail addresses.

-- Default schedule: Mondays 07:00 Europe/Berlin (Spring cron: second minute hour day month weekday)
INSERT INTO schedule (name, cron_expression, timezone)
VALUES ('Wöchentlich montags 07:00', '0 0 7 * * MON', 'Europe/Berlin');

-- First provider (ADR-007); the API key lives in AWS Secrets Manager under this name
INSERT INTO ai_provider (code, display_name, secret_name)
VALUES ('ANTHROPIC', 'Anthropic (Claude)', 'newsdigest/prod/ai/anthropic');
