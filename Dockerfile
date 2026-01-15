FROM eclipse-temurin:17-jre-jammy
WORKDIR /app
COPY build/libs/*-SNAPSHOT.jar app.jar

EXPOSE ${SERVER_PORT}
ENTRYPOINT ["java", "-jar", "app.jar"]