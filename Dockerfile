# ==========================================
# Stage 1: Build React Frontend
# ==========================================
FROM node:18-alpine AS frontend-build
WORKDIR /app/frontend

# Copy package files
COPY frontend/package*.json ./

# Install dependencies
RUN npm ci

# Copy source code
COPY frontend/ ./

# Build frontend (creates /app/dist because of vite config)
RUN npm run build

# ==========================================
# Stage 2: Build Java Backend
# ==========================================
FROM eclipse-temurin:17-jdk AS backend-build
WORKDIR /app

# Download PostgreSQL JDBC Driver, HikariCP and SLF4J (required by HikariCP)
RUN mkdir -p lib && \
    curl -L -o lib/postgresql.jar https://repo1.maven.org/maven2/org/postgresql/postgresql/42.7.1/postgresql-42.7.1.jar && \
    curl -L -o lib/hikaricp.jar https://repo1.maven.org/maven2/com/zaxxer/HikariCP/5.1.0/HikariCP-5.1.0.jar && \
    curl -L -o lib/slf4j-api.jar https://repo1.maven.org/maven2/org/slf4j/slf4j-api/2.0.9/slf4j-api-2.0.9.jar && \
    curl -L -o lib/slf4j-simple.jar https://repo1.maven.org/maven2/org/slf4j/slf4j-simple/2.0.9/slf4j-simple-2.0.9.jar

# Copy Java source code
COPY src/ src/

# Create bin directory
RUN mkdir -p bin

# Compile Java Code
RUN javac -cp "src:lib/postgresql.jar:lib/hikaricp.jar:lib/slf4j-api.jar" -d bin $(find src -name "*.java")

# ==========================================
# Stage 3: Runtime Environment
# ==========================================
FROM eclipse-temurin:17-jre
WORKDIR /app

# Create non-root user for security
RUN useradd -m -u 1001 appuser
USER appuser

# Copy compiled backend classes
COPY --from=backend-build /app/bin ./bin

# Copy library files (PostgreSQL driver)
COPY --from=backend-build /app/lib ./lib

# Copy built frontend static files to dist/
# NOTE: vite config builds to ../dist relative to frontend dir, so it is at /app/dist
COPY --from=frontend-build /app/dist ./dist

# Expose the port
EXPOSE 8080

# Start the server
# Modificado para priorizar variáveis de ambiente do sistema, usando .env apenas se existir e variáveis não estiverem definidas
CMD ["sh", "-c", "if [ -f .env ]; then echo 'Carregando .env...'; export $(cat .env | grep -v '^#' | xargs); fi; java -cp bin:lib/postgresql.jar:lib/hikaricp.jar:lib/slf4j-api.jar:lib/slf4j-simple.jar ControleSeServer"]
