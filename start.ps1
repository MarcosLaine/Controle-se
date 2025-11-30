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
$classpath = ".;lib/postgresql.jar;bin"
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
$classpath = ".;lib/postgresql.jar;bin"
$javaArgs = @(
    "-cp", $classpath,
    "-Dfile.encoding=UTF-8",
    "ControleSeServer"
)
& java $javaArgs

