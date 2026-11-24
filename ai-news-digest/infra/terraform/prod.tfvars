# Values of the production environment; no secrets and no mail addresses (alarm_email comes from
# TF_VAR_alarm_email or the GitHub secret ALARM_EMAIL).
domain_name       = "fuechslexd.com"
github_repository = "FuechsleXD/ai-newsletter"
state_bucket      = "fuechslexd-newsdigest-tfstate"
