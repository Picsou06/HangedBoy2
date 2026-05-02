FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY client/target/client-standalone.jar client.jar
ENV SERVER_HOST=server
ENV SERVER_PORT=25568
ENTRYPOINT ["java", "-jar", "client.jar"]
