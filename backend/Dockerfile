FROM eclipse-temurin:21-jre-alpine

LABEL maintainer="SafePath AI"
LABEL description="SafePath AI Spring Boot Backend"

# Install curl for reliable container healthchecks
RUN apk add --no-cache curl

# Create non-root user for security
RUN addgroup -S safepath && adduser -S safepath -G safepath

WORKDIR /app

# Copy the JAR built by Maven
COPY target/backend-*.jar app.jar

# Switch to non-root user
USER safepath

EXPOSE 8080

# Health check
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
    CMD wget -q --spider http://localhost:8080/api/health || exit 1

ENTRYPOINT ["java", \
    "-Djava.security.egd=file:/dev/./urandom", \
    "-jar", "app.jar"]
