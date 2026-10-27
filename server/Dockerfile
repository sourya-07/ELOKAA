# Production image of the ELOKA partner onboarding server.
#   docker build -t eloka-partner-server:<version> .
# Runs as a non-root user with the "prod" profile; all configuration comes from env vars
# (see docs/deploy/RUNBOOK.md once written). Logs go to /app/logs (mount a volume there).

FROM eclipse-temurin:25-jdk AS build
WORKDIR /workspace
COPY gradlew settings.gradle.kts build.gradle.kts ./
COPY gradle gradle
RUN ./gradlew --no-daemon dependencies > /dev/null
COPY src src
RUN ./gradlew --no-daemon bootJar -x test \
	&& java -Djarmode=tools -jar build/libs/eloka-partner-server-*.jar extract --layers --launcher --destination build/extracted

FROM eclipse-temurin:25-jre
# upgrade: pick up OS security fixes released after the base image was built
RUN apt-get update && apt-get upgrade -y && apt-get install -y --no-install-recommends curl && rm -rf /var/lib/apt/lists/* \
	&& useradd --system --uid 10001 --home /app eloka \
	&& mkdir -p /app/logs /app/data && chown -R eloka /app
WORKDIR /app
# Layers ordered from least to most frequently changing, for small image updates.
COPY --from=build /workspace/build/extracted/dependencies/ ./
COPY --from=build /workspace/build/extracted/spring-boot-loader/ ./
COPY --from=build /workspace/build/extracted/snapshot-dependencies/ ./
COPY --from=build /workspace/build/extracted/application/ ./
USER eloka
ENV SPRING_PROFILES_ACTIVE=prod \
	ELOKA_LOG_DIR=/app/logs \
	JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError -Duser.timezone=UTC"
EXPOSE 8080 8081
HEALTHCHECK --interval=15s --timeout=3s --start-period=60s --retries=5 \
	CMD curl -fsS http://localhost:8081/actuator/health/readiness || exit 1
ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]
