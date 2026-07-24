# Build stage
FROM eclipse-temurin:24-jdk AS builder
WORKDIR /app

COPY gradlew gradlew.bat ./
COPY gradle gradle/
COPY build.gradle.kts settings.gradle.kts gradle.properties ./
COPY src src/
COPY domain domain/
COPY infra infra/
COPY api api/

RUN ./gradlew bootJar --no-daemon

# Runtime stage
FROM eclipse-temurin:24-jre
WORKDIR /app
COPY --from=builder /app/build/libs/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
