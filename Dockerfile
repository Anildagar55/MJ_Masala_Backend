# ---------- build ----------
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app
# dependencies first so they are cached between builds
COPY pom.xml .
RUN mvn -B -q dependency:go-offline
COPY src ./src
RUN mvn -B -q -DskipTests package

# ---------- run ----------
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

# run as a non-root user
RUN addgroup -S mj && adduser -S mj -G mj
COPY --from=build /app/target/*.jar app.jar
USER mj

EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "app.jar"]
