# syntax=docker/dockerfile:1
# Backend multi-stage (design D9): build con JDK 21 → runtime JRE 21 con usuario no root.

FROM eclipse-temurin:21-jdk AS build
WORKDIR /app
COPY gradlew settings.gradle.kts build.gradle.kts ./
COPY gradle gradle
RUN chmod +x gradlew && ./gradlew dependencies --no-daemon -q > /dev/null
COPY src src
# Los tests corren en CI (necesitan Docker para Testcontainers); la imagen solo empaqueta.
RUN ./gradlew bootJar --no-daemon -q -x test

FROM eclipse-temurin:21-jre AS runtime
RUN groupadd --system app && useradd --system --gid app --home /app app
WORKDIR /app
COPY --from=build /app/build/libs/*.jar app.jar
USER app
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/app.jar"]
