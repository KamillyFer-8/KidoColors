# syntax=docker/dockerfile:1
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build
COPY pom.xml ./
COPY kidocolors-core/pom.xml kidocolors-core/pom.xml
COPY backend/pom.xml backend/pom.xml
COPY kidocolors-core/src kidocolors-core/src
COPY backend/src backend/src
# Tests are run separately; this stage packages the runtime application.
RUN mvn -B -ntp -DskipTests package

FROM eclipse-temurin:21-jre-noble AS java21

# Keep this version aligned with the Playwright dependency in backend/pom.xml.
FROM mcr.microsoft.com/playwright/java:v1.63.0-noble AS runtime
USER root
COPY --from=java21 /opt/java/openjdk /opt/java/openjdk
ENV JAVA_HOME=/opt/java/openjdk \
    PLAYWRIGHT_BROWSERS_PATH=/ms-playwright \
    PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD=1 \
    CAPTURE_STORAGE_PATH=/app/storage/captures
WORKDIR /app
RUN mkdir -p /app/storage/captures && chown -R pwuser:pwuser /app
COPY --from=build --chown=pwuser:pwuser /build/backend/target/backend-0.1.0-SNAPSHOT.jar /app/app.jar
USER pwuser
EXPOSE 8080
ENTRYPOINT ["/opt/java/openjdk/bin/java", "-jar", "/app/app.jar"]
