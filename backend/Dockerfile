FROM maven:3.9.9-eclipse-temurin-17 AS build

WORKDIR /workspace
COPY pom.xml .
RUN for attempt in 1 2 3; do \
      mvn -B -ntp -DskipTests dependency:go-offline && break; \
      if [ "$attempt" -eq 3 ]; then exit 1; fi; \
      echo "Maven dependency download failed; retrying..." >&2; sleep 3; \
    done

COPY src ./src
RUN for attempt in 1 2 3; do \
      mvn -B -ntp -DskipTests package && break; \
      if [ "$attempt" -eq 3 ]; then exit 1; fi; \
      echo "Maven package dependency resolution failed; retrying..." >&2; sleep 3; \
    done

FROM eclipse-temurin:17-jre-alpine

RUN addgroup -S winpress && adduser -S winpress -G winpress
WORKDIR /app
COPY --from=build /workspace/target/winpress-commercial-1.0.0.jar /app/app.jar
RUN mkdir -p /app/storage/uploads && chown -R winpress:winpress /app

USER winpress
EXPOSE 8092
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
