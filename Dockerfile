FROM maven:3.9.9-eclipse-temurin-21 AS build

WORKDIR /workspace
COPY pom.xml .
RUN mvn -B -q dependency:go-offline
COPY src ./src
RUN mvn -B -q clean package -DskipTests

FROM eclipse-temurin:21-jre

WORKDIR /app
RUN useradd --system --uid 10001 scanner
COPY --from=build /workspace/target/dependency-scanner-*.jar /app/app.jar
RUN chown -R scanner:scanner /app
USER scanner

EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/app.jar"]
