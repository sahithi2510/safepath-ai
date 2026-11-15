# ============================================================
# Stage 1: Build Application with Maven
# ============================================================
FROM maven:3.9.9-eclipse-temurin-21-alpine AS builder

WORKDIR /build

COPY pom.xml .
COPY src ./src

# Build production JAR (skipping tests for container image assembly)
RUN mvn clean package -DskipTests -B

# ============================================================
# Stage 2: Minimal Production JRE Runtime
# ============================================================
FROM eclipse-temurin:21-jre-alpine

LABEL maintainer="SafePath AI"
LABEL description="SafePath AI Spring Boot Backend"

# Install wget & curl for container healthchecks
RUN apk add --no-cache curl wget

# Create non-root user for security compliance
RUN addgroup -S safepath && adduser -S safepath -G safepath

WORKDIR /app

# Copy the compiled JAR from builder stage
COPY --from=builder /build/target/backend-*.jar app.jar

# Switch to non-root user
USER safepath

EXPOSE 8080

# Health check endpoint
HEALTHCHECK --interval=20s --timeout=10s --start-period=45s --retries=3 \
    CMD wget -q --spider http://localhost:8080/api/health || exit 1

ENTRYPOINT ["java", \
    "-Djava.security.egd=file:/dev/./urandom", \
    "-jar", "app.jar"]
