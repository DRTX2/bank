# syntax=docker/dockerfile:1.7
# Builds either JVM service: docker build -f services/Dockerfile --build-arg MODULE=core-banking .
# Glibc build image on purpose: the protobuf plugin downloads a native protoc that does not run on musl/alpine.
# Build stage runs natively ($BUILDPLATFORM): bytecode is arch-independent, so arm64 images need no QEMU.
FROM --platform=$BUILDPLATFORM eclipse-temurin:21-jdk-noble AS build
ARG MODULE
WORKDIR /src
COPY .mvn .mvn
COPY mvnw pom.xml ./
COPY proto/pom.xml proto/
COPY services/core-banking/pom.xml services/core-banking/
COPY services/risk-engine/pom.xml services/risk-engine/
RUN --mount=type=cache,target=/root/.m2 ./mvnw -B -q -pl services/${MODULE} -am dependency:go-offline || true
COPY proto proto
COPY services services
RUN --mount=type=cache,target=/root/.m2 ./mvnw -B -q -pl services/${MODULE} -am package -DskipTests \
 && java -Djarmode=tools -jar services/${MODULE}/target/${MODULE}-*.jar extract --layers --launcher --destination /app

# Distroless: no shell, no package manager, runs as uid 65532.
FROM gcr.io/distroless/java21-debian12:nonroot
WORKDIR /app
COPY --from=build /app/dependencies/ ./
COPY --from=build /app/spring-boot-loader/ ./
COPY --from=build /app/snapshot-dependencies/ ./
COPY --from=build /app/application/ ./
USER nonroot
EXPOSE 8080 9090
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-XX:+ExitOnOutOfMemoryError", "org.springframework.boot.loader.launch.JarLauncher"]
