# App image (docs/11-deployment-cicd.md). Build: docker build -t newsdigest:local .
# Tests are not run here: the CI job "backend" runs ./mvnw verify before the image is built.

# --- Build: JDK 21 + Maven wrapper, layered jar extracted for better layer caching ---
FROM eclipse-temurin:21.0.13.1_2-jdk-alpine-3.24 AS build
WORKDIR /build

# Dependencies first: this layer is only rebuilt when the pom or the wrapper changes
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw -B -ntp -q dependency:go-offline

COPY src/ src/
RUN ./mvnw -B -ntp -q package -DskipTests -Dspotless.check.skip=true -Djacoco.skip=true \
    && java -Djarmode=tools -jar target/ai-news-digest-*.jar extract --layers --launcher \
       --destination extracted

# --- Runtime: slim JRE 21, non-root, no secrets in the image ---
FROM eclipse-temurin:21.0.13.1_2-jre-alpine-3.24

RUN addgroup -S -g 10001 app && adduser -S -u 10001 -G app -h /app app
WORKDIR /app

# Layers from rarely to often changing
COPY --from=build --chown=app:app /build/extracted/dependencies/ ./
COPY --from=build --chown=app:app /build/extracted/spring-boot-loader/ ./
COPY --from=build --chown=app:app /build/extracted/snapshot-dependencies/ ./
COPY --from=build --chown=app:app /build/extracted/application/ ./

# PDFs are stored in S3 in production (profile prod, ADR-015), so no data directory is needed
USER app
EXPOSE 8080

# Internal actuator only; Caddy publishes /health to the outside
HEALTHCHECK --interval=30s --timeout=5s --start-period=90s --retries=3 \
  CMD wget -q -O /dev/null http://localhost:8080/actuator/health || exit 1

ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-XX:+ExitOnOutOfMemoryError", \
  "org.springframework.boot.loader.launch.JarLauncher"]
