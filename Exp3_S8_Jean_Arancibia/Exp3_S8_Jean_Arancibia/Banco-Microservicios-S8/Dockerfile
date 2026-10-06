# syntax=docker/dockerfile:1
FROM maven:3.9.9-eclipse-temurin-17 AS build
WORKDIR /proyecto
COPY pom.xml ./
COPY config-server ./config-server
COPY config-repo ./config-repo
COPY discovery-server ./discovery-server
COPY cuentas-service ./cuentas-service
COPY auth-server ./auth-server
ARG MODULE
RUN --mount=type=cache,target=/root/.m2 mvn -B -pl "${MODULE}" -am -DskipTests package

FROM eclipse-temurin:17-jre-jammy
ARG MODULE
RUN apt-get update && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --gid 10001 banco \
    && useradd --uid 10001 --gid banco --no-create-home banco \
    && mkdir -p /app/data /app/keys && chown -R banco:banco /app
WORKDIR /app
COPY --from=build --chown=banco:banco /proyecto/${MODULE}/target/${MODULE}-2.0.0.jar /app/app.jar
USER banco
ENV APP_PORT=8081
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=70.0"
EXPOSE 8888 8761 9000 8081
HEALTHCHECK --interval=10s --timeout=5s --start-period=60s --retries=12 \
    CMD curl -fsS "http://localhost:${APP_PORT}/actuator/health" || exit 1
ENTRYPOINT ["java","-jar","/app/app.jar"]
