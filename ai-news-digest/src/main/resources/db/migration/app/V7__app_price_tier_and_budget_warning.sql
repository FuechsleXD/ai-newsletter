-- Price tier by prompt length (open point 6, roadmap step 15): some models (e.g. Claude Haiku 5.5)
-- cost more per token when the prompt of a call exceeds a threshold. All three columns are set
-- together or not at all; without them the normal prices apply to every call.
ALTER TABLE ai_model
    ADD COLUMN long_context_threshold_tokens       integer,
    ADD COLUMN long_context_input_price_per_mtok   numeric(12, 6),
    ADD COLUMN long_context_output_price_per_mtok  numeric(12, 6),
    ADD CONSTRAINT ck_ai_model_long_context_complete CHECK (
        (long_context_threshold_tokens IS NULL
            AND long_context_input_price_per_mtok IS NULL
            AND long_context_output_price_per_mtok IS NULL)
        OR (long_context_threshold_tokens > 0
            AND long_context_input_price_per_mtok >= 0
            AND long_context_output_price_per_mtok >= 0));

COMMENT ON COLUMN ai_model.long_context_threshold_tokens IS
    'Input tokens of one call above which the long context prices apply to the whole call';

-- Budget warning once per calendar month (Europe/Berlin) when the spending reaches the warn
-- threshold; the row is written in the transaction that sends the mail.
CREATE TABLE budget_warning (
    month   date        PRIMARY KEY,
    sent_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT ck_budget_warning_first_of_month CHECK (extract(day FROM month) = 1)
);

-- Costs that are an estimate instead of usage reported by the provider (answer without content,
-- call that broke off; ADR-014). The cost report shows them separately (open point 7).
ALTER TABLE provider_result ADD COLUMN cost_estimated boolean NOT NULL DEFAULT false;
ALTER TABLE aggregation_result ADD COLUMN cost_estimated boolean NOT NULL DEFAULT false;
