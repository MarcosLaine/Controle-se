# Frontend React - Controle-se

## Instalação

```bash
cd frontend
npm install
```

## Desenvolvimento

```bash
npm run dev
```

O frontend estará disponível em `http://localhost:3000` e fará proxy das requisições `/api` para `http://localhost:8080`.

## Build para Produção

```bash
npm run build
```

O build será gerado no diretório `../dist` (raiz do projeto).

## Estrutura

- `src/components/` - Componentes React
- `src/pages/` - Páginas principais
- `src/contexts/` - Contextos (Auth, Theme)
- `src/services/` - Serviços de API
- `src/utils/` - Utilitários

