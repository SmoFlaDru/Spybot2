# syntax=docker/dockerfile:1.7

# Same base and wrapper as the web image, so both jars are built by the project's own Gradle
# version (the wrapper's) rather than whatever the `gradle:*` image happens to ship.
FROM eclipse-temurin:25-jdk AS build

WORKDIR /workspace

# Gradle distribution and dependencies live inside the primed layer below, not in a cache mount:
# BuildKit cache mounts are not exported to the GitHub Actions layer cache, so they were empty on
# every CI run. See the root Dockerfile for the full reasoning.
ENV GRADLE_USER_HOME=/workspace/.gradle-home

# Only dependency-affecting files first, so the primed layer survives source-only pushes.
COPY gradle ./gradle
COPY gradlew gradlew.bat settings.gradle.kts build.gradle.kts gradle.properties ./
COPY spybot-core/build.gradle.kts spybot-core/build.gradle.kts
COPY spybot-web/build.gradle.kts spybot-web/build.gradle.kts
COPY spybot-recorder/build.gradle.kts spybot-recorder/build.gradle.kts

RUN chmod +x ./gradlew && \
    ./gradlew --no-daemon --max-workers=1 -Dkotlin.compiler.execution.strategy=in-process -Dkotlin.incremental=false :spybot-core:dependencies :spybot-recorder:dependencies

COPY spybot-core ./spybot-core
COPY spybot-recorder ./spybot-recorder

RUN ./gradlew --no-daemon --max-workers=1 -Dkotlin.compiler.execution.strategy=in-process -Dkotlin.incremental=false :spybot-recorder:bootJar

FROM eclipse-temurin:25-jre

WORKDIR /app
COPY --from=build /workspace/spybot-recorder/build/libs/app.jar app.jar
COPY --from=build /workspace/spybot-recorder/build/sentry-agent/sentry-opentelemetry-agent.jar sentry-opentelemetry-agent.jar

# The agent handles OpenTelemetry bytecode instrumentation (JDBC/jOOQ, etc.) for fine-grained
# spans; SENTRY_AUTO_INIT=false stops it from also calling Sentry.init() itself, so the Spring
# Boot integration (configured via application.yml) remains the single init path. The OTel SDK's
# own auto-configuration otherwise defaults to also exporting via OTLP to a local collector on
# localhost:4317/4318, which doesn't exist here - disable those exporters since spans/logs reach
# Sentry through its own processor, not OTLP (per getsentry/sentry-java's sentry-opentelemetry
# README).
ENV SENTRY_AUTO_INIT=false
ENV OTEL_TRACES_EXPORTER=none
ENV OTEL_METRICS_EXPORTER=none
ENV OTEL_LOGS_EXPORTER=none
ENTRYPOINT ["java", "-javaagent:/app/sentry-opentelemetry-agent.jar", "-jar", "/app/app.jar"]
