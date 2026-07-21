FROM eclipse-temurin:21-jre-alpine

RUN adduser -D -H appuser

WORKDIR /app

COPY build/libs/*.jar app.jar

USER appuser

ENTRYPOINT ["java", "-jar", "app.jar"]
