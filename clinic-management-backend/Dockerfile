FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY pom.xml .
COPY src ./src
RUN for attempt in 1 2 3; do \
      if mvn -B -DskipTests package; then exit 0; fi; \
      if [ "$attempt" -eq 3 ]; then exit 1; fi; \
      sleep $((attempt * 15)); \
    done

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /workspace/target/clinic-management-backend-0.1.0-SNAPSHOT.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
