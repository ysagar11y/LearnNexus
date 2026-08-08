# syntax=docker/dockerfile:1

# ---- build -----------------------------------------------------------
# Same image family as ./mvnw.sh, so a local `docker build` reuses the layer
# and dependency cache a developer already has.
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app

# BuildKit cache mount for ~/.m2: persists across builds on the same
# builder without baking Maven's cache into any image layer, so it speeds up
# both local iteration and CI without affecting the shipped artifact.
COPY pom.xml .
RUN --mount=type=cache,target=/root/.m2 mvn -B -q dependency:go-offline

COPY src ./src
# Not fully offline: dependency:go-offline reliably resolves declared
# dependencies but can miss a handful of plugin/test-scope transitives, so
# this is left free to fetch the gaps. The cache mount still means only those
# gaps ever hit the network.
RUN --mount=type=cache,target=/root/.m2 mvn -B -q -DskipTests package \
 && mv target/*.jar app.jar

# ---- runtime -----------------------------------------------------------
# JRE only — the build toolchain does not belong in the deployed image.
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Runs as a non-root user; most free hosts (Render included) don't require
# this, but it costs nothing and it's the right default for a container that
# faces the internet.
RUN addgroup -S learnnexus && adduser -S learnnexus -G learnnexus
COPY --from=build /app/app.jar app.jar
USER learnnexus

# Render (and most free PaaS targets) inject $PORT and route to it; the
# server binds application.yml's server.port, which already reads it.
ENV SERVER_PORT=8081
EXPOSE 8081

# Container-level memory headroom for a free-tier instance (Render's free
# plan gives 512 MB); -XX:MaxRAMPercentage lets the JVM size its heap to
# whatever memory the container actually has rather than guessing.
ENTRYPOINT ["sh", "-c", "java -XX:MaxRAMPercentage=75 -Dserver.port=${PORT:-$SERVER_PORT} -jar app.jar"]
