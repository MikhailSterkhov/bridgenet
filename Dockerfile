# syntax=docker/dockerfile:1

# ---- Build stage: compile the whole system with the project's own script ----
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /src
COPY . .
# Cache the Maven local repository between image builds (BuildKit).
RUN --mount=type=cache,target=/root/.m2/repository bash bridgenet build

# ---- Runtime stage: minimal JRE 8 (the project's canonical Java version) ----
FROM eclipse-temurin:8-jre
RUN groupadd --system bridgenet \
 && useradd --system --gid bridgenet --home-dir /opt/bridgenet bridgenet

WORKDIR /opt/bridgenet
COPY --from=build --chown=bridgenet:bridgenet /src/.build/ ./

# Loopback binds are unreachable through published container ports.
# Intra-JVM service endpoints are bound via DI (services/loader), no network layer involved.
RUN sed -i 's/"host": "127.0.0.1"/"host": "0.0.0.0"/' etc/mtpconfig.json \
 && sed -i 's|<host>127.0.0.1</host>|<host>0.0.0.0</host>|' etc/rest_server.xml \
 && mkdir -p logs \
 && chown bridgenet:bridgenet logs

USER bridgenet

# 6791 - MTP protocol, 4590 - REST API
EXPOSE 6791 4590

ENV JAVA_OPTS=""
CMD ["sh", "-c", "exec java $JAVA_OPTS -jar bridgenet-server.jar"]
