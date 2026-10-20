# IT Helpdesk & Asset Management System

An educational portfolio project: a containerized Spring Boot IT help desk and asset management application. It is a single deployable application, not a microservice system, using Java 17, Spring Boot, Thymeleaf, Spring Data JPA, MySQL, Flyway, Redis, Spring Security, and BCrypt.

## Quick Start

1. Copy .env.example to .env.
2. Replace the database and JWT placeholder values with unique local secrets. Never commit .env.
3. Run:

   ~~~bash
   docker compose up --build
   ~~~

Open [http://localhost:8080](http://localhost:8080). Health check: [http://localhost:8080/actuator/health](http://localhost:8080/actuator/health). MySQL data persists in a Docker volume; docker compose down -v deletes that volume.

For local development without Docker:

~~~bash
mvn spring-boot:run -Dspring-boot.run.profiles=h2,demo
~~~

The demo profile is only for local demonstration. Do not enable it for a public deployment.

Demo credentials (local only): employee / employee123 and technician / technician123.

## Features

- Role-based employee and technician experiences, enforced by Spring Security and service-level ownership checks.
- Employees submit tickets, track only their own requests, follow an Open → In Progress → Resolved → Closed timeline, and add public comments.
- Technicians search, filter, paginate, sort, assign, edit, and update tickets; internal notes remain private to technicians.
- Resolution and closure require notes. Ticket edits use optimistic locking to reject stale updates rather than silently overwrite newer changes.
- Ticket activity history, asset inventory management, duplicate asset-tag validation, and paginated asset search.
- Timestamps are stored in UTC and shown in Philippine Time (PHT).
- Dashboard metrics are cached with Redis; cache failures fall back gracefully.
- Browser pages retain CSRF protection; REST endpoints support JWT bearer authentication.
- OpenAPI/Swagger documentation and Spring Boot Actuator health and metrics endpoints.

## Architecture

This project is a layered Spring Boot monolith: Thymeleaf and REST controllers call transactional services, which use Spring Data JPA repositories. Flyway manages the relational schema. Redis is an optional cache service in Compose; it does not make the app a separately deployed microservice.

~~~mermaid
flowchart LR
  Browser[Thymeleaf browser UI] --> App[Spring Boot application]
  Client[REST client with JWT] --> App
  App --> MySQL[(MySQL + Flyway)]
  App --> Redis[(Redis cache)]
~~~

Key packages: web for browser routes, api for JSON endpoints, service for business rules, repo for persistence, security for JWT support, and events for ticket audit events.

## Roles and Ticket Safety

Employees can create tickets and access their own /my-tickets progress pages. Technician-only dashboard, queue, ticket-management, and asset routes are protected server-side. Employee ticket details and comments are scoped to the authenticated owner; internal technician notes are not shown to employees.

CSRF remains enabled for browser forms. The API accepts bearer tokens from POST /api/auth/login; configure a unique JWT_SECRET via the environment. BCrypt is used for account passwords. Demo accounts are seeded only with the explicit demo profile.

## REST API and Monitoring

- Swagger UI: [http://localhost:8080/swagger-ui.html](http://localhost:8080/swagger-ui.html)
- OpenAPI JSON: /v3/api-docs
- Login: POST /api/auth/login with a username and password JSON body
- Ticket endpoints: /api/tickets
- Asset endpoints: /api/assets
- Health: /actuator/health
- Metrics: /actuator/metrics (available metrics depend on the active environment)

Send the returned token as Authorization: Bearer <token>. Technician API actions require a technician role; /api/tickets/my returns tickets scoped to the signed-in user.

## Database Migrations

- V1__init.sql: users, tickets, assets, ticket history, and base indexes.
- V2__audit_fields.sql: audit field-diff columns.
- V3__add_indexes.sql: indexes for common ticket, asset, user, and history queries.
- V4__ticket_comments_and_optimistic_lock.sql: public/internal comments and ticket versioning.

## Tests

Run the Maven suite:

~~~bash
mvn verify
~~~

Run the Selenium browser workflow locally (requires Chrome/Chromium):

~~~bash
mvn verify -Dbrowser.tests=true
~~~

Testcontainers exercises MySQL migrations and Redis cache invalidation when Docker is available. CI runs Maven verification, the browser flow, and a container image build.

## Current Screenshots

![Employee ticket progress](docs/screenshots/employee-ticket-progress.png)

![Technician ticket detail](docs/screenshots/technician-ticket-detail.png)

## Known Limitations

- No AWS or other hosted deployment is configured; CI builds the image but does not publish or deploy it.
- Attachments and email notifications are not implemented.
- JWT revocation/refresh-token flows and API rate limiting are not implemented.
- This is a portfolio/demo application, not a production deployment.
