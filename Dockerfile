# syntax=docker/dockerfile:1

# ---- Build stage -----------------------------------------------------------
FROM amazoncorretto:25-alpine-jdk AS build
WORKDIR /build

# Cache dependency resolution separately from source changes.
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN ./mvnw -B dependency:go-offline

COPY src ./src
RUN ./mvnw -B clean package -DskipTests

# Split the fat jar into layers (deps / loader / app classes) so unchanged
# dependency layers are cached across builds instead of re-pushed every time.
RUN java -Djarmode=tools -jar target/*.jar extract --layers --launcher --destination /build/extracted

# ---- Runtime stage ----------------------------------------------------------
FROM eclipse-temurin:25-jre-alpine
WORKDIR /app

# Ordered from least to most frequently changed, for layer cache reuse.
COPY --from=build /build/extracted/dependencies/ ./
COPY --from=build /build/extracted/spring-boot-loader/ ./
COPY --from=build /build/extracted/snapshot-dependencies/ ./
COPY --from=build /build/extracted/application/ ./

USER 1000
EXPOSE 8080
ENV TZ="Africa/Johannesburg"

HEALTHCHECK --interval=30s --timeout=10s --start-period=90s --retries=3 \
  CMD wget -qO- http://localhost:8080/actuator/health || exit 1

ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]
