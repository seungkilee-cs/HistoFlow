FROM eclipse-temurin:21-jdk
WORKDIR /app
COPY gradlew settings.gradle.kts build.gradle.kts /app/
COPY gradle /app/gradle
RUN ./gradlew --no-daemon build -x test || true
COPY src /app/src
RUN ./gradlew --no-daemon build -x test
EXPOSE 8080
CMD ["java","-jar","build/libs/api.jar"]

