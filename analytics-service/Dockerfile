FROM maven:3.9.9-eclipse-temurin-17 AS build
WORKDIR /build
COPY pom.xml .
COPY src ./src
RUN mvn -B -DskipTests package
FROM eclipse-temurin:17-jre-alpine
RUN addgroup -S analytics && adduser -S analytics -G analytics
USER analytics
WORKDIR /app
COPY --from=build /build/target/analytics-service-0.1.0.jar app.jar
EXPOSE 8082
ENTRYPOINT ["java", "-jar", "app.jar"]
