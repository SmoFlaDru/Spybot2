# Spybot 2

A Spring Boot + Kotlin application that records TeamSpeak activity and presents it on a website.
It uses Spring MVC, Spring Security, jOOQ, Flyway, and JTE templates on top of PostgreSQL.

## Modules

- `spybot-core`: shared query layer, domain models, security principal, Flyway migrations, and jOOQ generation config.
- `spybot-web`: MVC app, JTE views, REST endpoints, security config, static assets, and scheduled jobs.
- `spybot-recorder`: dedicated recorder process for the TeamSpeak listener.
- `frontend`: Rollup bundle (`main.js` / `main.css`) packaged into `spybot-web`.
- `infrastructure`: Caddy and Compose configuration for the server.

## Building and running

Build the frontend bundle once (Docker does this for you in its Node stage):

```sh
cd frontend && npm ci && npm run package
```

Then use Gradle for everything else:

```sh
./gradlew :spybot-web:test
./gradlew :spybot-web:bootJar
```

Or run the whole stack locally with Docker:

```sh
docker compose build
docker compose up -d
```

## Code style

Kotlin is formatted with ktlint through pre-commit; the check runs on every pull request.
To install the hook locally, use `pre-commit install`.

## Changelog

Every pull request adds one bullet at the top of `CHANGELOG.md`; see the notes at the top of that file.
