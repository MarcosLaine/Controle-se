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

# Verifica/baixa HikariCP e dependências se não existirem
if [ ! -f "lib/hikaricp.jar" ]; then
    echo ""
    echo "Baixando HikariCP Connection Pool..."
    HIKARICP_VERSION="5.1.0"
    HIKARICP_URL="https://repo1.maven.org/maven2/com/zaxxer/HikariCP/${HIKARICP_VERSION}/HikariCP-${HIKARICP_VERSION}.jar"
    
    if command -v wget &> /dev/null; then
        wget -q -O lib/hikaricp.jar "$HIKARICP_URL"
    elif command -v curl &> /dev/null; then
        curl -s -L -o lib/hikaricp.jar "$HIKARICP_URL"
    else
        echo "ERRO: wget ou curl não encontrado"
        echo "Baixe manualmente: $HIKARICP_URL"
        exit 1
    fi
    
    if [ $? -eq 0 ] && [ -f "lib/hikaricp.jar" ]; then
        echo "✓ HikariCP baixado (${HIKARICP_VERSION})"
    else
        echo "✗ Erro ao baixar HikariCP"
        exit 1
    fi
else
    echo "✓ HikariCP encontrado"
fi

# Verifica/baixa SLF4J API (dependência do HikariCP)
if [ ! -f "lib/slf4j-api.jar" ]; then
    echo ""
    echo "Baixando SLF4J API (dependência do HikariCP)..."
    SLF4J_VERSION="2.0.9"
    SLF4J_API_URL="https://repo1.maven.org/maven2/org/slf4j/slf4j-api/${SLF4J_VERSION}/slf4j-api-${SLF4J_VERSION}.jar"
    
    if command -v wget &> /dev/null; then
        wget -q -O lib/slf4j-api.jar "$SLF4J_API_URL"
    elif command -v curl &> /dev/null; then
        curl -s -L -o lib/slf4j-api.jar "$SLF4J_API_URL"
    fi
    
    if [ $? -eq 0 ] && [ -f "lib/slf4j-api.jar" ]; then
        echo "✓ SLF4J API baixado (${SLF4J_VERSION})"
    else
        echo "✗ Erro ao baixar SLF4J API"
        exit 1
    fi
else
    echo "✓ SLF4J API encontrado"
fi

# Verifica/baixa SLF4J Simple (implementação de logging)
if [ ! -f "lib/slf4j-simple.jar" ]; then
    echo ""
    echo "Baixando SLF4J Simple (implementação de logging)..."
    SLF4J_VERSION="2.0.9"
    SLF4J_SIMPLE_URL="https://repo1.maven.org/maven2/org/slf4j/slf4j-simple/${SLF4J_VERSION}/slf4j-simple-${SLF4J_VERSION}.jar"
    
    if command -v wget &> /dev/null; then
        wget -q -O lib/slf4j-simple.jar "$SLF4J_SIMPLE_URL"
    elif command -v curl &> /dev/null; then
        curl -s -L -o lib/slf4j-simple.jar "$SLF4J_SIMPLE_URL"
    fi
    
    if [ $? -eq 0 ] && [ -f "lib/slf4j-simple.jar" ]; then
        echo "✓ SLF4J Simple baixado (${SLF4J_VERSION})"
    else
        echo "✗ Erro ao baixar SLF4J Simple"
        exit 1
    fi
else
    echo "✓ SLF4J Simple encontrado"
fi

# Verifica/baixa Jakarta Validation API (Bean Validation)
if [ ! -f "lib/jakarta-validation-api.jar" ]; then
    echo ""
    echo "Baixando Jakarta Validation API (Bean Validation)..."
    VALIDATION_API_VERSION="3.1.1"
    VALIDATION_API_URL="https://repo1.maven.org/maven2/jakarta/validation/jakarta.validation-api/${VALIDATION_API_VERSION}/jakarta.validation-api-${VALIDATION_API_VERSION}.jar"
    
    if command -v wget &> /dev/null; then
        wget -q -O lib/jakarta-validation-api.jar "$VALIDATION_API_URL"
    elif command -v curl &> /dev/null; then
        curl -s -L -o lib/jakarta-validation-api.jar "$VALIDATION_API_URL"
    fi
    
    if [ $? -eq 0 ] && [ -f "lib/jakarta-validation-api.jar" ]; then
        echo "✓ Jakarta Validation API baixado (${VALIDATION_API_VERSION})"
    else
        echo "✗ Erro ao baixar Jakarta Validation API"
        exit 1
    fi
else
    echo "✓ Jakarta Validation API encontrado"
fi

# Verifica/baixa Hibernate Validator (implementação do Bean Validation)
if [ ! -f "lib/hibernate-validator.jar" ]; then
    echo ""
    echo "Baixando Hibernate Validator..."
    HIBERNATE_VALIDATOR_VERSION="8.0.1.Final"
    HIBERNATE_VALIDATOR_URL="https://repo1.maven.org/maven2/org/hibernate/validator/hibernate-validator/${HIBERNATE_VALIDATOR_VERSION}/hibernate-validator-${HIBERNATE_VALIDATOR_VERSION}.jar"
    
    if command -v wget &> /dev/null; then
        wget -q -O lib/hibernate-validator.jar "$HIBERNATE_VALIDATOR_URL"
    elif command -v curl &> /dev/null; then
        curl -s -L -o lib/hibernate-validator.jar "$HIBERNATE_VALIDATOR_URL"
    fi
    
    if [ $? -eq 0 ] && [ -f "lib/hibernate-validator.jar" ]; then
        echo "✓ Hibernate Validator baixado (${HIBERNATE_VALIDATOR_VERSION})"
    else
        echo "✗ Erro ao baixar Hibernate Validator"
        exit 1
    fi
else
    echo "✓ Hibernate Validator encontrado"
fi

# Verifica/baixa JBoss Logging (dependência do Hibernate Validator)
if [ ! -f "lib/jboss-logging.jar" ]; then
    echo ""
    echo "Baixando JBoss Logging..."
    JBOSS_LOGGING_VERSION="3.5.3.Final"
    JBOSS_LOGGING_URL="https://repo1.maven.org/maven2/org/jboss/logging/jboss-logging/${JBOSS_LOGGING_VERSION}/jboss-logging-${JBOSS_LOGGING_VERSION}.jar"
    
    if command -v wget &> /dev/null; then
        wget -q -O lib/jboss-logging.jar "$JBOSS_LOGGING_URL"
    elif command -v curl &> /dev/null; then
        curl -s -L -o lib/jboss-logging.jar "$JBOSS_LOGGING_URL"
    fi
    
    if [ $? -eq 0 ] && [ -f "lib/jboss-logging.jar" ]; then
        echo "✓ JBoss Logging baixado (${JBOSS_LOGGING_VERSION})"
    else
        echo "✗ Erro ao baixar JBoss Logging"
        exit 1
    fi
else
    echo "✓ JBoss Logging encontrado"
fi

# Verifica/baixa Classmate (dependência do Hibernate Validator)
if [ ! -f "lib/classmate.jar" ]; then
    echo ""
    echo "Baixando Classmate..."
    CLASSMATE_VERSION="1.5.1"
    CLASSMATE_URL="https://repo1.maven.org/maven2/com/fasterxml/classmate/${CLASSMATE_VERSION}/classmate-${CLASSMATE_VERSION}.jar"
    
    if command -v wget &> /dev/null; then
        wget -q -O lib/classmate.jar "$CLASSMATE_URL"
    elif command -v curl &> /dev/null; then
        curl -s -L -o lib/classmate.jar "$CLASSMATE_URL"
    fi
    
    if [ $? -eq 0 ] && [ -f "lib/classmate.jar" ]; then
        echo "✓ Classmate baixado (${CLASSMATE_VERSION})"
    else
        echo "✗ Erro ao baixar Classmate"
        exit 1
    fi
else
    echo "✓ Classmate encontrado"
fi

# Verifica/baixa Jakarta EL (Expression Language - dependência do Hibernate Validator)
if [ ! -f "lib/jakarta-el.jar" ]; then
    echo ""
    echo "Baixando Jakarta Expression Language..."
    EL_VERSION="5.0.1"
    EL_URL="https://repo1.maven.org/maven2/jakarta/el/jakarta.el-api/${EL_VERSION}/jakarta.el-api-${EL_VERSION}.jar"
    
    if command -v wget &> /dev/null; then
        wget -q -O lib/jakarta-el.jar "$EL_URL"
    elif command -v curl &> /dev/null; then
        curl -s -L -o lib/jakarta-el.jar "$EL_URL"
    fi
    
    if [ $? -eq 0 ] && [ -f "lib/jakarta-el.jar" ]; then
        echo "✓ Jakarta EL baixado (${EL_VERSION})"
    else
        echo "✗ Erro ao baixar Jakarta EL"
        exit 1
    fi
else
    echo "✓ Jakarta EL encontrado"
fi

# Verifica/baixa Jakarta EL Implementation (dependência do Hibernate Validator)
if [ ! -f "lib/jakarta-el-impl.jar" ]; then
    echo ""
    echo "Baixando Jakarta EL Implementation..."
    # Tenta versão 5.0.0 primeiro, se falhar tenta 4.0.1
    EL_IMPL_VERSION="5.0.0"
    EL_IMPL_URL="https://repo1.maven.org/maven2/org/glassfish/jakarta.el/${EL_IMPL_VERSION}/jakarta.el-${EL_IMPL_VERSION}.jar"
    EL_IMPL_FALLBACK_VERSION="4.0.1"
    EL_IMPL_FALLBACK_URL="https://repo1.maven.org/maven2/org/glassfish/jakarta.el/${EL_IMPL_FALLBACK_VERSION}/jakarta.el-${EL_IMPL_FALLBACK_VERSION}.jar"
    
    # Tenta baixar versão 5.0.0 primeiro
    if command -v wget &> /dev/null; then
        wget -q -O lib/jakarta-el-impl.jar "$EL_IMPL_URL" 2>/dev/null
    elif command -v curl &> /dev/null; then
        curl -s -L -o lib/jakarta-el-impl.jar "$EL_IMPL_URL" 2>/dev/null
    fi
    
    # Se falhou, tenta versão alternativa 4.0.1
    if [ ! -f "lib/jakarta-el-impl.jar" ] || [ ! -s "lib/jakarta-el-impl.jar" ]; then
        echo "⚠ Versão ${EL_IMPL_VERSION} não encontrada, tentando versão ${EL_IMPL_FALLBACK_VERSION}..."
        if command -v wget &> /dev/null; then
            wget -q -O lib/jakarta-el-impl.jar "$EL_IMPL_FALLBACK_URL"
        elif command -v curl &> /dev/null; then
            curl -s -L -o lib/jakarta-el-impl.jar "$EL_IMPL_FALLBACK_URL"
        fi
    fi
    
    if [ $? -eq 0 ] && [ -f "lib/jakarta-el-impl.jar" ] && [ -s "lib/jakarta-el-impl.jar" ]; then
        if [ -f "lib/jakarta-el-impl.jar" ] && [ $(stat -f%z "lib/jakarta-el-impl.jar" 2>/dev/null || stat -c%s "lib/jakarta-el-impl.jar" 2>/dev/null || echo 0) -gt 0 ]; then
            echo "✓ Jakarta EL Implementation baixado"
        else
            echo "✗ Erro ao baixar Jakarta EL Implementation (arquivo vazio)"
            exit 1
        fi
    else
        echo "✗ Erro ao baixar Jakarta EL Implementation"
        echo "  Tentou: $EL_IMPL_URL"
        echo "  Tentou: $EL_IMPL_FALLBACK_URL"
        echo "  Baixe manualmente uma das URLs acima"
        exit 1
    fi
else
    echo "✓ Jakarta EL Implementation encontrado"
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
javac -cp ".:lib/postgresql.jar:lib/hikaricp.jar:lib/slf4j-api.jar:lib/slf4j-simple.jar:lib/jakarta-validation-api.jar:lib/hibernate-validator.jar:lib/jboss-logging.jar:lib/classmate.jar:lib/jakarta-el.jar:lib/jakarta-el-impl.jar:bin" \
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
    # Apenas carrega se NÃO estiverem definidas no ambiente (prioridade para env vars do sistema/container)
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
java -cp ".:lib/postgresql.jar:lib/hikaricp.jar:lib/slf4j-api.jar:lib/slf4j-simple.jar:lib/jakarta-validation-api.jar:lib/hibernate-validator.jar:lib/jboss-logging.jar:lib/classmate.jar:lib/jakarta-el.jar:lib/jakarta-el-impl.jar:bin" \
     -Dfile.encoding=UTF-8 \
     ControleSeServer

