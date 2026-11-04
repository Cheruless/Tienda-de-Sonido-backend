# ---- Etapa 1: compilar el JAR ----
# Maven 3.9 + JDK 21. La imagen trae Maven, asi que no hace falta el wrapper.
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app

# Primero el pom: permite cachear la capa de dependencias aunque cambie el codigo.
COPY pom.xml .
RUN mvn -B dependency:go-offline

# Despues el codigo fuente y el empaquetado.
COPY src ./src
RUN mvn -B -DskipTests package

# ---- Etapa 2: imagen final con solo el JRE ----
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar

EXPOSE 8083
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
