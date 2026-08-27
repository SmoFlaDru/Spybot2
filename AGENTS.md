# Spybot2 Agent Guide

## Project status and boundaries

This repository contains two generations of Spybot:

- `spybot/`, `Spybot2/`, `manage.py`, and the Python files are the legacy Django application. They remain the behavior and UI reference while the rewrite is in progress.
- `spybot-core/`, `spybot-web/`, and `spybot-recorder/` are the active Spring Boot + Kotlin rewrite. Prefer changing these modules unless a task explicitly concerns legacy behavior.

The rewrite prioritizes feature and route compatibility before redesign. Preserve existing PostgreSQL data and public HTTP paths unless a task explicitly changes them.

## Architecture

- `spybot-core`: domain models, shared services, Spring Security principal, Flyway baseline, and jOOQ persistence/query code.
- `spybot-web`: Spring MVC application, JTE templates, security configuration, APIs, profile/passkey flows, and scheduled jobs.
- `spybot-recorder`: dedicated TeamSpeak event-recorder process.
- `frontend/`: Rollup bundle entry points. `main.js` and `main.css` are the canonical browser bundle URLs.
- `spybot/static/`: legacy static assets packaged into the Spring web JAR.
- `infrastructure/`: Caddy and Compose infrastructure configuration.

Use Spring MVC + JTE for server-rendered pages. Keep the existing Tabler, HTMX, ApexCharts, and browser-side behavior unless the task requires a change. Avoid adding JPA/Hibernate; persistence belongs in jOOQ services.

## Kotlin, Gradle, jOOQ, and Flyway

- The root build uses Java 25 toolchains and Kotlin targeting JVM 24. Follow the existing Gradle configuration rather than changing targets ad hoc.
- The database schema source of truth is `spybot-core/src/main/resources/db/migration/V1__baseline.sql`. Add future schema changes as Flyway migrations; do not edit existing applied migrations in a real deployment.
- jOOQ Kotlin sources are generated into `spybot-core/build/generated-src/jooq/main` with package `com.spybot.jooq`. Do not hand-edit generated files.
- Generated table references are imported from `com.spybot.jooq.tables.references`, for example `SPYBOT_USERPASSKEY`.
- `generateJooq` runs before Kotlin compilation. If generated imports appear unresolved, run:

  ```sh
  ./gradlew :spybot-core:generateJooq :spybot-core:compileKotlin
  ```

- Keep jOOQ generator artifacts aligned to the `jooqVersion` Gradle property. A mixed generator classpath causes runtime `NoSuchMethodError` failures.
- Prefer typed jOOQ DSL for simple CRUD and compact queries. Leave complex analytical SQL readable and well-tested rather than forcing an opaque DSL translation.
- For `TIMESTAMPTZ` generated fields, use `OffsetDateTime` and typed functions such as `DSL.currentOffsetDateTime()`, not `currentTimestamp()`.

## Authentication and security

- `MergedUserPrincipal` is the application principal. `is_superuser` maps to `ROLE_ADMIN`.
- Protect authenticated and administrative routes in `SecurityConfig`; never rely solely on hiding navigation links.
- HTML mutation flows use CSRF. Existing passkey endpoints have an explicit CSRF exemption for their JSON ceremony contract; do not broaden exemptions casually.
- Magic-link authentication is session based. Keep session creation/logout behavior compatible with the existing frontend.
- The current passkey backend is custom/WebAuthn4j-based. A planned migration is to `spring-security-webauthn`; when implementing it, replace the backend contract and update `frontend/passkeys.js` together. Existing stored passkeys are intentionally disposable: delete/clear them as part of that migration rather than attempting credential migration.

## Frontend and templates

- JTE templates live in `spybot-web/src/main/jte`; templates are precompiled. Use the existing `layout/base.kte` and page/fragment patterns.
- Template parameters must match controller model attributes and JTE types exactly.
- Run `npm ci && npm run package` inside `frontend/` to create `frontend/output/main.js` and `frontend/output/main.css` for local non-Docker builds.
- Import frontend packages through their public package entry points and use/initialize imported symbols so Rollup retains them. For custom elements, explicitly register the element when appropriate.
- Spring packages both `frontend/output/` and `spybot/static/` into `BOOT-INF/classes/static/`. Do not restore the old Django `collectstatic` shared-volume model.

## Docker and local development

- Production Compose is `docker-compose.yml`; local-only overrides belong in `docker-compose.override.yml`.
- Docker builds frontend assets in a Node stage and packages the Spring boot JAR in a Java stage. Keep the Dockerfile's dependency-layer split and BuildKit cache mounts intact.
- The local Compose override persists buildx cache under `.buildx-cache/`, which is ignored by Git and excluded from build context.
- Typical verification commands:

  ```sh
  ./gradlew :spybot-core:compileKotlin
  ./gradlew :spybot-web:test
  ./gradlew :spybot-web:bootJar
  docker compose build spybot-web
  docker compose up -d
  ```

## Testing expectations

- Add service tests for domain and query behavior, especially transactions that reassign user-linked data.
- Add MockMvc/security tests for routes, authorization, redirects, CSRF behavior, and stable JSON payloads.
- Use Testcontainers PostgreSQL for jOOQ/Flyway integration behavior when database semantics matter.
- For public pages and fragments, preserve URL and response compatibility with the Django app unless a change is explicitly approved.

## Working conventions

- Inspect nearby legacy Django code/templates when reproducing an existing behavior.
- Keep changes scoped; the worktree may contain unrelated user changes. Never reset, revert, or delete those changes.
- Use `apply_patch` for source edits and keep new text ASCII unless the file already needs Unicode.
- Do not commit generated build output, frontend output, Gradle caches, Docker build caches, secrets, or IDE user files.
