# syntax=docker/dockerfile:1.7

FROM node:24-bookworm AS frontend-build

WORKDIR /workspace/frontend
COPY frontend/package.json frontend/package-lock.json ./
RUN --mount=type=cache,target=/root/.npm npm ci
COPY frontend/ ./
RUN npm run package

FROM eclipse-temurin:25-jdk AS build

WORKDIR /workspace

# Keep the Gradle wrapper distribution and all resolved dependencies *inside the image layer*
# rather than in a `--mount=type=cache`: BuildKit cache mounts are not part of what
# `cache-to: type=gha` exports, so on CI's fresh runners they start empty every run and Gradle
# re-downloaded its distribution and every dependency on each build. A plain directory in the
# primed layer below is exported/restored with the layer cache and survives across runs.
ENV GRADLE_USER_HOME=/workspace/.gradle-home

# Copy Gradle wrapper and build descriptors first for better layer reuse. Only files that affect
# dependency resolution belong here - anything that changes on every merge (CHANGELOG.md, sources)
# would invalidate the primed dependency layer below and force a full re-download.
COPY gradle ./gradle
COPY gradlew gradlew.bat settings.gradle.kts build.gradle.kts gradle.properties ./
COPY spybot-core/build.gradle.kts spybot-core/build.gradle.kts
COPY spybot-web/build.gradle.kts spybot-web/build.gradle.kts
COPY spybot-recorder/build.gradle.kts spybot-recorder/build.gradle.kts

# Prime dependency/plugin caches before copying source files.
RUN chmod +x ./gradlew && \
    ./gradlew --no-daemon --max-workers=1 -Dkotlin.compiler.execution.strategy=in-process -Dkotlin.incremental=false :spybot-core:dependencies :spybot-web:dependencies

COPY CHANGELOG.md ./
COPY spybot-core ./spybot-core
COPY spybot-web ./spybot-web
COPY --from=frontend-build /workspace/frontend/output ./frontend/output

# Bind-mount .git (read-only, not COPY'd) so the git-properties Gradle plugin can read the real
# commit; it changes every commit, so COPY-ing it would bust the dependency-priming layer cache.
RUN --mount=type=bind,source=.git,target=.git,readonly \
    ./gradlew --no-daemon --max-workers=1 -Dkotlin.compiler.execution.strategy=in-process -Dkotlin.incremental=false :spybot-web:bootJar

FROM eclipse-temurin:25-jre

WORKDIR /app
COPY --from=build /workspace/spybot-web/build/libs/app.jar app.jar
COPY --from=build /workspace/spybot-web/build/sentry-agent/sentry-opentelemetry-agent.jar sentry-opentelemetry-agent.jar

EXPOSE 8000

# The agent handles OpenTelemetry bytecode instrumentation (JDBC/jOOQ, HTTP, etc.) for
# fine-grained spans; SENTRY_AUTO_INIT=false stops it from also calling Sentry.init() itself, so
# the Spring Boot integration (configured via application.yml) remains the single init path. The
# OTel SDK's own auto-configuration otherwise defaults to also exporting via OTLP to a local
# collector on localhost:4317/4318, which doesn't exist here - disable those exporters since
# spans/logs reach Sentry through its own processor, not OTLP (per getsentry/sentry-java's
# sentry-opentelemetry README).
ENV SENTRY_AUTO_INIT=false
ENV OTEL_TRACES_EXPORTER=none
ENV OTEL_METRICS_EXPORTER=none
ENV OTEL_LOGS_EXPORTER=none
ENTRYPOINT ["java", "-javaagent:/app/sentry-opentelemetry-agent.jar", "-jar", "/app/app.jar"]
