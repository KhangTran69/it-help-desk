# Changelog

All notable changes to this project are documented here. The format follows
Keep a Changelog; versions follow semantic versioning.

## [0.1.0] - 2026-11-28

First tagged release line, covering the long-term roadmap phases 0-5.

### Added
- MIT license; CI concurrency control and failure report artifacts (phase 0).
- Quality gates: JaCoCo coverage gate (60% bundle / 70% service instruction
  coverage), SpotBugs findings, CycloneDX SBOM, Dependabot, CodeQL,
  palantir-java-format via Spotless, Trivy-gated image publishing (phase 1).
- Ticket state machine with enforced transitions; JPA Specifications for the
  ticket queue and asset inventory; consolidated service/API vocabularies;
  paginated comments; shared navigation fragment; unread-notification badge;
  CSP without unsafe-inline/unsafe-eval (phase 2).
- Forwarded-header strategy for reverse proxies; Swagger locked down in prod;
  JWT refresh tokens with rotation; optional best-effort notification email
  (Mailpit locally); SLA breach escalation sweep with helpdesk.sla.breached
  metric; Alertmanager routing (phase 3).
- ADMIN role with last-active-technician protection; fifteen-minute comment
  editing window; CSV export of the filtered technician queue (phase 4).
- Java 21 + Spring Boot 3.5.7 runtime with virtual threads in prod;
  ShedLock-guarded SLA sweep for multi-instance deployments; semantic
  versioning (phase 5).

### Changed
- Optimistic locking is strict everywhere: a missing expected version is a
  conflict.
- REST create/update use dedicated request records; GET /api/tickets supports
  the same filters as the technician queue.

### Security
- API docs no longer public in the prod profile; refresh tokens cannot call
  API endpoints; disabled accounts cannot mint tokens.
