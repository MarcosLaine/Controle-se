@echo off
REM Script para testar o deploy localmente com Docker (Windows)

echo 🐳 Testando deploy local com Docker...
echo.

REM Build da imagem
echo 📦 Construindo imagem Docker...
docker build -t controle-se .

if %errorlevel% neq 0 (
    echo ❌ Erro ao construir imagem Docker
    exit /b 1
)

echo.
echo ✅ Imagem construída com sucesso!
echo.

REM Criar diretório data se não existir
if not exist data mkdir data

REM Executar container
echo 🚀 Iniciando container...
echo 📁 Dados serão salvos em: %cd%\data
echo.
echo 🌐 Acesse: http://localhost:8080
echo.
echo ⚠️  Pressione Ctrl+C para parar o servidor
echo.

docker run -p 8080:8080 -v "%cd%/data:/app/data" controle-se

