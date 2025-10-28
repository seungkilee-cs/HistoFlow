# Multi-stage build for HistoFlow Kotlin backend

# -------- Build stage --------
FROM gradle:8.10.2-jdk17-jammy AS builder
WORKDIR /workspace

# Copy project files into the build context
COPY gradlew ./
COPY gradle ./gradle
COPY build.gradle.kts settings.gradle.kts ./
COPY src ./src

# Ensure the gradle wrapper is executable
RUN chmod +x gradlew

# Build the Spring Boot executable JAR and normalize the output name
RUN ./gradlew bootJar --no-daemon \
    && JAR_PATH="$(find build/libs -maxdepth 1 -type f -name '*-SNAPSHOT.jar' | head -n 1)" \
    && if [ -z "$JAR_PATH" ]; then JAR_PATH="$(find build/libs -maxdepth 1 -type f -name '*.jar' ! -name '*-plain.jar' | head -n 1)"; fi \
    && if [ -z "$JAR_PATH" ]; then echo "Spring Boot jar not found" && exit 1; fi \
    && cp "$JAR_PATH" app.jar

# -------- Runtime stage --------
FROM eclipse-temurin:17-jre-jammy
WORKDIR /app

# Copy the normalized Spring Boot jar built in the previous stage
COPY --from=builder /workspace/app.jar app.jar

EXPOSE 8080

ENV JAVA_OPTS=""
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
