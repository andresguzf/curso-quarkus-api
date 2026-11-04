####
# Imagen para Render (o cualquier plataforma que construya desde el repositorio).
# Multi-stage: compila el proyecto dentro de Docker, así no necesita target/ ya generado.
#
#   docker build -t andresguzf/quarkus-api .
#
# Los tests se saltan aquí porque necesitan Docker (Dev Services); corren en local con ./mvnw test.
# Local con la extensión de Quarkus: src/main/docker/Dockerfile.jvm (copia target/quarkus-app ya compilado).
####

# ===== 1) Build: JDK 25 + Maven Wrapper =====
FROM eclipse-temurin:25-jdk AS build
# El Maven Wrapper (distributionType=only-script) descarga Maven con curl y lo descomprime con unzip
RUN apt-get update && apt-get install -y --no-install-recommends curl unzip && rm -rf /var/lib/apt/lists/*
WORKDIR /build

# Primero solo el pom: las dependencias quedan en una capa cacheada mientras el pom no cambie
COPY mvnw pom.xml ./
COPY .mvn .mvn
RUN chmod +x mvnw && ./mvnw -B -q dependency:go-offline

COPY src src
RUN ./mvnw -B -q package -DskipTests

# ===== 2) Runtime: la misma base que Dockerfile.jvm =====
FROM registry.access.redhat.com/ubi9/openjdk-25-runtime:1.24

ENV LANGUAGE='en_US:en'

# Formato fast-jar: lanzador + dependencias + código + lo generado por Quarkus en el build
COPY --from=build --chown=185 /build/target/quarkus-app/lib/ /deployments/lib/
COPY --from=build --chown=185 /build/target/quarkus-app/*.jar /deployments/
COPY --from=build --chown=185 /build/target/quarkus-app/app/ /deployments/app/
COPY --from=build --chown=185 /build/target/quarkus-app/quarkus/ /deployments/quarkus/

# Render entrega el puerto en la variable PORT (quarkus.http.port=${PORT:8080})
EXPOSE 8080
USER 185
ENV JAVA_OPTS_APPEND="-Dquarkus.http.host=0.0.0.0 -Djava.util.logging.manager=org.jboss.logmanager.LogManager"
ENV JAVA_APP_JAR="/deployments/quarkus-run.jar"

ENTRYPOINT [ "/opt/jboss/container/java/run/run-java.sh" ]
