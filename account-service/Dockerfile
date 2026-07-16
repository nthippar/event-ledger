FROM eclipse-temurin:21-jre

WORKDIR /app

COPY target/account-service-0.0.1-SNAPSHOT-exec.jar app.jar

EXPOSE 8081

ENTRYPOINT ["java", "-jar", "app.jar"]