# ==========================================
# Stage 1: Build React Frontend
# ==========================================
FROM node:18-alpine AS frontend-build
WORKDIR /app/frontend

# Declara argumentos de build para variáveis de ambiente do Vite
ARG VITE_RECAPTCHA_SITE_KEY
ENV VITE_RECAPTCHA_SITE_KEY=$VITE_RECAPTCHA_SITE_KEY

# Copy package files
COPY frontend/package*.json ./

# Install dependencies
RUN npm ci

# Copy source code
COPY frontend/ ./

# Build frontend (vite config builds to ../dist relative to frontend dir)
RUN npm run build

# ==========================================
# Stage 2: Build Java Backend
# ==========================================
FROM eclipse-temurin:17-jdk AS backend-build
WORKDIR /app

# Download PostgreSQL JDBC Driver, HikariCP, SLF4J and Bean Validation dependencies
RUN mkdir -p lib && \
    curl -L -o lib/postgresql.jar https://repo1.maven.org/maven2/org/postgresql/postgresql/42.7.1/postgresql-42.7.1.jar && \
    curl -L -o lib/hikaricp.jar https://repo1.maven.org/maven2/com/zaxxer/HikariCP/5.1.0/HikariCP-5.1.0.jar && \
    curl -L -o lib/slf4j-api.jar https://repo1.maven.org/maven2/org/slf4j/slf4j-api/2.0.9/slf4j-api-2.0.9.jar && \
    curl -L -o lib/slf4j-simple.jar https://repo1.maven.org/maven2/org/slf4j/slf4j-simple/2.0.9/slf4j-simple-2.0.9.jar && \
    curl -L -o lib/jakarta-validation-api.jar https://repo1.maven.org/maven2/jakarta/validation/jakarta.validation-api/3.1.1/jakarta.validation-api-3.1.1.jar && \
    curl -L -o lib/hibernate-validator.jar https://repo1.maven.org/maven2/org/hibernate/validator/hibernate-validator/8.0.1.Final/hibernate-validator-8.0.1.Final.jar && \
    curl -L -o lib/jboss-logging.jar https://repo1.maven.org/maven2/org/jboss/logging/jboss-logging/3.5.3.Final/jboss-logging-3.5.3.Final.jar && \
    curl -L -o lib/classmate.jar https://repo1.maven.org/maven2/com/fasterxml/classmate/1.5.1/classmate-1.5.1.jar && \
    curl -L -o lib/jakarta-el.jar https://repo1.maven.org/maven2/jakarta/el/jakarta.el-api/5.0.1/jakarta.el-api-5.0.1.jar && \
    curl -L -o lib/jakarta-el-impl.jar https://repo1.maven.org/maven2/org/glassfish/jakarta.el/4.0.1/jakarta.el-4.0.1.jar || \
    curl -L -o lib/jakarta-el-impl.jar https://repo1.maven.org/maven2/org/glassfish/jakarta.el/5.0.0/jakarta.el-5.0.0.jar

# Copy Java source code
COPY src/ src/

# Create bin directory
RUN mkdir -p bin

# Compile Java Code
# Compile all Java files, javac will resolve dependencies automatically
RUN javac -cp "lib/postgresql.jar:lib/hikaricp.jar:lib/slf4j-api.jar:lib/jakarta-validation-api.jar:lib/hibernate-validator.jar:lib/jboss-logging.jar:lib/classmate.jar:lib/jakarta-el.jar:lib/jakarta-el-impl.jar" -d bin -sourcepath src $(find src -name "*.java")

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
# NOTE: vite config builds to ../dist relative to /app/frontend (WORKDIR), so output is at /app/dist
COPY --from=frontend-build /app/dist ./dist

# Expose the port
EXPOSE 8080

# Start the server
# Modificado para priorizar variáveis de ambiente do sistema, usando .env apenas se existir e variáveis não estiverem definidas
CMD ["sh", "-c", "if [ -f .env ]; then echo 'Carregando .env...'; export $(cat .env | grep -v '^#' | xargs); fi; java -cp bin:lib/postgresql.jar:lib/hikaricp.jar:lib/slf4j-api.jar:lib/slf4j-simple.jar:lib/jakarta-validation-api.jar:lib/hibernate-validator.jar:lib/jboss-logging.jar:lib/classmate.jar:lib/jakarta-el.jar:lib/jakarta-el-impl.jar ControleSeServer"]
