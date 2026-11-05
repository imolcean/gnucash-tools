# syntax=docker/dockerfile:1

# Build from the repository root, so the reactor sees the parent pom and gnucash-book:
#   docker build -f gnucash-togo/Dockerfile -t gnucash-togo .

# ---- Build stage: compile and package the Spring Boot jar ----
FROM eclipse-temurin:25-jdk-alpine AS build
WORKDIR /workspace

# Download dependencies first so this layer is cached until a pom changes. The reactor lists every module, so
# every module's pom is needed, but only gnucash-book and gnucash-togo are built. Our own modules are excluded:
# they don't exist in a repository yet.
COPY mvnw pom.xml ./
COPY .mvn .mvn
COPY fincore-core/pom.xml fincore-core/
COPY gnucash-book/pom.xml gnucash-book/
COPY gnucash-togo/pom.xml gnucash-togo/
RUN chmod +x mvnw \
    && ./mvnw -B -q -pl gnucash-togo -am dependency:go-offline -DexcludeGroupIds=org.duckdns.gory26

COPY gnucash-book/src gnucash-book/src
COPY gnucash-togo/src gnucash-togo/src
# Tests run in development/CI, not during the image build.
RUN ./mvnw -B -q -pl gnucash-togo -am package -DskipTests \
    && java -Djarmode=tools -jar gnucash-togo/target/gnucash-togo-*.jar extract --layers --launcher --destination extracted

# ---- Runtime stage: JRE only, non-root user ----
FROM eclipse-temurin:25-jre-alpine
WORKDIR /app

RUN addgroup -S app && adduser -S app -G app
USER app

# Layers ordered from least to most frequently changed, for better caching.
COPY --from=build /workspace/extracted/dependencies/ ./
COPY --from=build /workspace/extracted/spring-boot-loader/ ./
COPY --from=build /workspace/extracted/snapshot-dependencies/ ./
COPY --from=build /workspace/extracted/application/ ./

ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75"
EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=5s --start-period=30s --retries=3 \
    CMD wget -qO- http://localhost:8080/actuator/health || exit 1

ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]
