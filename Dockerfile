# Use Eclipse Temurin 17 (alternativa moderna ao OpenJDK)
FROM eclipse-temurin:17-jdk

# Define o diretório de trabalho
WORKDIR /app

# Copia os arquivos fonte
COPY src/ ./src/
COPY index.html ./
COPY styles.css ./
COPY app.js ./

# Cria o diretório de dados (será montado como volume persistente)
RUN mkdir -p /app/data

# Compila o projeto
RUN javac -d bin -cp src src/*.java src/server/handlers/*.java src/server/utils/*.java

# Expõe a porta 8080
EXPOSE 8080

# Comando para executar o servidor
CMD ["java", "-cp", "bin:src", "ControleSeServer"]

