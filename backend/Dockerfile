# JDK 21: build.gradle задаёт sourceCompatibility/targetCompatibility 21 (--release 21).
FROM eclipse-temurin:21-jdk AS build
WORKDIR /app
COPY gradlew ./
COPY gradle gradle
COPY build.gradle settings.gradle ./
COPY src ./src
RUN sed -i 's/\r$//' gradlew && chmod +x gradlew && ./gradlew bootJar --no-daemon

FROM amazoncorretto:21
WORKDIR /app
COPY --from=build /app/build/libs/backend-0.0.1-SNAPSHOT.jar /app/backend.jar
EXPOSE 8080
CMD ["java", "-jar", "backend.jar"]
