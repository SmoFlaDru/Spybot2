FROM gradle:9.1.0-jdk25 AS build

WORKDIR /workspace
COPY . .
RUN gradle --no-daemon --max-workers=1 -Dkotlin.compiler.execution.strategy=in-process :spybot-recorder:bootJar

FROM eclipse-temurin:25-jre

WORKDIR /app
COPY --from=build /workspace/spybot-recorder/build/libs/app.jar app.jar

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
