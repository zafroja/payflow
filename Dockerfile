# syntax=docker/dockerfile:1.6
# One Dockerfile for every service:  docker build --build-arg MODULE=account-service -t payflow/account-service:1.0.0 .

# ---------- build stage ----------
FROM maven:3.9-eclipse-temurin-17 AS build
ARG MODULE
WORKDIR /workspace
COPY . .
# Cache mount keeps ~/.m2 between builds, so dependencies download only once
RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -q -pl ${MODULE} -am package -DskipTests

# ---------- runtime stage ----------
FROM eclipse-temurin:17-jre-alpine
ARG MODULE
# Numeric UID so Kubernetes runAsNonRoot can verify the user
RUN addgroup -S -g 10001 payflow && adduser -S -u 10001 -G payflow payflow
WORKDIR /app
COPY --from=build /workspace/${MODULE}/target/app.jar app.jar
USER 10001:10001
# Container-aware heap sizing; crash fast on OOM so Kubernetes restarts the pod
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError"
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
