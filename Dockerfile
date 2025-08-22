# Multi-stage Docker build for SSH Terminal Application Backend
# Stage 1: Build environment
FROM maven:3.9.6-eclipse-temurin-17 AS build

WORKDIR /app

# Copy Maven files for dependency caching
COPY pom.xml .
COPY owasp-dependency-check-suppressions.xml .

# Download dependencies (this layer will be cached if pom.xml doesn't change)
RUN mvn dependency:go-offline -B

# Copy source code
COPY src ./src

# Build the application (skip tests for faster build, run tests in CI/CD)
RUN mvn clean package -DskipTests

# Stage 2: Runtime environment
FROM eclipse-temurin:17-jre-alpine

# Install required system packages for SSH functionality
RUN apk add --no-cache \
    openssh-client \
    curl \
    bash \
    && rm -rf /var/cache/apk/*

# Create application user for security
RUN addgroup -g 1000 appgroup && \
    adduser -D -u 1000 -G appgroup appuser

# Set working directory
WORKDIR /app

# Copy the built JAR from build stage
COPY --from=build /app/target/*.jar app.jar

# Create necessary directories and set permissions
RUN mkdir -p /app/logs /app/temp /app/uploads && \
    chown -R appuser:appgroup /app && \
    chmod 755 /app/logs /app/temp /app/uploads

# Switch to non-root user
USER appuser

# Expose the application port
EXPOSE 8100

# Health check
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
  CMD curl -f http://localhost:8100/actuator/health || exit 1

# JVM optimization for containerized environment
ENV JAVA_OPTS="-Xms512m -Xmx1024m -XX:+UseG1GC -XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0"

# Run the application
CMD ["sh", "-c", "java $JAVA_OPTS -Djava.security.egd=file:/dev/./urandom -jar app.jar"]