# Values of the production environment; no secrets and no mail addresses (alarm_email comes from
# TF_VAR_alarm_email or the GitHub secret ALARM_EMAIL).
domain_name       = "fuechslexd.com"
github_repository = "FuechsleXD/ai-newsletter"
# IDs from the OIDC subject (CloudTrail), see variables.tf
github_repository_ids = {
  owner_id      = 23313823
  repository_id = 1468641943
}
state_bucket = "newsdigest-tfstate-172033175227"
# Instance only runs on Mondays 06:30-08:00 Berlin; topic schedule Mondays 07:00 (docs/betrieb/zeitfenster.md)
run_window = {
  start    = "30 6 ? * MON *"
  stop     = "0 8 ? * MON *"
  timezone = "Europe/Berlin"
  # Weekly snapshot of the stopped data volume, Mondays 09:00 UTC
  snapshot = "cron(0 9 ? * MON *)"
}
