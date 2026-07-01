# # ---- Stage 1: build ----
# FROM gradle:8.12-jdk21 AS build
# WORKDIR /app

# # Cache dependencies separately from source so code changes don't re-download the world
# COPY Backend/praxis-service/build.gradle Backend/praxis-service/settings.gradle ./
# RUN gradle dependencies --no-daemon || true

# COPY Backend/praxis-service/ .
# RUN gradle bootJar --no-daemon -x test

# # ---- Stage 2: run ----
# FROM eclipse-temurin:21-jre-alpine
# WORKDIR /app

# RUN addgroup -S praxis && adduser -S praxis -G praxis
# COPY --from=build /app/build/libs/*.jar app.jar
# RUN chown praxis:praxis app.jar
# USER praxis

# EXPOSE 8145
# ENTRYPOINT ["java", "-jar", "app.jar"]