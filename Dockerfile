FROM maven:3.9.5-eclipse-temurin-17 AS build
WORKDIR /app
COPY . .
RUN mvn clean package

FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /app/target/SushiTgBot-1.0-SNAPSHOT.jar app.jar
CMD ["java", "-jar", "app.jar"]