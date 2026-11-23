-- Whether a model accepts a sampling temperature. Current Claude models reject it, so a temperature
-- may only be maintained for models that are marked as supporting it (ADR-013). The check applies
-- to every way of changing the data (API, UI, SQL).
ALTER TABLE ai_model ADD COLUMN supports_temperature boolean NOT NULL DEFAULT false;

-- Existing rows with a temperature keep working: they are marked as supporting it.
UPDATE ai_model SET supports_temperature = true WHERE temperature IS NOT NULL;

ALTER TABLE ai_model
    ADD CONSTRAINT ck_ai_model_temperature_supported
        CHECK (temperature IS NULL OR supports_temperature);
