FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn -q -B dependency:go-offline
COPY src ./src
RUN mvn -q -B -DskipTests package

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=build /app/target/document-service.jar app.jar
RUN mkdir -p /data/documents
EXPOSE 8084
ENTRYPOINT ["java", "-jar", "app.jar"]
