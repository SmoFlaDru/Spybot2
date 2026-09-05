FROM gradle:9.1.0-jdk25 AS build

WORKDIR /workspace
COPY . .
RUN gradle --no-daemon --max-workers=1 -Dkotlin.compiler.execution.strategy=in-process :spybot-recorder:bootJar

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
