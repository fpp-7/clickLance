FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY pom.xml .
COPY src ./src
RUN mvn -B -ntp package -DskipTests

FROM eclipse-temurin:21-jre
WORKDIR /app
RUN mkdir /app/data && chown -R 10001:10001 /app
USER 10001:10001
COPY --from=build /workspace/target/backend-cloud-0.1.0-SNAPSHOT.jar /app/app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
