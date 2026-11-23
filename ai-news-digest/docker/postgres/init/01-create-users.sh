#!/bin/sh
# Creates the database users (docs/06-security.md). Runs once on an empty data volume via
# /docker-entrypoint-initdb.d (local Compose and Testcontainers).
# - newsdigest_migration: used by Flyway only, creates and owns the schemas
# - newsdigest_app: used by the app, data access only (privileges granted by the migrations)
set -eu

: "${DB_MIGRATION_PASSWORD:?DB_MIGRATION_PASSWORD must be set}"
: "${DB_APP_PASSWORD:?DB_APP_PASSWORD must be set}"

psql -v ON_ERROR_STOP=1 \
  --username "$POSTGRES_USER" \
  --dbname "$POSTGRES_DB" \
  --set=db="$POSTGRES_DB" \
  --set=migration_password="$DB_MIGRATION_PASSWORD" \
  --set=app_password="$DB_APP_PASSWORD" <<'EOSQL'
CREATE ROLE newsdigest_migration LOGIN PASSWORD :'migration_password';
CREATE ROLE newsdigest_app LOGIN PASSWORD :'app_password';
REVOKE ALL ON DATABASE :"db" FROM PUBLIC;
GRANT CONNECT, CREATE ON DATABASE :"db" TO newsdigest_migration;
GRANT CONNECT ON DATABASE :"db" TO newsdigest_app;
EOSQL
