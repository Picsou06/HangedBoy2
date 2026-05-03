FROM maven:3.9-eclipse-temurin-17 AS builder
WORKDIR /build
COPY . .
RUN mvn clean package -DskipTests

FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
RUN apk add --no-cache netcat-openbsd
COPY --from=builder /build/server/target/server-standalone.jar server.jar
EXPOSE 25568
HEALTHCHECK --interval=5s --timeout=3s --retries=10 --start-period=15s \
  CMD nc -z localhost 25568 || exit 1
ENTRYPOINT ["java", "-jar", "server.jar"]
