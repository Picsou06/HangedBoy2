FROM maven:3.9-eclipse-temurin-17 AS builder
WORKDIR /build
COPY . .
RUN mvn clean package -DskipTests

FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY --from=builder /build/client/target/client-standalone.jar client.jar
ENV SERVER_HOST=server
ENV SERVER_PORT=25568
ENTRYPOINT ["java", "-jar", "client.jar"]
