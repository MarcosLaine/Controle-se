#!/bin/bash

# Script para rodar apenas o backend sem buildar o frontend

set -e

echo "=========================================="
echo "  CONTROLE-SE - Backend Only"
echo "=========================================="
echo ""

# Garante que o script está sendo executado do diretório correto
if [ ! -f "src/ControleSeServer.java" ]; then
    echo "ERRO: Execute este script do diretório raiz do projeto"
    exit 1
fi

# Cria diretórios necessários
mkdir -p bin lib logs

# Verifica Java
if ! command -v javac &> /dev/null; then
    echo "ERRO: Java não encontrado!"
    echo "Instale Java 11 ou superior"
    exit 1
fi

echo "✓ Java encontrado: $(javac -version 2>&1)"
echo ""

# Verifica se as dependências existem
if [ ! -f "lib/postgresql.jar" ] || [ ! -f "lib/hikaricp.jar" ] || [ ! -f "lib/slf4j-api.jar" ] || [ ! -f "lib/slf4j-simple.jar" ] || \
   [ ! -f "lib/jakarta-validation-api.jar" ] || [ ! -f "lib/hibernate-validator.jar" ] || [ ! -f "lib/jakarta-el.jar" ] || [ ! -f "lib/jakarta-el-impl.jar" ]; then
    echo "⚠ AVISO: Algumas dependências não foram encontradas."
    echo "Execute './start.sh' uma vez para baixar as dependências."
    exit 1
fi

echo "✓ Dependências encontradas"
echo ""

# Compila o projeto
echo "Compilando servidor Java..."

# Limpa compilações anteriores
rm -rf bin/*.class

# Encontra todos os arquivos Java (incluindo subdiretórios)
JAVA_FILES=$(find src -name "*.java" ! -name "BancoDados.java" ! -name "MigracaoDados.java")

if [ -z "$JAVA_FILES" ]; then
    echo "✗ ERRO: Nenhum arquivo Java encontrado para compilar"
    exit 1
fi

# Compila todos os arquivos de uma vez
javac -cp ".:lib/postgresql.jar:lib/hikaricp.jar:lib/slf4j-api.jar:lib/slf4j-simple.jar:lib/jakarta-validation-api.jar:lib/hibernate-validator.jar:lib/jakarta-el.jar:lib/jakarta-el-impl.jar:bin" \
      -d bin \
      -source 11 \
      -target 11 \
      -encoding UTF-8 \
      $JAVA_FILES

# Verifica se a compilação foi bem-sucedida
if [ $? -eq 0 ]; then
    echo "✓ Compilação do servidor concluída com sucesso"
    
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

# Carrega variáveis de ambiente se existirem
if [ -f .env ]; then
    echo "Carregando variáveis de ambiente de .env..."
    export $(cat .env | grep -v '^#' | xargs)
elif [ -f db.properties ]; then
    echo "Carregando configuração de db.properties (fallback)..."
    [ -z "$PGHOST" ] && export PGHOST=$(grep "^db.host=" db.properties | cut -d'=' -f2)
    [ -z "$PGPORT" ] && export PGPORT=$(grep "^db.port=" db.properties | cut -d'=' -f2)
    [ -z "$PGDATABASE" ] && export PGDATABASE=$(grep "^db.database=" db.properties | cut -d'=' -f2)
    [ -z "$PGUSER" ] && export PGUSER=$(grep "^db.username=" db.properties | cut -d'=' -f2)
    [ -z "$PGPASSWORD" ] && export PGPASSWORD=$(grep "^db.password=" db.properties | cut -d'=' -f2)
fi

echo ""
echo "=========================================="
echo "  Servidor iniciando..."
echo "=========================================="
echo ""

# Executa o servidor
java -cp ".:lib/postgresql.jar:lib/hikaricp.jar:lib/slf4j-api.jar:lib/slf4j-simple.jar:lib/jakarta-validation-api.jar:lib/hibernate-validator.jar:lib/jakarta-el.jar:lib/jakarta-el-impl.jar:bin" \
     -Dfile.encoding=UTF-8 \
     ControleSeServer


