-- Template for local development only (profile dev), not a Flyway migration.
-- Models and prices are maintained in the database only (ADR-013). Copy this file, replace the
-- placeholders with the current values from the provider's model list and price page, and run it
-- once against your local database, e.g.:
--   docker compose exec -T postgres psql -U postgres -d newsdigest < my-models.sql
-- Prices are USD per 1 million tokens; the search price is USD per web search.

INSERT INTO app.ai_model (provider_id, model_name, display_name, supports_web_search,
                          supports_structured_output, input_price_per_mtok, output_price_per_mtok,
                          price_per_search_call)
SELECT id, '<MODEL_ID>', '<DISPLAY_NAME>', true, true, <INPUT_PRICE>, <OUTPUT_PRICE>, <SEARCH_PRICE>
FROM app.ai_provider
WHERE code = 'ANTHROPIC';
