# Build Stage
FROM --platform=linux/amd64 maven:3.9.7-eclipse-temurin-21-alpine AS build
WORKDIR /app
COPY pom.xml .
COPY .mvn .mvn
COPY mvnw .
COPY mvnw.cmd .
COPY src src

# Build app without running tests as this is purely for the runtime container
RUN --mount=type=cache,target=/root/.m2 ./mvnw clean package -DskipTests

# Runtime Stage
FROM --platform=linux/amd64 eclipse-temurin:21-jre-alpine
VOLUME /tmp
WORKDIR /app

# Add a non-root user for security (SecOps requirement)
RUN addgroup -S appgroup && adduser -S appuser -G appgroup
USER appuser

COPY --from=build /app/target/trading-engine-*.jar app.jar
ENTRYPOINT ["java","-jar","/app/app.jar"]
