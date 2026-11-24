-- Template for local development and eval only (profiles dev and eval), not a Flyway migration.
-- Creates one topic with its research models, its aggregator model and one recipient. Run
-- models.example.sql first. Copy this file, replace the placeholders and run it once, e.g.:
--   docker compose exec -T postgres psql -U postgres -d newsdigest < my-topic.sql
-- The recipient address ends in example.test: locally the mail lands in Mailpit
-- (http://localhost:8025) and never reaches a real mailbox. Never put real addresses into the repo.

BEGIN;

INSERT INTO app.topic (name, description, schedule_id, aggregator_model_id)
SELECT '<TOPIC_NAME>', '<TOPIC_DESCRIPTION>', s.id, m.id
FROM app.schedule s, app.ai_model m
WHERE s.name = 'Wöchentlich montags 07:00'
  AND m.model_name = '<AGGREGATOR_MODEL_ID>';

-- One row per research model; repeat for further models
INSERT INTO app.topic_model (topic_id, model_id)
SELECT t.id, m.id
FROM app.topic t, app.ai_model m
WHERE t.name = '<TOPIC_NAME>'
  AND m.model_name = '<RESEARCH_MODEL_ID>';

INSERT INTO app.recipient (email, display_name)
VALUES ('empfaenger@example.test', 'Lokaler Testempfänger')
ON CONFLICT (email) DO NOTHING;

INSERT INTO app.topic_recipient (topic_id, recipient_id)
SELECT t.id, r.id
FROM app.topic t, app.recipient r
WHERE t.name = '<TOPIC_NAME>'
  AND r.email = 'empfaenger@example.test';

COMMIT;
