FROM node:22-bookworm-slim AS frontend-build
WORKDIR /app
COPY package.json ./
COPY frontend/package.json ./frontend/package.json
RUN npm install --no-audit --no-fund
COPY frontend ./frontend
RUN npm run frontend:build
RUN mkdir -p /app/frontend-dist/app /app/frontend-dist/reservar \
    && cp -R /app/src/main/resources/static/app/. /app/frontend-dist/app/ \
    && cp -R /app/src/main/resources/static/reservar/. /app/frontend-dist/reservar/

FROM maven:3.9.16-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn -q -DskipTests dependency:go-offline
COPY src ./src
COPY --from=frontend-build /app/frontend-dist/app ./src/main/resources/static/app
COPY --from=frontend-build /app/frontend-dist/reservar ./src/main/resources/static/reservar
# Full backend + browser tests are enforced by GitHub Actions before production deploys.
# Railway's Docker builder has no Docker socket, so Testcontainers cannot run here.
RUN mvn -q -DskipTests clean package

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app/target/helvoca-0.1.0-SNAPSHOT.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
