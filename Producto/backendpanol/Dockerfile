FROM maven:3.9.9-eclipse-temurin-21 AS builder

ARG JOOQ_DB_URL
ARG JOOQ_DB_USER
ARG JOOQ_DB_PASSWORD

WORKDIR /workspace/backendpanol

COPY backendpanol/.mvn .mvn
COPY backendpanol/mvnw mvnw
COPY backendpanol/pom.xml pom.xml
COPY databasepanol ../databasepanol
COPY backendpanol/src src

RUN chmod +x mvnw \
    && /bin/sh -ec 'test -n "$JOOQ_DB_URL"; test -n "$JOOQ_DB_USER"; test -n "$JOOQ_DB_PASSWORD"; ./mvnw -q -DskipTests package'

FROM eclipse-temurin:21-jre

WORKDIR /app

COPY --from=builder /workspace/backendpanol/target/backendpanol-0.0.1-SNAPSHOT.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
