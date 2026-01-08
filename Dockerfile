FROM gradle:8.5-jdk17 AS build
WORKDIR /home/gradle/src
COPY --chown=gradle:gradle . .

RUN chmod +x ./gradlew && ./gradlew build -x test --no-daemon

FROM eclipse-temurin:17-jre-jammy
WORKDIR /app
COPY --from=build /home/gradle/src/build/libs/*-SNAPSHOT.jar app.jar

EXPOSE 8000
ENTRYPOINT ["java", "-jar", "app.jar"]

