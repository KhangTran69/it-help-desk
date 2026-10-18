# Docker deployment notes

## Local startup

From the repository root, copy `.env.example` to `.env`, replace the placeholder passwords with unique local values, then run:

```bash
docker compose up --build
```

Compose starts the Spring application, MySQL 8, and Redis 7 on a private bridge network. The application connects using `mysql:3306` and `redis:6379`; it waits until both dependency health checks pass. Flyway applies schema migrations when the application starts. The application image build runs `mvn verify` in the Maven/JDK 17 build stage before copying the packaged jar into a JRE-only runtime image.

The only published application port is `8080`. MySQL is intentionally not exposed on the host by default. If a local SQL client needs access, opt in with:

```bash
docker compose -f docker-compose.yml -f docker-compose.debug.yml up --build
```

The MySQL data volume survives a normal `docker compose down`. `docker compose down -v` removes persisted MySQL and Redis data and should be treated as destructive.

## Configuration

`.env.example` documents local Compose variables. `.env` is ignored by Git and excluded from the Docker build context. Keep production passwords in a secret manager or deployment environment, never in `.env.example`, the Dockerfile, or source control.

The `dev` Spring profile connects to local MySQL and uses debug logging with caching disabled. `prod` requires `DB_URL`, `DB_USERNAME`, and `DB_PASSWORD`, uses INFO logging, and enables Redis caching for dashboard ticket counts. Ticket creation, edits, assignments, and status changes evict the counters. The `h2` and `test` profiles disable Redis caching so they remain self-contained.

The container runs as an unprivileged user and probes `/actuator/health`. Only the `health` and `info` Actuator endpoints are exposed; detailed health output is hidden.

## Troubleshooting

- Docker API socket unavailable: start Docker Desktop, wait for its engine to report ready, then retry `docker info`.
- App starts before dependencies: inspect `docker compose ps` and `docker compose logs mysql redis app`; the app depends on healthy MySQL and Redis services.
- Authentication errors from MySQL: ensure `DB_PASSWORD` in `.env` matches the MySQL application user's password; for a newly initialized volume, Compose creates that user from `DB_USERNAME`.
- Migration errors: inspect `docker compose logs app`. Do not delete the named volume on a database containing data. For a disposable local database only, remove volumes with `docker compose down -v` and restart.
- Port 8080 conflict: stop the other local app or change the published side of `8080:8080`.

## Cloud deployment boundary

This Compose setup is a local/container foundation, not an AWS deployment. Production AWS work still requires an EC2/RDS design, TLS termination and DNS, GitHub Actions secrets/permissions, and an approved deployment method. Do not copy local Compose passwords into cloud workflows.
