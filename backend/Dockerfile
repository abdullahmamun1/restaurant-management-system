# Build and run the Spring Boot API as a container. Used by Render (and by any other
# Docker host — Cloud Run, Fly.io, Koyeb, Railway — without modification).
#
# Two stages so the shipped image carries a JRE and one jar, not Maven and a .m2 cache.

# ---------- stage 1: build ----------
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build

# Dependencies resolve in their own layer, so a source-only change does not re-download them.
COPY pom.xml ./
RUN mvn -B -e dependency:go-offline

COPY src ./src
# Tests are skipped here on purpose: `mvn test` belongs in CI, and the integration profile
# (-Pit) needs a real database the build container has no business reaching.
RUN mvn -B -e clean package -DskipTests

# ---------- stage 2: runtime ----------
FROM eclipse-temurin:21-jre-alpine AS runtime
WORKDIR /app

# Never run the API as root.
RUN addgroup -S spring && adduser -S spring -G spring

COPY --from=build /build/target/*.jar /app/app.jar
USER spring

# MaxRAMPercentage keeps the heap inside a 512 MB container (Render's free plan); SerialGC
# costs less memory than G1 on a single small instance.
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75 -XX:+UseSerialGC -Djava.security.egd=file:/dev/./urandom"

EXPOSE 8080

# The port is assigned by the platform ($PORT on Render/Cloud Run) and falls back to 8080
# locally. `exec` so the JVM is PID 1 and receives SIGTERM for a clean shutdown.
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -Dserver.port=${PORT:-8080} -jar /app/app.jar"]
