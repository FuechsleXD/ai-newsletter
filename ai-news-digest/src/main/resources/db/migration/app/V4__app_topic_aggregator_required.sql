-- Every topic names its aggregator model; there is no configured default (ADR-013).
-- No topics exist yet in any environment, so no data migration is needed. Should a row without
-- an aggregator exist, this migration fails instead of guessing a model.
ALTER TABLE topic ALTER COLUMN aggregator_model_id SET NOT NULL;
