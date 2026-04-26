# syntax=docker/dockerfile:1
# Production-oriented: non-root runtime, slim JRE only in final image.
# Build (from repo root): docker build -t payment-service:local -f payment-service/Dockerfile payment-service

FROM maven:3.9.9-eclipse-temurin-21-alpine AS build
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN mvn -B -q -e package -DskipTests

FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S -g 10001 app && adduser -S -D -H -u 10001 -G app app
WORKDIR /app
COPY --from=build /app/target/payment-service-*.jar /app/app.jar
RUN chown -R app:app /app
USER app
EXPOSE 8080
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75.0 -XX:InitialRAMPercentage=40.0 -Djava.security.egd=file:/dev/./urandom"
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
