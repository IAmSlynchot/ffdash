# Build stage: compile the Spring Boot app into an executable jar.
# Uses an image with Gradle preinstalled (matching the wrapper's pinned
# version) so the build doesn't also have to cold-download the Gradle
# distribution itself on top of resolving Maven dependencies.
FROM gradle:8.10.2-jdk21 AS build
WORKDIR /app

# Resolve dependencies in their own layer first, so Docker only re-runs this
# (slow) step when build.gradle/settings.gradle change, not on every source edit.
COPY settings.gradle build.gradle ./
RUN gradle --no-daemon dependencies

COPY src src
RUN gradle --no-daemon bootJar

# Run stage: a slim JRE image with just the built jar.
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app/build/libs/*.jar app.jar

# Render (and most PaaS hosts) tell the container which port to listen on
# via $PORT; application.yml maps server.port to it.
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
