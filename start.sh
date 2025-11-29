#!/bin/bash

# Script de inicialização completo
# Builda o servidor Java, o frontend React e inicia o projeto

set -e

echo "=========================================="
echo "  CONTROLE-SE - Build e Inicialização"
echo "=========================================="
echo ""

# Garante que o script está sendo executado do diretório correto
if [ ! -f "src/ControleSeServer.java" ]; then
    echo "ERRO: Execute este script do diretório raiz do projeto"
    exit 1
fi

# ===== PARTE 1: BUILD DO FRONTEND REACT =====
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "  [1/3] Build do Frontend React"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo ""

if [ ! -d "frontend" ]; then
    echo "⚠ AVISO: Diretório frontend não encontrado. Pulando build do frontend."
    echo ""
else
    # Verifica Node.js
    if ! command -v node &> /dev/null; then
        echo "⚠ AVISO: Node.js não encontrado. Frontend não será buildado."
        echo "  Instale Node.js 18 ou superior para buildar o frontend."
        echo ""
    else
        echo "✓ Node.js encontrado: $(node --version)"
        echo "✓ npm encontrado: $(npm --version)"
        echo ""
        
        cd frontend
        
        # Instala dependências se necessário
        if [ ! -d "node_modules" ]; then
            echo "Instalando dependências do frontend..."
            npm install
            echo "✓ Dependências instaladas"
            echo ""
        fi
        
        # Faz o build
        echo "Fazendo build do frontend React..."
        npm run build
        
        if [ $? -eq 0 ]; then
            echo "✓ Build do frontend concluído"
            echo "  Arquivos gerados em: ../dist"
        else
            echo "✗ Erro no build do frontend"
            exit 1
        fi
        
        cd ..
        echo ""
    fi
fi

# ===== PARTE 2: BUILD DO SERVIDOR JAVA =====
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "  [2/3] Build do Servidor Java"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo ""

# Cria diretórios necessários
mkdir -p bin lib logs

# Verifica Java
if ! command -v javac &> /dev/null; then
    echo "ERRO: Java não encontrado!"
    echo "Instale Java 11 ou superior"
    exit 1
fi

echo "✓ Java encontrado: $(javac -version 2>&1)"

# Verifica/baixa driver PostgreSQL se não existir
if [ ! -f "lib/postgresql.jar" ]; then
    echo ""
    echo "Baixando driver PostgreSQL JDBC..."
    POSTGRESQL_VERSION="42.7.1"
    POSTGRESQL_URL="https://repo1.maven.org/maven2/org/postgresql/postgresql/${POSTGRESQL_VERSION}/postgresql-${POSTGRESQL_VERSION}.jar"
    
    if command -v wget &> /dev/null; then
        wget -q -O lib/postgresql.jar "$POSTGRESQL_URL"
    elif command -v curl &> /dev/null; then
        curl -s -L -o lib/postgresql.jar "$POSTGRESQL_URL"
    else
        echo "ERRO: wget ou curl não encontrado"
        echo "Baixe manualmente: $POSTGRESQL_URL"
        exit 1
    fi
    
    if [ $? -eq 0 ] && [ -f "lib/postgresql.jar" ]; then
        echo "✓ Driver PostgreSQL baixado (${POSTGRESQL_VERSION})"
    else
        echo "✗ Erro ao baixar driver"
        exit 1
    fi
else
    echo "✓ Driver PostgreSQL encontrado"
fi

# Limpa compilações anteriores
echo ""
echo "Limpando compilações anteriores..."
rm -rf bin/*.class
echo "✓ Limpeza concluída"

# Compila o projeto
echo ""
echo "Compilando servidor Java..."

# Encontra todos os arquivos Java (incluindo subdiretórios)
JAVA_FILES=$(find src -name "*.java" ! -name "BancoDados.java" ! -name "MigracaoDados.java")

if [ -z "$JAVA_FILES" ]; then
    echo "✗ ERRO: Nenhum arquivo Java encontrado para compilar"
    exit 1
fi

# Compila todos os arquivos de uma vez
javac -cp ".:lib/postgresql.jar:bin" \
      -d bin \
      -source 11 \
      -target 11 \
      -encoding UTF-8 \
      $JAVA_FILES

# Verifica se a compilação foi bem-sucedida
if [ $? -eq 0 ]; then
    echo "✓ Compilação do servidor concluída com sucesso"
    
    # Conta arquivos compilados
    CLASS_COUNT=$(find bin -name "*.class" 2>/dev/null | wc -l | tr -d ' ')
    echo "  Arquivos compilados: $CLASS_COUNT"
    
    # Verifica se a classe principal foi compilada
    if [ ! -f "bin/ControleSeServer.class" ]; then
        echo "✗ ERRO: Classe principal ControleSeServer.class não encontrada após compilação"
        exit 1
    fi
    
    echo "✓ Classe principal encontrada: ControleSeServer.class"
else
    echo "✗ Erro na compilação do servidor"
    exit 1
fi

echo ""

# ===== PARTE 3: INICIAR SERVIDOR =====
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "  [3/3] Iniciando Servidor"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo ""

# Carrega variáveis de ambiente se existirem
if [ -f .env ]; then
    echo "Carregando variáveis de ambiente de .env..."
    export $(cat .env | grep -v '^#' | xargs)
elif [ -f db.properties ]; then
    echo "Carregando configuração de db.properties..."
    export PGHOST=$(grep "^db.host=" db.properties | cut -d'=' -f2)
    export PGPORT=$(grep "^db.port=" db.properties | cut -d'=' -f2)
    export PGDATABASE=$(grep "^db.database=" db.properties | cut -d'=' -f2)
    export PGUSER=$(grep "^db.username=" db.properties | cut -d'=' -f2)
    export PGPASSWORD=$(grep "^db.password=" db.properties | cut -d'=' -f2)
fi

echo ""
echo "=========================================="
echo "  Servidor iniciando..."
echo "=========================================="
echo ""

# Executa o servidor
java -cp ".:lib/postgresql.jar:bin" \
     -Dfile.encoding=UTF-8 \
     ControleSeServer

