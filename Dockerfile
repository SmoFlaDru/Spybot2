# syntax=docker/dockerfile:1.7

FROM node:24-bookworm AS frontend-build

WORKDIR /workspace/frontend
COPY frontend/package.json frontend/package-lock.json ./
RUN --mount=type=cache,target=/root/.npm npm ci
COPY frontend/ ./
RUN npm run package

FROM eclipse-temurin:25-jdk AS build

WORKDIR /workspace

# Copy Gradle wrapper and build descriptors first for better layer reuse.
COPY gradle ./gradle
COPY gradlew gradlew.bat settings.gradle.kts build.gradle.kts gradle.properties ./
COPY spybot-core/build.gradle.kts spybot-core/build.gradle.kts
COPY spybot-web/build.gradle.kts spybot-web/build.gradle.kts
COPY spybot-recorder/build.gradle.kts spybot-recorder/build.gradle.kts

# Prime dependency/plugin caches before copying source files.
RUN --mount=type=cache,target=/root/.gradle \
    chmod +x ./gradlew && \
    ./gradlew --no-daemon --max-workers=1 -Dkotlin.compiler.execution.strategy=in-process -Dkotlin.incremental=false :spybot-web:dependencies

COPY spybot-core ./spybot-core
COPY spybot-web ./spybot-web
COPY spybot ./spybot
COPY --from=frontend-build /workspace/frontend/output ./frontend/output

RUN --mount=type=cache,target=/root/.gradle \
    ./gradlew --no-daemon --max-workers=1 -Dkotlin.compiler.execution.strategy=in-process -Dkotlin.incremental=false :spybot-web:bootJar

FROM eclipse-temurin:25-jre

WORKDIR /app
COPY --from=build /workspace/spybot-web/build/libs/app.jar app.jar

EXPOSE 8000

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
