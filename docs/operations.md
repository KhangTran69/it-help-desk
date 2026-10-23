# Operations and staging guide

## Local metrics and dashboards

Set `GRAFANA_ADMIN_PASSWORD` to a unique local password, then start the optional services:

```bash
docker compose --profile observability up --build
```

Open Grafana at <http://localhost:3000>. Prometheus is not published to the host; Grafana reaches it on the private Compose network. The dashboard shows request rate, server errors, and ticket/asset creation counters. Prometheus rules include availability and elevated 5xx examples. No alert receiver is configured.

## Backup and restore

Run `scripts/backup-mysql.sh` to create a timestamped SQL dump under `backups/`, or supply a path. Restrict access to the output: it contains user and ticket data. Keep encrypted off-machine copies for any non-disposable environment.

Restore only into a disposable or explicitly selected target database. The restore script requires an interactive `RESTORE` confirmation and imports the SQL into the database selected by the active Compose environment. A normal Compose restart preserves the MySQL volume. Never use volume deletion as a backup or restore procedure.

The repository change adds the scripts but does not claim a successful recovery drill against a production database. To practice safely, use a separate Compose project name and a fresh named volume, insert fictional data, back it up, recreate that disposable database, restore, and compare the records.

## EC2 staging workflow prerequisites

The GitHub Actions workflow is manual (`workflow_dispatch`) and runs in the `staging` environment. It skips when any required repository/environment variable is missing. Configure these variables in the GitHub `staging` environment:

| Variable | Purpose |
| --- | --- |
| `AWS_ROLE_ARN` | IAM role trusted for GitHub OIDC |
| `AWS_REGION` | Region containing ECR and the EC2 instance |
| `EC2_INSTANCE_ID` | Target instance managed by Systems Manager |
| `ECR_REPOSITORY` | Existing ECR repository name |
| `APP_ENV_PARAMETER` | SecureString parameter containing the app's dotenv configuration |

The GitHub role needs narrowly scoped ECR push and SSM send/poll permissions for the selected instance. The EC2 instance needs the SSM managed-instance role, ECR pull permission, and permission to read only the selected SecureString parameter. Install Docker, AWS CLI, curl, and a working SSM agent on the instance. Set the parameter to contain `SPRING_PROFILES_ACTIVE=prod`, MySQL/RDS connection values, Redis endpoint values, and a strong JWT secret. Keep those values in Parameter Store, never in GitHub variables or source control.

The deploy script replaces the single app container, checks `/actuator/health`, and starts the previous image if the new one fails. A successful rollback still marks the workflow failed so the release is visible as unsuccessful. The app listens on the instance loopback at port 8080; configure a host reverse proxy with HTTPS and proxy it to `127.0.0.1:8080`. Restrict network access to RDS and Redis with security groups. Test the rollback procedure on a disposable instance before using important data.

This workflow has not been run because no AWS environment variables, role, instance, RDS, Redis endpoint, DNS, or TLS certificate are configured here. Its inclusion is deployment preparation, not evidence of a cloud deployment.

## SLA rules

SLA targets use elapsed seconds: High defaults to 86,400 seconds, Medium to 259,200, and Low to 1,209,600. The clock starts at ticket submission. It pauses when status becomes Resolved or Closed, including the elapsed time accrued so far. Reopening resumes with the remaining allowance. Changing priority preserves accrued time and recalculates the remaining allowance against the new target. If accrued time already exceeds the new target, the ticket is immediately overdue. No business calendar or notification escalation is implemented.
