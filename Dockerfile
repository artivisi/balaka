# syntax=docker/dockerfile:1.7
#
# Balaka — accounting-finance
# Multi-stage build: Maven build + layered Spring Boot runtime on Alpine.
# No Nginx, no PostgreSQL — external managed DB, platform handles SSL/routing.

FROM maven:3.9-eclipse-temurin-25-alpine AS build
WORKDIR /src
COPY pom.xml .
RUN --mount=type=cache,target=/root/.m2 mvn -q -B dependency:go-offline
COPY .git ./.git
COPY src ./src
RUN --mount=type=cache,target=/root/.m2 mvn -q -B -DskipTests package && \
    mkdir -p /out && cp target/*.jar /out/app.jar && \
    cd /out && java -Djarmode=tools -jar app.jar extract --layers --destination layers

FROM azul/zulu-openjdk-alpine:25-jre
LABEL org.opencontainers.image.title="Balaka" \
      org.opencontainers.image.description="Open-source accounting application for Indonesian SMEs" \
      org.opencontainers.image.source="https://github.com/artivisi/balaka" \
      org.opencontainers.image.licenses="Apache-2.0" \
      org.opencontainers.image.vendor="PT Artivisi Intermedia"

RUN apk add --no-cache tzdata tini wget && \
    cp /usr/share/zoneinfo/Asia/Jakarta /etc/localtime && \
    echo "Asia/Jakarta" > /etc/timezone && \
    addgroup -S app && adduser -S app -G app && \
    mkdir -p /opt/app/documents && chown -R app:app /opt/app

WORKDIR /opt/app
COPY --from=build --chown=app:app /out/layers/dependencies/ ./
COPY --from=build --chown=app:app /out/layers/spring-boot-loader/ ./
COPY --from=build --chown=app:app /out/layers/snapshot-dependencies/ ./
COPY --from=build --chown=app:app /out/layers/application/ ./

USER app
VOLUME ["/opt/app/documents"]
EXPOSE 8080

ENV JAVA_OPTS="-XX:MaxRAMPercentage=75 -XX:+UseG1GC -XX:+ExitOnOutOfMemoryError" \
    SPRING_PROFILES_ACTIVE=production \
    APP_DOCUMENT_STORAGE_PATH=/opt/app/documents

ENTRYPOINT ["/sbin/tini", "--", "sh", "-c", "exec java $JAVA_OPTS org.springframework.boot.loader.launch.JarLauncher"]

HEALTHCHECK --interval=30s --timeout=5s --start-period=90s --retries=3 \
  CMD wget -qO- http://localhost:8080/actuator/health/liveness || exit 1
