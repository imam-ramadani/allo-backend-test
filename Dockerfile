# Multi-stage build for Allo Bank Backend Challenge
# Build the application using Maven
# Run on port 4110
# Start without requiring any manual steps

# ── Stage 1: Build ──────────────────────────────────────────────────────────
FROM eclipse-temurin:21-jdk-alpine AS builder

WORKDIR /app

# Copy Maven wrapper and pom first (layer cache for dependencies)
COPY allobank/.mvn/ .mvn/
COPY allobank/mvnw allobank/pom.xml ./
RUN ./mvnw dependency:go-offline -q

# Copy source and build
COPY allobank/src/ src/
RUN ./mvnw package -DskipTests -q

# ── Stage 2: Runtime ─────────────────────────────────────────────────────────
FROM eclipse-temurin:21-jre-alpine AS runtime

WORKDIR /app

COPY --from=builder /app/target/*.jar app.jar

EXPOSE 4110

ENTRYPOINT ["java", "-jar", "app.jar"]
