FROM eclipse-temurin:21-jdk AS build
WORKDIR /src
COPY . .
RUN chmod +x gradlew && ./gradlew bootJar --no-daemon

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /src/build/libs/subscriptions-0.1.0.jar app.jar
VOLUME /app/data
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
