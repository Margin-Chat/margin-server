# Build stage
FROM maven:3.9-eclipse-temurin-25 AS builder

WORKDIR /app

# Copy pom.xml and source
COPY pom.xml .
COPY src src

# Build directly (downloads dependencies as needed)
RUN mvn clean package -DskipTests -B

# Runtime stage
FROM eclipse-temurin:25-jre

WORKDIR /app

# Copy the jar from build stage
COPY --from=builder /app/target/*.jar app.jar

# Expose ports (HTTP and WebSocket)
EXPOSE 8080 8081

# JVM options for containerized environment
ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0"

# Run the application
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]