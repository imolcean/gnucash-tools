# syntax=docker/dockerfile:1

# Build from the repository root, which holds the parent pom and gnucash-book:
#   docker build -f gnucash-togo/Dockerfile -t gnucash-togo .
#
# The image needs the root pom, gnucash-book and gnucash-togo, and nothing else. Each module is built on its own
# with -f instead of through the root reactor: the reactor would need the pom of every module the root lists,
# so every new module would have to be copied in here.

# ---- Build stage: compile and package the Spring Boot jar ----
FROM eclipse-temurin:25-jdk-alpine AS build
WORKDIR /workspace

# Download dependencies first so this layer is cached until a pom changes. The root pom is installed alone (-N,
# its module list is not read), so the modules find their parent in the local repository. Our own modules are
# excluded from the download: they are built below, not fetched.
COPY mvnw pom.xml ./
COPY .mvn .mvn
COPY gnucash-book/pom.xml gnucash-book/
COPY gnucash-togo/pom.xml gnucash-togo/
RUN chmod +x mvnw \
    && ./mvnw -B -q -N install \
    && ./mvnw -B -q -f gnucash-book/pom.xml dependency:go-offline -DexcludeGroupIds=org.duckdns.gory26 \
    && ./mvnw -B -q -f gnucash-togo/pom.xml dependency:go-offline -DexcludeGroupIds=org.duckdns.gory26

COPY gnucash-book/src gnucash-book/src
COPY gnucash-togo/src gnucash-togo/src
# gnucash-book goes into the local repository (its jar and test-jar), where gnucash-togo's build finds it.
# Tests run in development/CI, not during the image build.
RUN ./mvnw -B -q -f gnucash-book/pom.xml install -DskipTests \
    && ./mvnw -B -q -f gnucash-togo/pom.xml package -DskipTests \
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
