# ---- Build stage: compile and package with JDK 21 ----
FROM eclipse-temurin:21-jdk AS build
WORKDIR /app

# Copy the Maven wrapper and POM first so dependency resolution is cached
# as a layer and only re-runs when the POM changes.
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw -B -q dependency:go-offline

# Copy sources and build the executable jar (tests run in CI, not in the image build).
COPY src/ src/
RUN ./mvnw -B -q clean package -DskipTests

# ---- Runtime stage: slim JRE 21 ----
FROM eclipse-temurin:21-jre
WORKDIR /app

# The Spring Boot plugin produces a single runnable jar (the non-repackaged
# artifact is *.jar.original, which this glob does not match).
COPY --from=build /app/target/*.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
