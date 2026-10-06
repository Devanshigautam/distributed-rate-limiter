# ---- Stage 1: build the jar with the full JDK ----
FROM eclipse-temurin:21-jdk AS build
WORKDIR /app

# Download dependencies first, so Docker caches them when only the code changes
COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN sed -i 's/\r$//' mvnw && chmod +x mvnw && ./mvnw -B -q dependency:go-offline

COPY src src
RUN ./mvnw -B -q package -DskipTests

# ---- Stage 2: run it on a small JRE-only image ----
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
EXPOSE 8080
# Use at most 75% of the container's memory limit for the Java heap
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "app.jar"]
