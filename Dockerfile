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

# Build frontend (creates /app/frontend/dist)
RUN npm run build

# ==========================================
# Stage 2: Build Java Backend
# ==========================================
FROM eclipse-temurin:17-jdk AS backend-build
WORKDIR /app

# Download PostgreSQL JDBC Driver
RUN mkdir -p lib && \
    curl -L -o lib/postgresql.jar https://repo1.maven.org/maven2/org/postgresql/postgresql/42.7.1/postgresql-42.7.1.jar

# Copy Java source code
COPY src/ src/

# Create bin directory
RUN mkdir -p bin

# Compile Java Code
RUN javac -cp "src:lib/postgresql.jar" -d bin $(find src -name "*.java")

# ==========================================
# Stage 3: Runtime Environment
# ==========================================
FROM eclipse-temurin:17-jre
WORKDIR /app

# Create non-root user for security
RUN useradd -m -u 1000 appuser
USER appuser

# Copy compiled backend classes
COPY --from=backend-build /app/bin ./bin

# Copy library files (PostgreSQL driver)
COPY --from=backend-build /app/lib ./lib

# Copy built frontend static files to dist/
COPY --from=frontend-build /app/frontend/dist ./dist

# Expose the port
EXPOSE 8080

# Start the server
# We use a shell command to load .env variables if the file exists (Render Secret Files)
CMD ["sh", "-c", "if [ -f .env ]; then export $(cat .env | grep -v '^#' | xargs); fi; java -cp bin:lib/postgresql.jar ControleSeServer"]
