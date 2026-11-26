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
