# Giai đoạn 1: Build ứng dụng
FROM eclipse-temurin:21-jdk AS builder
WORKDIR /app
COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN chmod +x mvnw
RUN ./mvnw -B -DskipTests dependency:go-offline
COPY src src
RUN ./mvnw -B -DskipTests clean package

# Giai đoạn 2: Runtime siêu nhẹ JRE
FROM eclipse-temurin:21-jre
WORKDIR /app
RUN useradd --system --create-home spring
RUN mkdir -p /app/logs && chown -R spring:spring /app/logs /app
USER spring
COPY --from=builder --chown=spring:spring /app/target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]