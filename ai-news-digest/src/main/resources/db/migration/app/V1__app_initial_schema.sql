-- Initial schema "app" (docs/03-datenmodell.md).
-- Runs as the migration user (owner of the schema). The app user only gets data access (DML).
-- Placeholder ${appUser}: name of the app database user (spring.flyway.placeholders.appUser).

-- ---------------------------------------------------------------------------------------------
-- Privileges for the app user: data access only, no DDL
-- ---------------------------------------------------------------------------------------------
GRANT USAGE ON SCHEMA app TO ${appUser};
ALTER DEFAULT PRIVILEGES IN SCHEMA app GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO ${appUser};
ALTER DEFAULT PRIVILEGES IN SCHEMA app GRANT USAGE, SELECT ON SEQUENCES TO ${appUser};

-- ---------------------------------------------------------------------------------------------
-- Configuration
-- ---------------------------------------------------------------------------------------------
CREATE TABLE schedule (
    id              bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name            varchar(100) NOT NULL,
    cron_expression varchar(100) NOT NULL,
    timezone        varchar(64)  NOT NULL,
    version         bigint       NOT NULL DEFAULT 0,
    created_at      timestamptz  NOT NULL DEFAULT now(),
    updated_at      timestamptz  NOT NULL DEFAULT now(),
    CONSTRAINT uq_schedule_name UNIQUE (name),
    CONSTRAINT ck_schedule_name_not_blank CHECK (btrim(name) <> ''),
    CONSTRAINT ck_schedule_cron_not_blank CHECK (btrim(cron_expression) <> ''),
    CONSTRAINT ck_schedule_timezone_not_blank CHECK (btrim(timezone) <> '')
);

CREATE TABLE ai_provider (
    id                   bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    code                 varchar(32)  NOT NULL,
    display_name         varchar(100) NOT NULL,
    -- Name of the secret in AWS Secrets Manager, never the key itself
    secret_name          varchar(255) NOT NULL,
    max_concurrent_calls integer,
    active               boolean      NOT NULL DEFAULT true,
    version              bigint       NOT NULL DEFAULT 0,
    created_at           timestamptz  NOT NULL DEFAULT now(),
    updated_at           timestamptz  NOT NULL DEFAULT now(),
    CONSTRAINT uq_ai_provider_code UNIQUE (code),
    CONSTRAINT ck_ai_provider_code CHECK (code ~ '^[A-Z][A-Z0-9_]*$'),
    CONSTRAINT ck_ai_provider_secret_name_not_blank CHECK (btrim(secret_name) <> ''),
    CONSTRAINT ck_ai_provider_max_concurrent_calls CHECK (max_concurrent_calls IS NULL OR max_concurrent_calls > 0)
);

CREATE TABLE ai_model (
    id                         bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    provider_id                bigint        NOT NULL,
    model_name                 varchar(200)  NOT NULL,
    display_name               varchar(200)  NOT NULL,
    supports_web_search        boolean       NOT NULL,
    supports_structured_output boolean       NOT NULL,
    max_output_tokens          integer,
    temperature                numeric(3, 2),
    -- Prices in USD per 1 million tokens
    input_price_per_mtok       numeric(12, 6) NOT NULL,
    output_price_per_mtok      numeric(12, 6) NOT NULL,
    price_per_search_call      numeric(12, 6),
    active                     boolean       NOT NULL DEFAULT true,
    version                    bigint        NOT NULL DEFAULT 0,
    created_at                 timestamptz   NOT NULL DEFAULT now(),
    updated_at                 timestamptz   NOT NULL DEFAULT now(),
    CONSTRAINT fk_ai_model_provider FOREIGN KEY (provider_id) REFERENCES ai_provider (id),
    -- Also serves as the index for fk_ai_model_provider (leading column)
    CONSTRAINT uq_ai_model_provider_model_name UNIQUE (provider_id, model_name),
    CONSTRAINT ck_ai_model_model_name_not_blank CHECK (btrim(model_name) <> ''),
    CONSTRAINT ck_ai_model_max_output_tokens CHECK (max_output_tokens IS NULL OR max_output_tokens > 0),
    CONSTRAINT ck_ai_model_temperature CHECK (temperature IS NULL OR temperature BETWEEN 0 AND 2),
    CONSTRAINT ck_ai_model_prices CHECK (
        input_price_per_mtok >= 0
        AND output_price_per_mtok >= 0
        AND (price_per_search_call IS NULL OR price_per_search_call >= 0))
);

CREATE TABLE topic (
    id                    bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name                  varchar(200) NOT NULL,
    description           text,
    -- NULL = default (newsdigest.run.initial-lookback-days)
    initial_lookback_days integer,
    -- NULL = default aggregator model (newsdigest.ai.aggregator-model-id)
    aggregator_model_id   bigint,
    schedule_id           bigint       NOT NULL,
    active                boolean      NOT NULL DEFAULT true,
    version               bigint       NOT NULL DEFAULT 0,
    created_at            timestamptz  NOT NULL DEFAULT now(),
    updated_at            timestamptz  NOT NULL DEFAULT now(),
    CONSTRAINT uq_topic_name UNIQUE (name),
    CONSTRAINT fk_topic_aggregator_model FOREIGN KEY (aggregator_model_id) REFERENCES ai_model (id),
    CONSTRAINT fk_topic_schedule FOREIGN KEY (schedule_id) REFERENCES schedule (id),
    CONSTRAINT ck_topic_name_not_blank CHECK (btrim(name) <> ''),
    CONSTRAINT ck_topic_initial_lookback_days CHECK (initial_lookback_days IS NULL OR initial_lookback_days > 0)
);
CREATE INDEX ix_topic_aggregator_model_id ON topic (aggregator_model_id);
CREATE INDEX ix_topic_schedule_id ON topic (schedule_id);

-- Set of research models of a topic (parallel, no order)
CREATE TABLE topic_model (
    topic_id bigint NOT NULL,
    model_id bigint NOT NULL,
    CONSTRAINT pk_topic_model PRIMARY KEY (topic_id, model_id),
    CONSTRAINT fk_topic_model_topic FOREIGN KEY (topic_id) REFERENCES topic (id) ON DELETE CASCADE,
    CONSTRAINT fk_topic_model_model FOREIGN KEY (model_id) REFERENCES ai_model (id)
);
CREATE INDEX ix_topic_model_model_id ON topic_model (model_id);

CREATE TABLE recipient (
    id           bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    email        varchar(320) NOT NULL,
    display_name varchar(200),
    active       boolean      NOT NULL DEFAULT true,
    version      bigint       NOT NULL DEFAULT 0,
    created_at   timestamptz  NOT NULL DEFAULT now(),
    updated_at   timestamptz  NOT NULL DEFAULT now(),
    CONSTRAINT uq_recipient_email UNIQUE (email),
    CONSTRAINT ck_recipient_email_lower_case CHECK (email = lower(email)),
    CONSTRAINT ck_recipient_email_format CHECK (email ~ '^[^@\s]+@[^@\s]+$')
);

CREATE TABLE topic_recipient (
    topic_id     bigint NOT NULL,
    recipient_id bigint NOT NULL,
    CONSTRAINT pk_topic_recipient PRIMARY KEY (topic_id, recipient_id),
    CONSTRAINT fk_topic_recipient_topic FOREIGN KEY (topic_id) REFERENCES topic (id) ON DELETE CASCADE,
    CONSTRAINT fk_topic_recipient_recipient FOREIGN KEY (recipient_id) REFERENCES recipient (id)
);
CREATE INDEX ix_topic_recipient_recipient_id ON topic_recipient (recipient_id);

-- Without an active row the default budget applies (newsdigest.budget.*)
CREATE TABLE budget (
    id              bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    month_limit_usd numeric(12, 6) NOT NULL,
    warn_percent    integer        NOT NULL,
    active          boolean        NOT NULL DEFAULT true,
    CONSTRAINT ck_budget_month_limit_usd CHECK (month_limit_usd >= 0),
    CONSTRAINT ck_budget_warn_percent CHECK (warn_percent BETWEEN 1 AND 100)
);
-- At most one active budget
CREATE UNIQUE INDEX uq_budget_single_active ON budget (active) WHERE active;

-- ---------------------------------------------------------------------------------------------
-- State and history
-- ---------------------------------------------------------------------------------------------
CREATE TABLE topic_state (
    topic_id               bigint PRIMARY KEY,
    -- Date (period_to) of the last run with status COMPLETED or NO_NEWS
    last_successful_run_at date,
    CONSTRAINT fk_topic_state_topic FOREIGN KEY (topic_id) REFERENCES topic (id) ON DELETE CASCADE
);

CREATE TABLE digest_run (
    id                  bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    topic_id            bigint       NOT NULL,
    aggregator_model_id bigint       NOT NULL,
    process_instance_id varchar(64),
    prompt_version      varchar(50)  NOT NULL,
    -- Run period as dates in the schedule's time zone, both inclusive
    period_from         date         NOT NULL,
    period_to           date         NOT NULL,
    status              varchar(20)  NOT NULL,
    started_at          timestamptz  NOT NULL DEFAULT now(),
    finished_at         timestamptz,
    error_message       text,
    -- Deliberate denormalisation: document result of the aggregator
    aggregate_json      jsonb,
    pdf_s3_key          varchar(1024),
    CONSTRAINT fk_digest_run_topic FOREIGN KEY (topic_id) REFERENCES topic (id),
    CONSTRAINT fk_digest_run_aggregator_model FOREIGN KEY (aggregator_model_id) REFERENCES ai_model (id),
    CONSTRAINT ck_digest_run_status CHECK (
        status IN ('RUNNING', 'COMPLETED', 'NO_NEWS', 'FAILED', 'SKIPPED_BUDGET', 'CANCELLED')),
    CONSTRAINT ck_digest_run_period CHECK (period_from <= period_to),
    CONSTRAINT ck_digest_run_finished CHECK (finished_at IS NULL OR finished_at >= started_at)
);
CREATE INDEX ix_digest_run_topic_id ON digest_run (topic_id);
CREATE INDEX ix_digest_run_aggregator_model_id ON digest_run (aggregator_model_id);
CREATE INDEX ix_digest_run_started_at ON digest_run (started_at);
-- At most one running run per topic
CREATE UNIQUE INDEX uq_digest_run_single_running ON digest_run (topic_id) WHERE status = 'RUNNING';

CREATE TABLE provider_result (
    id             bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    digest_run_id  bigint        NOT NULL,
    model_id       bigint        NOT NULL,
    status         varchar(20)   NOT NULL,
    -- Cleared after the retention period (newsdigest.retention.raw-response)
    raw_response   text,
    duration_ms    bigint        NOT NULL,
    input_tokens   integer,
    output_tokens  integer,
    search_calls   integer,
    -- Price snapshot at run time
    cost_usd       numeric(12, 6),
    error_message  text,
    CONSTRAINT fk_provider_result_digest_run FOREIGN KEY (digest_run_id) REFERENCES digest_run (id) ON DELETE CASCADE,
    CONSTRAINT fk_provider_result_model FOREIGN KEY (model_id) REFERENCES ai_model (id),
    -- Also serves as the index for fk_provider_result_digest_run (leading column)
    CONSTRAINT uq_provider_result_run_model UNIQUE (digest_run_id, model_id),
    CONSTRAINT ck_provider_result_status CHECK (status IN ('SUCCEEDED', 'FAILED')),
    CONSTRAINT ck_provider_result_numbers CHECK (
        duration_ms >= 0
        AND (input_tokens IS NULL OR input_tokens >= 0)
        AND (output_tokens IS NULL OR output_tokens >= 0)
        AND (search_calls IS NULL OR search_calls >= 0)
        AND (cost_usd IS NULL OR cost_usd >= 0))
);
CREATE INDEX ix_provider_result_model_id ON provider_result (model_id);

CREATE TABLE news_item (
    id                 bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    provider_result_id bigint        NOT NULL,
    headline           text          NOT NULL,
    summary            text          NOT NULL,
    url                varchar(2048) NOT NULL,
    normalized_url     varchar(2048) NOT NULL,
    -- Publication date as reported by the model (NULL = unknown)
    published_at       date,
    status             varchar(20)   NOT NULL,
    CONSTRAINT fk_news_item_provider_result FOREIGN KEY (provider_result_id) REFERENCES provider_result (id) ON DELETE CASCADE,
    CONSTRAINT ck_news_item_status CHECK (
        status IN ('VALID', 'OUT_OF_PERIOD', 'DUPLICATE_PREVIOUS', 'DUPLICATE_RUN', 'UNREACHABLE'))
);
CREATE INDEX ix_news_item_provider_result_id ON news_item (provider_result_id);
CREATE INDEX ix_news_item_normalized_url ON news_item (normalized_url);

CREATE TABLE aggregation_result (
    digest_run_id bigint PRIMARY KEY,
    model_id      bigint         NOT NULL,
    input_tokens  integer        NOT NULL,
    output_tokens integer        NOT NULL,
    cost_usd      numeric(12, 6) NOT NULL,
    duration_ms   bigint         NOT NULL,
    CONSTRAINT fk_aggregation_result_digest_run FOREIGN KEY (digest_run_id) REFERENCES digest_run (id) ON DELETE CASCADE,
    CONSTRAINT fk_aggregation_result_model FOREIGN KEY (model_id) REFERENCES ai_model (id),
    CONSTRAINT ck_aggregation_result_numbers CHECK (
        input_tokens >= 0 AND output_tokens >= 0 AND cost_usd >= 0 AND duration_ms >= 0)
);
CREATE INDEX ix_aggregation_result_model_id ON aggregation_result (model_id);

CREATE TABLE mail_delivery (
    id            bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    digest_run_id bigint      NOT NULL,
    recipient_id  bigint      NOT NULL,
    status        varchar(20) NOT NULL,
    sent_at       timestamptz,
    error_message text,
    CONSTRAINT fk_mail_delivery_digest_run FOREIGN KEY (digest_run_id) REFERENCES digest_run (id) ON DELETE CASCADE,
    CONSTRAINT fk_mail_delivery_recipient FOREIGN KEY (recipient_id) REFERENCES recipient (id),
    -- Also serves as the index for fk_mail_delivery_digest_run (leading column)
    CONSTRAINT uq_mail_delivery_run_recipient UNIQUE (digest_run_id, recipient_id),
    CONSTRAINT ck_mail_delivery_status CHECK (status IN ('PENDING', 'SENT', 'FAILED')),
    CONSTRAINT ck_mail_delivery_sent_at CHECK (status <> 'SENT' OR sent_at IS NOT NULL)
);
CREATE INDEX ix_mail_delivery_recipient_id ON mail_delivery (recipient_id);
