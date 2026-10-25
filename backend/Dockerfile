# --- Build stage ---
FROM eclipse-temurin:17-jdk AS build
WORKDIR /workspace

# Resolve dependencies first so they are cached between source changes
COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw -B -q dependency:go-offline

COPY src src
RUN ./mvnw -B -q -DskipTests package && cp target/*.jar app.jar

# --- Runtime stage ---
FROM eclipse-temurin:17-jre
WORKDIR /app

RUN groupadd --system civsetu && useradd --system --gid civsetu --no-create-home civsetu
COPY --from=build --chown=civsetu:civsetu /workspace/app.jar app.jar
USER civsetu

ENV SPRING_PROFILES_ACTIVE=prod
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "app.jar"]
