#!/bin/bash

# Script de build para produção
# Otimizado e preparado para deploy

set -e  # Para na primeira erro

# Garante que o script está sendo executado do diretório correto
if [ ! -f "src/ControleSeServer.java" ]; then
    echo "ERRO: Execute este script do diretório raiz do projeto"
    exit 1
fi

echo "=== Build para Produção - Controle-se ==="
echo ""

# Cria diretórios necessários
mkdir -p lib bin logs

# Verifica Java
if ! command -v javac &> /dev/null; then
    echo "ERRO: Java não encontrado!"
    echo "Instale Java 11 ou superior"
    exit 1
fi

JAVA_VERSION=$(javac -version 2>&1 | awk -F '"' '{print $2}' | cut -d'.' -f1)
if [ -z "$JAVA_VERSION" ] || [ "$JAVA_VERSION" -lt 11 ] 2>/dev/null; then
    echo "AVISO: Não foi possível verificar versão do Java, continuando..."
else
    if [ "$JAVA_VERSION" -lt 11 ]; then
        echo "ERRO: Java 11 ou superior necessário. Versão atual: $JAVA_VERSION"
        exit 1
    fi
fi

echo "✓ Java encontrado: $(javac -version 2>&1)"

# Baixa driver PostgreSQL se não existir
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
    echo "✓ Driver PostgreSQL já existe"
fi

# Limpa compilações anteriores
echo ""
echo "Limpando compilações anteriores..."
rm -rf bin/*.class
echo "✓ Limpeza concluída"

# Compila o projeto (exclui BancoDados.java que usa classes antigas)
echo ""
echo "Compilando projeto..."

# Encontra todos os arquivos Java (incluindo subdiretórios)
JAVA_FILES=$(find src -name "*.java" ! -name "BancoDados.java" ! -name "MigracaoDados.java")

if [ -z "$JAVA_FILES" ]; then
    echo "✗ ERRO: Nenhum arquivo Java encontrado para compilar"
    exit 1
fi

# Compila todos os arquivos de uma vez (melhor para dependências)
javac -cp ".:lib/postgresql.jar:bin" \
      -d bin \
      -source 11 \
      -target 11 \
      -encoding UTF-8 \
      -Xlint:unchecked \
      -Xlint:deprecation \
      $JAVA_FILES

if [ $? -eq 0 ]; then
    echo "✓ Compilação concluída com sucesso"
    
    # Conta arquivos compilados
    CLASS_COUNT=$(find bin -name "*.class" 2>/dev/null | wc -l | tr -d ' ')
    echo "  Arquivos compilados: $CLASS_COUNT"
    
    # Verifica se a classe principal foi compilada
    if [ ! -f "bin/ControleSeServer.class" ]; then
        echo "✗ ERRO: Classe principal ControleSeServer.class não encontrada após compilação"
        exit 1
    fi
    
    echo "✓ Classe principal encontrada: ControleSeServer.class"
    
    # Verifica se há dependências críticas
    if [ ! -f "bin/BancoDadosPostgreSQL.class" ]; then
        echo "⚠ AVISO: BancoDadosPostgreSQL.class não encontrado"
    fi
    
    echo ""
    echo "=== Build Concluído ==="
    echo ""
    echo "Para executar em desenvolvimento:"
    echo "  java -cp \".:lib/postgresql.jar:bin\" ControleSeServer"
    echo ""
    echo "Para produção, use:"
    echo "  ./start_producao.sh"
    echo ""
else
    echo "✗ Erro na compilação"
    exit 1
fi

