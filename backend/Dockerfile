# Build stage: compile the Spring Boot app into an executable jar.
FROM eclipse-temurin:21-jdk AS build
WORKDIR /app

COPY gradlew settings.gradle build.gradle ./
COPY gradle gradle
RUN chmod +x gradlew

COPY src src
RUN ./gradlew bootJar --no-daemon

# Run stage: a slim JRE image with just the built jar.
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app/build/libs/*.jar app.jar

# Render (and most PaaS hosts) tell the container which port to listen on
# via $PORT; application.yml maps server.port to it.
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
