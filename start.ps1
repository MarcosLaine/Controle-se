# Script de inicialização completo
# Builda o servidor Java, o frontend React e inicia o projeto

$ErrorActionPreference = "Stop"

Write-Host "=========================================="
Write-Host "  CONTROLE-SE - Build e Inicialização"
Write-Host "=========================================="
Write-Host ""

# Garante que o script está sendo executado do diretório correto
if (-not (Test-Path "src/ControleSeServer.java")) {
    Write-Host "ERRO: Execute este script do diretório raiz do projeto" -ForegroundColor Red
    exit 1
}

# ===== PARTE 1: BUILD DO FRONTEND REACT =====
Write-Host "=========================================="
Write-Host "  [1/3] Build do Frontend React"
Write-Host "=========================================="
Write-Host ""

if (-not (Test-Path "frontend")) {
    Write-Host "[AVISO] Diretorio frontend nao encontrado. Pulando build do frontend." -ForegroundColor Yellow
    Write-Host ""
}
else {
    # Verifica Node.js
    $nodeCmd = Get-Command node -ErrorAction SilentlyContinue
    if (-not $nodeCmd) {
        Write-Host "[AVISO] Node.js nao encontrado. Frontend nao sera buildado." -ForegroundColor Yellow
        Write-Host "  Instale Node.js 18 ou superior para buildar o frontend."
        Write-Host ""
    }
    else {
        $nodeVersion = node --version
        $npmVersion = npm --version
        Write-Host "[OK] Node.js encontrado: $nodeVersion" -ForegroundColor Green
        Write-Host "[OK] npm encontrado: $npmVersion" -ForegroundColor Green
        Write-Host ""
        
        Push-Location frontend
        
        # Instala/atualiza dependencias
        Write-Host "Instalando dependencias do frontend..."
        npm install
        if ($LASTEXITCODE -ne 0) {
            Write-Host "[ERRO] Erro ao instalar dependencias" -ForegroundColor Red
            Pop-Location
            exit 1
        }
        Write-Host "[OK] Dependencias instaladas" -ForegroundColor Green
        Write-Host ""
        
        # Faz o build
        Write-Host "Fazendo build do frontend React..."
        npm run build
        
        if ($LASTEXITCODE -eq 0) {
            Write-Host "[OK] Build do frontend concluido" -ForegroundColor Green
            Write-Host "  Arquivos gerados em: ../dist"
        }
        else {
            Write-Host "[ERRO] Erro no build do frontend" -ForegroundColor Red
            Pop-Location
            exit 1
        }
        
        Pop-Location
        Write-Host ""
    }
}

# ===== PARTE 2: BUILD DO SERVIDOR JAVA =====
Write-Host "=========================================="
Write-Host "  [2/3] Build do Servidor Java"
Write-Host "=========================================="
Write-Host ""

# Cria diretórios necessários
New-Item -ItemType Directory -Force -Path "bin", "lib", "logs" | Out-Null

# Verifica Java
$javacCmd = Get-Command javac -ErrorAction SilentlyContinue
if (-not $javacCmd) {
    Write-Host "ERRO: Java não encontrado!" -ForegroundColor Red
    Write-Host "Instale Java 11 ou superior"
    exit 1
}

$javaVersion = javac -version 2>&1
Write-Host "[OK] Java encontrado: $javaVersion" -ForegroundColor Green

# Verifica/baixa driver PostgreSQL se nao existir
$postgresqlJarPath = "lib/postgresql.jar"
$needsDownload = -not (Test-Path $postgresqlJarPath)

if ($needsDownload) {
    Write-Host ""
    Write-Host "Baixando driver PostgreSQL JDBC..."
    $POSTGRESQL_VERSION = "42.7.1"
    $POSTGRESQL_URL = "https://repo1.maven.org/maven2/org/postgresql/postgresql/$POSTGRESQL_VERSION/postgresql-$POSTGRESQL_VERSION.jar"
    
    $oldErrorAction = $ErrorActionPreference
    $ErrorActionPreference = "SilentlyContinue"
    $result = Invoke-WebRequest -Uri $POSTGRESQL_URL -OutFile $postgresqlJarPath -UseBasicParsing
    $ErrorActionPreference = $oldErrorAction
    
    if (($result -ne $null) -and (Test-Path $postgresqlJarPath)) {
        Write-Host "[OK] Driver PostgreSQL baixado ($POSTGRESQL_VERSION)" -ForegroundColor Green
    }
    else {
        Write-Host "ERRO: Falha ao baixar driver PostgreSQL" -ForegroundColor Red
        Write-Host "Baixe manualmente: $POSTGRESQL_URL"
        exit 1
    }
}

if (-not $needsDownload) {
    Write-Host "[OK] Driver PostgreSQL encontrado" -ForegroundColor Green
}

# Verifica/baixa HikariCP se nao existir
$hikariJarPath = "lib/hikaricp.jar"
$needsHikariDownload = -not (Test-Path $hikariJarPath)

if ($needsHikariDownload) {
    Write-Host ""
    Write-Host "Baixando HikariCP (Connection Pool)..."
    $HIKARI_VERSION = "5.1.0"
    $HIKARI_URL = "https://repo1.maven.org/maven2/com/zaxxer/HikariCP/$HIKARI_VERSION/HikariCP-$HIKARI_VERSION.jar"
    
    $oldErrorAction = $ErrorActionPreference
    $ErrorActionPreference = "SilentlyContinue"
    $result = Invoke-WebRequest -Uri $HIKARI_URL -OutFile $hikariJarPath -UseBasicParsing
    $ErrorActionPreference = $oldErrorAction
    
    if (($result -ne $null) -and (Test-Path $hikariJarPath)) {
        Write-Host "[OK] HikariCP baixado ($HIKARI_VERSION)" -ForegroundColor Green
    }
    else {
        Write-Host "ERRO: Falha ao baixar HikariCP" -ForegroundColor Red
        Write-Host "Baixe manualmente: $HIKARI_URL"
        exit 1
    }
}

if (-not $needsHikariDownload) {
    Write-Host "[OK] HikariCP encontrado" -ForegroundColor Green
}

# Verifica/baixa SLF4J API (dependência do HikariCP) se nao existir
$slf4jApiJarPath = "lib/slf4j-api.jar"
$needsSlf4jDownload = -not (Test-Path $slf4jApiJarPath)

if ($needsSlf4jDownload) {
    Write-Host ""
    Write-Host "Baixando SLF4J API (dependência do HikariCP)..."
    $SLF4J_VERSION = "2.0.9"
    $SLF4J_API_URL = "https://repo1.maven.org/maven2/org/slf4j/slf4j-api/$SLF4J_VERSION/slf4j-api-$SLF4J_VERSION.jar"
    
    $oldErrorAction = $ErrorActionPreference
    $ErrorActionPreference = "SilentlyContinue"
    $result = Invoke-WebRequest -Uri $SLF4J_API_URL -OutFile $slf4jApiJarPath -UseBasicParsing
    $ErrorActionPreference = $oldErrorAction
    
    if (($result -ne $null) -and (Test-Path $slf4jApiJarPath)) {
        Write-Host "[OK] SLF4J API baixado ($SLF4J_VERSION)" -ForegroundColor Green
    }
    else {
        Write-Host "[AVISO] Falha ao baixar SLF4J API (HikariCP pode funcionar sem ele)" -ForegroundColor Yellow
    }
}

if (-not $needsSlf4jDownload) {
    Write-Host "[OK] SLF4J API encontrado" -ForegroundColor Green
}

# Verifica/baixa SLF4J Simple (implementação de logging) se nao existir
$slf4jSimpleJarPath = "lib/slf4j-simple.jar"
$needsSlf4jSimpleDownload = -not (Test-Path $slf4jSimpleJarPath)

if ($needsSlf4jSimpleDownload) {
    Write-Host ""
    Write-Host "Baixando SLF4J Simple (implementação de logging)..."
    $SLF4J_SIMPLE_VERSION = "2.0.9"
    $SLF4J_SIMPLE_URL = "https://repo1.maven.org/maven2/org/slf4j/slf4j-simple/$SLF4J_SIMPLE_VERSION/slf4j-simple-$SLF4J_SIMPLE_VERSION.jar"
    
    $oldErrorAction = $ErrorActionPreference
    $ErrorActionPreference = "SilentlyContinue"
    $result = Invoke-WebRequest -Uri $SLF4J_SIMPLE_URL -OutFile $slf4jSimpleJarPath -UseBasicParsing
    $ErrorActionPreference = $oldErrorAction
    
    if (($result -ne $null) -and (Test-Path $slf4jSimpleJarPath)) {
        Write-Host "[OK] SLF4J Simple baixado ($SLF4J_SIMPLE_VERSION)" -ForegroundColor Green
    }
    else {
        Write-Host "[AVISO] Falha ao baixar SLF4J Simple (logs do HikariCP não serão exibidos)" -ForegroundColor Yellow
    }
}

if (-not $needsSlf4jSimpleDownload) {
    Write-Host "[OK] SLF4J Simple encontrado" -ForegroundColor Green
}

# Verifica/baixa Jakarta Validation API (Bean Validation) se não existir
$validationApiJarPath = "lib/jakarta-validation-api.jar"
$needsValidationApiDownload = -not (Test-Path $validationApiJarPath)

if ($needsValidationApiDownload) {
    Write-Host ""
    Write-Host "Baixando Jakarta Validation API (Bean Validation)..."
    $VALIDATION_API_VERSION = "3.1.1"
    $VALIDATION_API_URL = "https://repo1.maven.org/maven2/jakarta/validation/jakarta.validation-api/$VALIDATION_API_VERSION/jakarta.validation-api-$VALIDATION_API_VERSION.jar"
    
    try {
        $oldErrorAction = $ErrorActionPreference
        $ErrorActionPreference = "Stop"
        Invoke-WebRequest -Uri $VALIDATION_API_URL -OutFile $validationApiJarPath -UseBasicParsing
        $ErrorActionPreference = $oldErrorAction
        
        if (Test-Path $validationApiJarPath) {
            Write-Host "[OK] Jakarta Validation API baixado ($VALIDATION_API_VERSION)" -ForegroundColor Green
        }
        else {
            Write-Host "ERRO: Falha ao baixar Jakarta Validation API" -ForegroundColor Red
            Write-Host "Baixe manualmente: $VALIDATION_API_URL"
            exit 1
        }
    }
    catch {
        Write-Host "ERRO: Falha ao baixar Jakarta Validation API" -ForegroundColor Red
        Write-Host "Erro: $($_.Exception.Message)" -ForegroundColor Red
        Write-Host "Baixe manualmente: $VALIDATION_API_URL"
        exit 1
    }
}

if (-not $needsValidationApiDownload) {
    Write-Host "[OK] Jakarta Validation API encontrado" -ForegroundColor Green
}

# Verifica/baixa Hibernate Validator (implementação do Bean Validation) se não existir
$hibernateValidatorJarPath = "lib/hibernate-validator.jar"
$needsHibernateValidatorDownload = -not (Test-Path $hibernateValidatorJarPath)

if ($needsHibernateValidatorDownload) {
    Write-Host ""
    Write-Host "Baixando Hibernate Validator..."
    $HIBERNATE_VALIDATOR_VERSION = "8.0.1.Final"
    $HIBERNATE_VALIDATOR_URL = "https://repo1.maven.org/maven2/org/hibernate/validator/hibernate-validator/$HIBERNATE_VALIDATOR_VERSION/hibernate-validator-$HIBERNATE_VALIDATOR_VERSION.jar"
    
    try {
        $oldErrorAction = $ErrorActionPreference
        $ErrorActionPreference = "Stop"
        Invoke-WebRequest -Uri $HIBERNATE_VALIDATOR_URL -OutFile $hibernateValidatorJarPath -UseBasicParsing
        $ErrorActionPreference = $oldErrorAction
        
        if (Test-Path $hibernateValidatorJarPath) {
            Write-Host "[OK] Hibernate Validator baixado ($HIBERNATE_VALIDATOR_VERSION)" -ForegroundColor Green
        }
        else {
            Write-Host "ERRO: Falha ao baixar Hibernate Validator" -ForegroundColor Red
            Write-Host "Baixe manualmente: $HIBERNATE_VALIDATOR_URL"
            exit 1
        }
    }
    catch {
        Write-Host "ERRO: Falha ao baixar Hibernate Validator" -ForegroundColor Red
        Write-Host "Erro: $($_.Exception.Message)" -ForegroundColor Red
        Write-Host "Baixe manualmente: $HIBERNATE_VALIDATOR_URL"
        exit 1
    }
}

if (-not $needsHibernateValidatorDownload) {
    Write-Host "[OK] Hibernate Validator encontrado" -ForegroundColor Green
}

# Verifica/baixa Jakarta EL (Expression Language - dependência do Hibernate Validator) se não existir
$jakartaElJarPath = "lib/jakarta-el.jar"
$needsJakartaElDownload = -not (Test-Path $jakartaElJarPath)

if ($needsJakartaElDownload) {
    Write-Host ""
    Write-Host "Baixando Jakarta Expression Language..."
    $EL_VERSION = "5.0.1"
    $EL_URL = "https://repo1.maven.org/maven2/jakarta/el/jakarta.el-api/$EL_VERSION/jakarta.el-api-$EL_VERSION.jar"
    
    try {
        $oldErrorAction = $ErrorActionPreference
        $ErrorActionPreference = "Stop"
        Invoke-WebRequest -Uri $EL_URL -OutFile $jakartaElJarPath -UseBasicParsing
        $ErrorActionPreference = $oldErrorAction
        
        if (Test-Path $jakartaElJarPath) {
            Write-Host "[OK] Jakarta EL baixado ($EL_VERSION)" -ForegroundColor Green
        }
        else {
            Write-Host "ERRO: Falha ao baixar Jakarta EL" -ForegroundColor Red
            Write-Host "Baixe manualmente: $EL_URL"
            exit 1
        }
    }
    catch {
        Write-Host "ERRO: Falha ao baixar Jakarta EL" -ForegroundColor Red
        Write-Host "Erro: $($_.Exception.Message)" -ForegroundColor Red
        Write-Host "Baixe manualmente: $EL_URL"
        exit 1
    }
}

if (-not $needsJakartaElDownload) {
    Write-Host "[OK] Jakarta EL encontrado" -ForegroundColor Green
}

# Verifica/baixa Jakarta EL Implementation (dependência do Hibernate Validator) se não existir
$jakartaElImplJarPath = "lib/jakarta-el-impl.jar"
$needsJakartaElImplDownload = -not (Test-Path $jakartaElImplJarPath)

if ($needsJakartaElImplDownload) {
    Write-Host ""
    Write-Host "Baixando Jakarta EL Implementation..."
    # Tenta versão 5.0.0 primeiro, se falhar tenta 4.0.1
    $EL_IMPL_VERSION = "5.0.0"
    $EL_IMPL_URL = "https://repo1.maven.org/maven2/org/glassfish/jakarta.el/$EL_IMPL_VERSION/jakarta.el-$EL_IMPL_VERSION.jar"
    $EL_IMPL_FALLBACK_VERSION = "4.0.1"
    $EL_IMPL_FALLBACK_URL = "https://repo1.maven.org/maven2/org/glassfish/jakarta.el/$EL_IMPL_FALLBACK_VERSION/jakarta.el-$EL_IMPL_FALLBACK_VERSION.jar"
    
    try {
        $oldErrorAction = $ErrorActionPreference
        $ErrorActionPreference = "Stop"
        
        # Tenta baixar versão 5.0.0 primeiro
        try {
            Invoke-WebRequest -Uri $EL_IMPL_URL -OutFile $jakartaElImplJarPath -UseBasicParsing
            if (Test-Path $jakartaElImplJarPath) {
                Write-Host "[OK] Jakarta EL Implementation baixado ($EL_IMPL_VERSION)" -ForegroundColor Green
            }
        }
        catch {
            Write-Host "[AVISO] Versão $EL_IMPL_VERSION não encontrada, tentando versão $EL_IMPL_FALLBACK_VERSION..." -ForegroundColor Yellow
            # Tenta versão alternativa 4.0.1
            Invoke-WebRequest -Uri $EL_IMPL_FALLBACK_URL -OutFile $jakartaElImplJarPath -UseBasicParsing
            if (Test-Path $jakartaElImplJarPath) {
                Write-Host "[OK] Jakarta EL Implementation baixado ($EL_IMPL_FALLBACK_VERSION)" -ForegroundColor Green
            }
            else {
                throw "Falha ao baixar ambas as versões"
            }
        }
        
        $ErrorActionPreference = $oldErrorAction
    }
    catch {
        Write-Host "ERRO: Falha ao baixar Jakarta EL Implementation" -ForegroundColor Red
        Write-Host "Erro: $($_.Exception.Message)" -ForegroundColor Red
        Write-Host "Tentou: $EL_IMPL_URL" -ForegroundColor Yellow
        Write-Host "Tentou: $EL_IMPL_FALLBACK_URL" -ForegroundColor Yellow
        Write-Host "Baixe manualmente uma das URLs acima e coloque em: $jakartaElImplJarPath" -ForegroundColor Yellow
        exit 1
    }
}

if (-not $needsJakartaElImplDownload) {
    Write-Host "[OK] Jakarta EL Implementation encontrado" -ForegroundColor Green
}

# Limpa compilações anteriores
Write-Host ""
Write-Host "Limpando compilações anteriores..."
Get-ChildItem -Path "bin" -Filter "*.class" -Recurse -ErrorAction SilentlyContinue | Remove-Item -Force
Write-Host "[OK] Limpeza concluida" -ForegroundColor Green

# Compila o projeto
Write-Host ""
Write-Host "Compilando servidor Java..."

# Encontra todos os arquivos Java (incluindo subdiretórios)
$JAVA_FILES = Get-ChildItem -Path "src" -Filter "*.java" -Recurse | 
    Where-Object { $_.Name -ne "BancoDados.java" -and $_.Name -ne "MigracaoDados.java" } |
    ForEach-Object { $_.FullName }

if ($JAVA_FILES.Count -eq 0) {
    Write-Host "[ERRO] Nenhum arquivo Java encontrado para compilar" -ForegroundColor Red
    exit 1
}

# Compila todos os arquivos de uma vez

# Usa caminhos absolutos para garantir que o javac encontre os JARs
$pwd = (Get-Location).Path
$classpath = "$pwd;$pwd\lib\postgresql.jar;$pwd\lib\hikaricp.jar"
if (Test-Path "lib/slf4j-api.jar") {
    $classpath += ";$pwd\lib\slf4j-api.jar"
}
if (Test-Path "lib/slf4j-simple.jar") {
    $classpath += ";$pwd\lib\slf4j-simple.jar"
}
if (Test-Path "lib/jakarta-validation-api.jar") {
    $classpath += ";$pwd\lib\jakarta-validation-api.jar"
}
if (Test-Path "lib/hibernate-validator.jar") {
    $classpath += ";$pwd\lib\hibernate-validator.jar"
}
if (Test-Path "lib/jakarta-el.jar") {
    $classpath += ";$pwd\lib\jakarta-el.jar"
}
if (Test-Path "lib/jakarta-el-impl.jar") {
    $classpath += ";$pwd\lib\jakarta-el-impl.jar"
}
$classpath += ";$pwd\bin"
& javac -cp $classpath -d bin -source 11 -target 11 -encoding UTF-8 $JAVA_FILES

# Verifica se a compilação foi bem-sucedida
if ($LASTEXITCODE -eq 0) {
    Write-Host "[OK] Compilacao do servidor concluida com sucesso" -ForegroundColor Green
    
    # Conta arquivos compilados
    $CLASS_COUNT = (Get-ChildItem -Path "bin" -Filter "*.class" -Recurse -ErrorAction SilentlyContinue).Count
    Write-Host "  Arquivos compilados: $CLASS_COUNT"
    
    # Verifica se a classe principal foi compilada
    if (-not (Test-Path "bin/ControleSeServer.class")) {
        Write-Host "[ERRO] Classe principal ControleSeServer.class nao encontrada apos compilacao" -ForegroundColor Red
        exit 1
    }
    
    Write-Host "[OK] Classe principal encontrada: ControleSeServer.class" -ForegroundColor Green
} else {
    Write-Host "[ERRO] Erro na compilacao do servidor" -ForegroundColor Red
    exit 1
}

Write-Host ""

# ===== PARTE 3: INICIAR SERVIDOR =====
Write-Host "=========================================="
Write-Host "  [3/3] Iniciando Servidor"
Write-Host "=========================================="
Write-Host ""

# Carrega variáveis de ambiente se existirem
if (Test-Path ".env") {
    Write-Host "Carregando variáveis de ambiente de .env..."
    Get-Content ".env" | ForEach-Object {
        if ($_ -match '^\s*([^#][^=]+)=(.*)$') {
            $key = $matches[1].Trim()
            $value = $matches[2].Trim()
            [Environment]::SetEnvironmentVariable($key, $value, "Process")
        }
    }
} elseif (Test-Path "db.properties") {
    # Apenas carrega se NÃO estiverem definidas no ambiente (prioridade para env vars do sistema/container)
    Write-Host "Carregando configuração de db.properties (fallback)..."
    $props = Get-Content "db.properties"
    
    foreach ($line in $props) {
        if ($line -match '^db\.host=(.*)$') {
            if (-not $env:PGHOST) { $env:PGHOST = $matches[1] }
        } elseif ($line -match '^db\.port=(.*)$') {
            if (-not $env:PGPORT) { $env:PGPORT = $matches[1] }
        } elseif ($line -match '^db\.database=(.*)$') {
            if (-not $env:PGDATABASE) { $env:PGDATABASE = $matches[1] }
        } elseif ($line -match '^db\.username=(.*)$') {
            if (-not $env:PGUSER) { $env:PGUSER = $matches[1] }
        } elseif ($line -match '^db\.password=(.*)$') {
            if (-not $env:PGPASSWORD) { $env:PGPASSWORD = $matches[1] }
        }
    }
}

Write-Host ""
Write-Host "=========================================="
Write-Host "  Servidor iniciando..."
Write-Host "=========================================="
Write-Host ""

# Executa o servidor
# Usa caminhos absolutos para garantir que o java encontre os JARs
$pwd = (Get-Location).Path
$classpath = "$pwd;$pwd\lib\postgresql.jar;$pwd\lib\hikaricp.jar"
if (Test-Path "lib/slf4j-api.jar") {
    $classpath += ";$pwd\lib\slf4j-api.jar"
}
if (Test-Path "lib/slf4j-simple.jar") {
    $classpath += ";$pwd\lib\slf4j-simple.jar"
}
if (Test-Path "lib/jakarta-validation-api.jar") {
    $classpath += ";$pwd\lib\jakarta-validation-api.jar"
}
if (Test-Path "lib/hibernate-validator.jar") {
    $classpath += ";$pwd\lib\hibernate-validator.jar"
}
if (Test-Path "lib/jakarta-el.jar") {
    $classpath += ";$pwd\lib\jakarta-el.jar"
}
if (Test-Path "lib/jakarta-el-impl.jar") {
    $classpath += ";$pwd\lib\jakarta-el-impl.jar"
}
$classpath += ";$pwd\bin"
$javaArgs = @(
    "-cp", $classpath,
    "-Dfile.encoding=UTF-8",
    "ControleSeServer"
)
& java $javaArgs

