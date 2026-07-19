# SatSim public demo image (Render deployment, see render.yaml).
# Build stage: full JDK + Maven wrapper. Tests are skipped here — the CI
# pipeline is the quality gate; the image build must not depend on it.
FROM eclipse-temurin:21-jdk AS build
WORKDIR /build
COPY . .
RUN ./mvnw -q package -DskipTests

# Runtime stage: JRE only, single self-contained jar.
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /build/simulator/target/simulator-*.jar app.jar
# Free-tier container: cap the JVM to the container's memory budget.
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75.0"
EXPOSE 8090
# Render injects PORT; default to the project's standard 8090 elsewhere.
CMD ["sh", "-c", "java -Dserver.port=${PORT:-8090} -jar app.jar"]
