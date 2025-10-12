# 💰 Controle-se - Sistema Financeiro Pessoal

Sistema completo de controle financeiro pessoal com **estruturas de dados avançadas**, **frontend web moderno** e **API REST** implementados em Java puro.

## 🚀 Tecnologias e Arquitetura

### Stack Tecnológica
- **Backend**: Java puro com servidor HTTP nativo (`com.sun.net.httpserver`)
- **Frontend**: HTML5, CSS3, JavaScript (ES6+)
- **Persistência**: Arquivos binários `.db` com serialização Java
- **API**: REST API completa com JSON
- **Estruturas de Dados**: Árvore B+ e Hash Extensível implementados do zero

### Arquitetura do Sistema

```
┌─────────────────────────────────────────────────────────────┐
│                    FRONTEND WEB                             │
│  (HTML + CSS + JavaScript - Interface Moderna e Responsiva) │
└────────────────┬────────────────────────────────────────────┘
                 │ HTTP/JSON
┌────────────────┴────────────────────────────────────────────┐
│                   API REST (Servidor Java)                  │
│  ● Autenticação     ● Transações    ● Categorias            │
│  ● Gastos/Receitas  ● Contas        ● Orçamentos            │
└────────────────┬────────────────────────────────────────────┘
                 │
┌────────────────┴────────────────────────────────────────────┐
│              BANCO DE DADOS (BancoDados.java)               │
│   ┌─────────────────────┐  ┌─────────────────────┐          │
│   │   Árvore B+ (4)     │  │  Hash Extensível    │          │
│   │  Índices Primários  │  │  Relacionamentos    │          │
│   │  ● Usuários         │  │  ● Usuario→Gastos   │          │
│   │  ● Categorias       │  │  ● Usuario→Receitas │          │
│   │  ● Gastos           │  │  ● Categoria→Gastos │          │
│   │  ● Receitas         │  │  ● Data→Gastos      │          │
│   │  ● Contas           │  │  ● Email→Usuario    │          │
│   │  ● Orçamentos       │  │                     │          |
│   └─────────────────────┘  └─────────────────────┘          │
└────────────────┬────────────────────────────────────────────┘
                 │
┌────────────────┴────────────────────────────────────────────┐
│              PERSISTÊNCIA (data/*.db)                       │
│  usuarios.db  categorias.db  gastos.db  receitas.db         │
│  contas.db    orcamentos.db  contadores.db                  │
└─────────────────────────────────────────────────────────────┘
```

## ✨ Funcionalidades Implementadas

### 🔐 Autenticação
- ✅ Cadastro de usuários
- ✅ Login com email e senha
- ✅ Sessão mantida no frontend

### 📊 Gestão Financeira
- ✅ **CRUD Completo** de Categorias (Create, Read, Update, Delete)
- ✅ **CRUD Completo** de Contas Bancárias
- ✅ **CRUD Completo** de Orçamentos
- ✅ Registro de Gastos com categoria e frequência
- ✅ Registro de Receitas com descrição
- ✅ **Exclusão Lógica (Lápide)** - registros marcados como inativos

### 🔍 Filtros e Buscas (Usando Índices)
- ✅ Filtro de transações por **categoria** (Hash Extensível)
- ✅ Filtro de transações por **data** (Hash Extensível)
- ✅ Filtros combinados (categoria + data)
- ✅ Busca otimizada O(1) com índices secundários

### 📈 Dashboard e Relatórios (ainda não implementado)
- ✅ Resumo financeiro com totais
- ✅ Listagem de todas as transações
- ✅ Monitoramento de orçamentos
- ✅ Visualização de contas e saldos

### 💾 Persistência de Dados
- ✅ Salvamento automático em arquivos `.db` binários
- ✅ Carregamento automático ao iniciar servidor
- ✅ Serialização de objetos Java
- ✅ Pasta `data/` com todos os arquivos

## 🗂️ Estruturas de Dados Avançadas

### 1. Árvore B+ (`ArvoreBPlus.java`)
**Uso**: Índices primários de todas as tabelas

```
Características:
├─ Ordem: 4 (mínimo 2 chaves por nó)
├─ Busca: O(log n)
├─ Inserção: O(log n)
├─ Ordenação automática
└─ Crescimento dinâmico com divisão de nós
```

**Aplicações no Projeto:**
- Tabela de Usuários (ID_Usuario → Usuario)
- Tabela de Categorias (ID_Categoria → Categoria)
- Tabela de Gastos (ID_Gasto → Gasto)
- Tabela de Receitas (ID_Receita → Receita)
- Tabela de Contas (ID_Conta → Conta)
- Tabela de Orçamentos (ID_Orcamento → Orcamento)

### 2. Hash Extensível (`HashExtensivel.java`)
**Uso**: Relacionamentos 1:N e índices secundários

```
Características:
├─ Capacidade: 3 registros por bucket
├─ Busca: O(1) médio
├─ Crescimento dinâmico de diretório
├─ Profundidade global e local
└─ Redistribuição automática em divisão
```

**Aplicações no Projeto:**
- `indiceUsuarioGastos`: Usuario → Lista<Gasto>
- `indiceUsuarioReceitas`: Usuario → Lista<Receita>
- `indiceUsuarioCategorias`: Usuario → Lista<Categoria>
- `indiceCategoriaGastos`: Categoria → Lista<Gasto> (para filtros)
- `indiceDataGastos`: Data → Lista<Gasto> (para filtros)
- `indiceDataReceitas`: Data → Lista<Receita> (para filtros)
- `indiceEmailUsuarios`: Email → Usuario (para login)

## 🎯 Como Executar

### Pré-requisitos
- Java JDK 11 ou superior
- Navegador web moderno (Chrome, Firefox, Edge)

### 1. Compilar o Projeto

```bash
javac -d bin src/*.java
```

### 2. Iniciar o Servidor

```bash
java -cp bin ControleSeServer
```

O servidor iniciará na porta **8080** e exibirá:
```
✅ Dados carregados do disco
✅ Banco de dados inicializado
=== ESTATÍSTICAS DO BANCO DE DADOS ===
Usuários cadastrados: X
Categorias cadastradas: Y
...
=== SERVIDOR CONTROLE-SE INICIADO ===
Servidor rodando em: http://localhost:8080
Frontend disponível em: http://localhost:8080/
API disponível em: http://localhost:8080/api/
```

### 3. Acessar o Sistema

Abra o navegador e acesse: **http://localhost:8080**

## 📁 Estrutura do Projeto

```
Controle-se/
├── src/                              # Código fonte Java
│   ├── ControleSe.java               # Programa principal (CLI)
│   ├── ControleSeServer.java         # Servidor HTTP + API REST
│   ├── BancoDados.java               # Gerenciador de dados
│   ├── Entidades.java                # Classes de domínio
│   ├── ArvoreBPlus.java              # Índices primários
│   ├── HashExtensivel.java           # Índices secundários
│   ├── SistemaFinanceiro.java        # Interface CLI
│   └── server/                       # Módulos do servidor (em progresso)
│       ├── utils/
│       │   ├── JsonUtil.java         # Parser JSON
│       │   ├── RequestUtil.java      # Utilitários de request
│       │   └── ResponseUtil.java     # Utilitários de response
│       └── handlers/
│           ├── StaticFileHandler.java     # Handler de arquivos estáticos
│           ├── CategoriesHandler.java     # Handler de categorias
│           └── AccountsHandler.java       # Handler de contas
├── bin/                              # Classes compiladas
├── data/                             # Arquivos de persistência
│   ├── usuarios.db                   # Dados de usuários
│   ├── categorias.db                 # Dados de categorias
│   ├── gastos.db                     # Dados de gastos
│   ├── receitas.db                   # Dados de receitas
│   ├── contas.db                     # Dados de contas
│   ├── orcamentos.db                 # Dados de orçamentos
│   └── contadores.db                 # Contadores de IDs
├── docs/                             # Documentação do projeto
│   ├── TP_Aeds3_Fase1.pdf            # Especificação Fase I
│   ├── Fase II - TP.pdf              # Especificação Fase II
│   └── ARQUITETURA_MODULAR.md        # Documentação da arquitetura
├── app.js                            # JavaScript do frontend
├── index.html                        # Interface web
├── styles.css                        # Estilos CSS
└── README.md                         # Este arquivo
```

## 🔌 Endpoints da API REST

### Autenticação
```http
POST   /api/auth/register    # Cadastrar usuário
POST   /api/auth/login        # Fazer login
```

### Categorias
```http
GET    /api/categories?userId={id}           # Listar categorias
POST   /api/categories                       # Criar categoria
PUT    /api/categories                       # Atualizar categoria
DELETE /api/categories?id={id}               # Excluir categoria (lógica)
```

### Contas
```http
GET    /api/accounts?userId={id}             # Listar contas
POST   /api/accounts                         # Criar conta
PUT    /api/accounts                         # Atualizar conta
DELETE /api/accounts?id={id}                 # Excluir conta (lógica)
```

### Transações
```http
GET    /api/transactions?userId={id}&categoryId={cat}&date={date}  # Listar com filtros
```

### Gastos
```http
POST   /api/expenses                         # Registrar gasto
```

### Receitas
```http
POST   /api/incomes                          # Registrar receita
```

### Orçamentos
```http
GET    /api/budgets?userId={id}              # Listar orçamentos
POST   /api/budgets                          # Criar orçamento
PUT    /api/budgets                          # Atualizar orçamento
DELETE /api/budgets?id={id}                  # Excluir orçamento (lógica)
```

### Dashboard
```http
GET    /api/overview?userId={id}&period={mes}  # Resumo financeiro
```

## 📊 Modelo de Dados

### Entidades

#### Usuario
```java
- idUsuario: int (PK)
- nome: String
- email: String (Unique)
- senha: String
- ativo: boolean
```

#### Categoria
```java
- idCategoria: int (PK)
- nome: String
- idUsuario: int (FK)
- ativo: boolean
```

#### Gasto
```java
- idGasto: int (PK)
- descricao: String
- valor: double
- data: LocalDate
- frequencia: String
- idCategoria: int (FK)
- idUsuario: int (FK)
- ativo: boolean
```

#### Receita
```java
- idReceita: int (PK)
- descricao: String
- valor: double
- data: LocalDate
- idUsuario: int (FK)
- ativo: boolean
```

#### Conta
```java
- idConta: int (PK)
- nome: String
- tipo: String
- saldoAtual: double
- idUsuario: int (FK)
- ativo: boolean
```

#### Orcamento
```java
- idOrcamento: int (PK)
- valorPlanejado: double
- periodo: String
- idCategoria: int (FK)
- idUsuario: int (FK)
- ativo: boolean
```

## 🎓 Complexidade das Operações

| Operação | Estrutura | Complexidade |
|----------|-----------|--------------|
| Busca por ID | Árvore B+ | **O(log n)** |
| Busca por FK | Hash Extensível | **O(1) médio** |
| Inserção | Árvore B+ | **O(log n)** |
| Filtro por categoria | Hash Extensível | **O(1) médio** |
| Filtro por data | Hash Extensível | **O(1) médio** |
| Filtros combinados | Hash + filtro | **O(m)** onde m = registros do usuário |

## 🔒 Características de Segurança

- ✅ **Exclusão Lógica (Lápide)**: Dados nunca são apagados fisicamente
- ✅ **Validação de Dados**: Verificações antes de inserir no banco
- ✅ **Tratamento de Erros**: Try-catch em todas as operações críticas
- ✅ **Logging**: Debug logs para rastreamento de problemas

## 🚀 Próximas Melhorias

### Em Desenvolvimento
- 🔄 Modularização completa do servidor (handlers separados)
- 🔄 Autenticação com tokens JWT
- 🔄 Gráficos interativos no dashboard

### Planejado
- 📋 Exportação de relatórios em PDF
- 📋 Importação de dados de CSV
- 📋 Notificações de orçamento excedido
- 📋 Metas financeiras
- 📋 Categorias padrão pré-definidas

## 📚 Documentação Adicional

- [ARQUITETURA_MODULAR.md](ARQUITETURA_MODULAR.md) - Guia de modularização do servidor
- [docs/TP_Aeds3_Fase1.pdf](docs/TP_Aeds3_Fase1.pdf) - Especificação técnica Fase I
- [docs/Fase II - TP.pdf](docs/Fase%20II%20-%20TP.pdf) - Especificação técnica Fase II

## 🤝 Desenvolvimento

### Interface de Linha de Comando (CLI)
Execute o sistema em modo CLI:
```bash
java -cp bin ControleSe
```

Funcionalidades CLI:
- Menu interativo
- Todas operações CRUD disponíveis
- Relatórios em texto
- Útil para testes e debug

### Interface Web
Execute o servidor HTTP:
```bash
java -cp bin ControleSeServer
```

Acesse: **http://localhost:8080**

## 📝 Licença

Este projeto foi desenvolvido para fins **educacionais** como parte da disciplina de **Algoritmos e Estruturas de Dados III (AEDS3)**.

---

**Desenvolvido com ☕ e Java puro**
