# ==========================================
# ESTÁGIO 1: Build (Ambiente de compilação com Maven e JDK)
# ==========================================
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app

# Copia primeiro o pom.xml e baixa as dependências: essa camada fica em cache enquanto o pom não mudar
COPY pom.xml .
RUN mvn -q dependency:go-offline

# Copia o código fonte e compila (testes rodam na etapa de CI, por isso -DskipTests aqui)
COPY src ./src
RUN mvn clean package -DskipTests

# ==========================================
# ESTÁGIO 2: Runtime (Imagem enxuta)
# ==========================================
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

# Copia apenas o .jar gerado no Estágio 1
COPY --from=build /app/target/*.jar app.jar

# Executa como usuário comum (sem privilégios de root)
RUN addgroup -S spring && adduser -S spring -G spring
USER spring

EXPOSE 8080

# JAVA_OPTS permite limitar o heap pelo compose (ex.: -Xmx512m) e deixar o pico dos testes de caos visível
ENV JAVA_OPTS=""
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
