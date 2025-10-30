#!/bin/bash
# Script para testar o deploy localmente com Docker

echo "🐳 Testando deploy local com Docker..."
echo ""

# Build da imagem
echo "📦 Construindo imagem Docker..."
docker build -t controle-se .

if [ $? -ne 0 ]; then
    echo "❌ Erro ao construir imagem Docker"
    exit 1
fi

echo ""
echo "✅ Imagem construída com sucesso!"
echo ""

# Criar diretório data se não existir
mkdir -p data

# Executar container
echo "🚀 Iniciando container..."
echo "📁 Dados serão salvos em: $(pwd)/data"
echo ""
echo "🌐 Acesse: http://localhost:8080"
echo ""
echo "⚠️  Pressione Ctrl+C para parar o servidor"
echo ""

docker run -p 8080:8080 -v "$(pwd)/data:/app/data" controle-se

