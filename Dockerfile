# Stage 1: build the jar with Maven + JDK. None of this ships in the final image.
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /build
COPY pom.xml .
RUN mvn -B -q dependency:go-offline
COPY src ./src
RUN mvn -B -q -DskipTests package

# Stage 2: run it on a slim Java runtime.
FROM eclipse-temurin:17-jre
WORKDIR /app
RUN useradd --system --no-create-home shuttleflow
COPY --from=build /build/target/shuttleflow-*.jar app.jar
USER shuttleflow
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
