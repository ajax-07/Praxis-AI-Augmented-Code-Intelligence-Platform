# ─────────────────────────────────────────────────────────────────────────────
# Praxis backend — multi-stage, production image
#
# Stage 1 builds the Spring Boot fat jar with the Gradle wrapper (JDK 21).
# Stage 2 ships only the JRE + jar, runs as a non-root user, and is container-
# memory aware. Build context is this directory (Backend/praxis-service).
# ─────────────────────────────────────────────────────────────────────────────

# ---- Stage 1: build ---------------------------------------------------------
FROM eclipse-temurin:21-jdk AS build
WORKDIR /app

# Copy only what's needed to resolve dependencies first, so the (slow) dependency
# download layer is cached and reused until build.gradle/settings.gradle change.
COPY gradlew ./
COPY gradle ./gradle
COPY settings.gradle build.gradle ./
RUN chmod +x gradlew && ./gradlew --no-daemon dependencies > /dev/null 2>&1 || true

# Now the source. Running only `bootJar` produces the single runnable fat jar
# (the Spring Boot plugin's bootJar task does not trigger the plain `jar` task),
# so build/libs/*.jar is unambiguous. Tests need Postgres/Redis, so skip them in
# the image build — CI runs the suite separately (./gradlew test).
COPY src ./src
RUN ./gradlew --no-daemon clean bootJar -x test

# ---- Stage 2: runtime -------------------------------------------------------
FROM eclipse-temurin:21-jre-alpine AS runtime

# curl powers the container HEALTHCHECK; the non-root user limits blast radius.
RUN apk add --no-cache curl \
 && addgroup -S praxis && adduser -S praxis -G praxis
WORKDIR /app

COPY --from=build --chown=praxis:praxis /app/build/libs/*.jar app.jar
USER praxis

EXPOSE 8145

# Let the JVM size its heap from the container's cgroup limit (Java 21 is
# container-aware; this makes the ceiling explicit and tunable at deploy time).
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75.0"

# actuator health is public (see SecurityConfig) and cheap — ideal for orchestration.
HEALTHCHECK --interval=15s --timeout=3s --start-period=60s --retries=5 \
  CMD curl -fsS http://localhost:8145/actuator/health || exit 1

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]

# ─── Hot reload (dev only — NOT enabled) ─────────────────────────────────────
# For live backend reloads, don't bake code into the image. Instead run the
# Gradle continuous build against a bind-mounted source tree (see the commented
# `backend` dev block in Docker/docker-compose.yml). In short:
#   1. add `developmentOnly 'org.springframework.boot:spring-boot-devtools'` to build.gradle
#   2. mount the project into a JDK container and run: ./gradlew bootRun --continuous
#   3. devtools restarts the context on recompiled classes.
