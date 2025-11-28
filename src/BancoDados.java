import java.io.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Sistema de Gerenciamento de Banco de Dados para Controle-se
 * Implementa tabelas com índices usando Árvore B+ e Hash Extensível
 * Com persistência em arquivos binários .db
 */
public class BancoDados {
    
    private static final String DATA_DIR = "data";
    private static final String IDX_DIR = DATA_DIR + "/idx";
    private static final String USUARIOS_DB = DATA_DIR + "/usuarios.db";
    private static final String CATEGORIAS_DB = DATA_DIR + "/categorias.db";
    private static final String GASTOS_DB = DATA_DIR + "/gastos.db";
    private static final String RECEITAS_DB = DATA_DIR + "/receitas.db";
    private static final String CONTAS_DB = DATA_DIR + "/contas.db";
    private static final String ORCAMENTOS_DB = DATA_DIR + "/orcamentos.db";
    private static final String CONTADORES_DB = DATA_DIR + "/contadores.db";
    private static final String CATEGORIA_GASTO_DB = DATA_DIR + "/categoria_gasto.db";
    private static final String TAGS_DB = DATA_DIR + "/tags.db";
    private static final String TRANSACAO_TAG_DB = DATA_DIR + "/transacao_tag.db";
    private static final String INVESTIMENTOS_DB = DATA_DIR + "/investimentos.db";
    
    // Arquivos de índices
    private static final String IDX_USUARIOS = IDX_DIR + "/idx_usuarios.db";
    private static final String IDX_CATEGORIAS = IDX_DIR + "/idx_categorias.db";
    private static final String IDX_GASTOS = IDX_DIR + "/idx_gastos.db";
    private static final String IDX_RECEITAS = IDX_DIR + "/idx_receitas.db";
    private static final String IDX_CONTAS = IDX_DIR + "/idx_contas.db";
    private static final String IDX_ORCAMENTOS = IDX_DIR + "/idx_orcamentos.db";
    private static final String IDX_TAGS = IDX_DIR + "/idx_tags.db";
    private static final String IDX_INVESTIMENTOS = IDX_DIR + "/idx_investimentos.db";
    
    // Arquivos de índices Hash Extensível
    private static final String IDX_USUARIO_CATEGORIAS = IDX_DIR + "/idx_usuario_categorias.db";
    private static final String IDX_USUARIO_GASTOS = IDX_DIR + "/idx_usuario_gastos.db";
    private static final String IDX_USUARIO_RECEITAS = IDX_DIR + "/idx_usuario_receitas.db";
    private static final String IDX_USUARIO_CONTAS = IDX_DIR + "/idx_usuario_contas.db";
    private static final String IDX_USUARIO_ORCAMENTOS = IDX_DIR + "/idx_usuario_orcamentos.db";
    private static final String IDX_CATEGORIA_ORCAMENTOS = IDX_DIR + "/idx_categoria_orcamentos.db";
    private static final String IDX_EMAIL_USUARIOS = IDX_DIR + "/idx_email_usuarios.db";
    private static final String IDX_DATA_GASTOS = IDX_DIR + "/idx_data_gastos.db";
    private static final String IDX_DATA_RECEITAS = IDX_DIR + "/idx_data_receitas.db";
    private static final String IDX_TIPO_CONTAS = IDX_DIR + "/idx_tipo_contas.db";
    private static final String IDX_CATEGORIA_GASTOS = IDX_DIR + "/idx_categoria_gastos.db";
    private static final String IDX_GASTO_CATEGORIAS = IDX_DIR + "/idx_gasto_categorias.db";
    private static final String IDX_USUARIO_TAGS = IDX_DIR + "/idx_usuario_tags.db";
    private static final String IDX_TAG_GASTOS = IDX_DIR + "/idx_tag_gastos.db";
    private static final String IDX_TAG_RECEITAS = IDX_DIR + "/idx_tag_receitas.db";
    private static final String IDX_GASTO_TAGS = IDX_DIR + "/idx_gasto_tags.db";
    private static final String IDX_RECEITA_TAGS = IDX_DIR + "/idx_receita_tags.db";
    private static final String IDX_USUARIO_INVESTIMENTOS = IDX_DIR + "/idx_usuario_investimentos.db";
    
    // Tabelas principais com índices primários (Árvore B+)
    private ArvoreBPlus tabelaUsuarios;
    private ArvoreBPlus tabelaCategorias;
    private ArvoreBPlus tabelaGastos;
    private ArvoreBPlus tabelaReceitas;
    private ArvoreBPlus tabelaContas;
    private ArvoreBPlus tabelaOrcamentos;
    private ArvoreBPlus tabelaTags;
    private ArvoreBPlus tabelaInvestimentos;
    // Relacionamentos N:N não precisam de ArvoreBPlus, apenas dos índices Hash
    private List<CategoriaGasto> relacionamentosCategoriaGasto;
    private List<TransacaoTag> relacionamentosTransacaoTag;
    
    // Índices secundários (Hash Extensível) para relacionamentos 1:N
    private HashExtensivel indiceUsuarioCategorias;    // Usuario -> Categorias
    private HashExtensivel indiceUsuarioGastos;         // Usuario -> Gastos
    private HashExtensivel indiceUsuarioReceitas;       // Usuario -> Receitas
    private HashExtensivel indiceUsuarioContas;         // Usuario -> Contas
    private HashExtensivel indiceUsuarioOrcamentos;     // Usuario -> Orçamentos
    private HashExtensivel indiceCategoriaOrcamentos;   // Categoria -> Orçamentos
    
    // Índices úteis para consultas frequentes
    private HashExtensivel indiceEmailUsuarios;         // Email -> Usuario (para login)
    private HashExtensivel indiceDataGastos;            // Data -> Gastos (para filtros por data)
    private HashExtensivel indiceDataReceitas;         // Data -> Receitas (para filtros por data)
    private HashExtensivel indiceTipoContas;          // Tipo -> Contas (para filtros por tipo)
    private HashExtensivel indiceCategoriaGastos;     // Categoria -> Gastos (relacionamento N:N)
    private HashExtensivel indiceGastoCategorias;     // Gasto -> Categorias (relacionamento N:N reverso)
    
    // Índices para Tags (atributo multivalorado via N:N)
    private HashExtensivel indiceUsuarioTags;         // Usuario -> Tags
    private HashExtensivel indiceTagGastos;           // Tag -> Gastos (relacionamento N:N)
    private HashExtensivel indiceTagReceitas;         // Tag -> Receitas (relacionamento N:N)
    private HashExtensivel indiceGastoTags;           // Gasto -> Tags (relacionamento N:N reverso)
    private HashExtensivel indiceReceitaTags;         // Receita -> Tags (relacionamento N:N reverso)
    private HashExtensivel indiceUsuarioInvestimentos; // Usuario -> Investimentos
    
    // Contadores para IDs únicos
    private int proximoIdUsuario = 1;
    private int proximoIdCategoria = 1;
    private int proximoIdGasto = 1;
    private int proximoIdReceita = 1;
    private int proximoIdConta = 1;
    private int proximoIdOrcamento = 1;
    private int proximoIdCategoriaGasto = 1;
    private int proximoIdTag = 1;
    private int proximoIdTransacaoTag = 1;
    private int proximoIdInvestimento = 1;
    
    public BancoDados() {
        // Inicializa tabelas principais com Árvore B+ de ordem 4
        // Ordem 4 = máximo de 4 filhos por nó = máximo de 3 chaves por nó
        tabelaUsuarios = new ArvoreBPlus(4);
        tabelaCategorias = new ArvoreBPlus(4);
        tabelaGastos = new ArvoreBPlus(4);
        tabelaReceitas = new ArvoreBPlus(4);
        tabelaContas = new ArvoreBPlus(4);
        tabelaOrcamentos = new ArvoreBPlus(4);
        tabelaTags = new ArvoreBPlus(4);
        tabelaInvestimentos = new ArvoreBPlus(4);
        relacionamentosCategoriaGasto = new ArrayList<>();
        relacionamentosTransacaoTag = new ArrayList<>();
        
        // Inicializa índices secundários
        indiceUsuarioCategorias = new HashExtensivel(3);
        indiceUsuarioGastos = new HashExtensivel(3);
        indiceUsuarioReceitas = new HashExtensivel(3);
        indiceUsuarioContas = new HashExtensivel(3);
        indiceUsuarioOrcamentos = new HashExtensivel(3);
        indiceCategoriaOrcamentos = new HashExtensivel(3);
        
        // Inicializa índices úteis
        indiceEmailUsuarios = new HashExtensivel(3);
        indiceDataGastos = new HashExtensivel(3);
        indiceDataReceitas = new HashExtensivel(3);
        indiceTipoContas = new HashExtensivel(3);
        indiceCategoriaGastos = new HashExtensivel(3);
        indiceGastoCategorias = new HashExtensivel(3);
        
        // Inicializa índices para Tags
        indiceUsuarioTags = new HashExtensivel(3);
        indiceTagGastos = new HashExtensivel(3);
        indiceTagReceitas = new HashExtensivel(3);
        indiceGastoTags = new HashExtensivel(3);
        indiceReceitaTags = new HashExtensivel(3);
        indiceUsuarioInvestimentos = new HashExtensivel(3);
        
        // Tenta carregar índices primeiro
        boolean indicesExistem = verificarIndicesExistem();
        
        if (indicesExistem) {
            // Carrega índices dos arquivos (os dados já estão nos índices)
            System.out.println("Carregando índices dos arquivos...");
            carregarTodosIndicesPrimarios();
            carregarTodosIndicesSecundarios();
            try {
                carregarContadores();
                // Carrega apenas as listas de relacionamentos (já estão nos índices Hash)
                carregarCategoriaGasto(false); // Carrega lista relacionamentosCategoriaGasto sem reconstruir índices
                carregarTransacaoTag(false); // Carrega lista relacionamentosTransacaoTag sem reconstruir índices
            } catch (IOException | ClassNotFoundException e) {
                System.err.println("Erro ao carregar dados auxiliares: " + e.getMessage());
            }
            System.out.println("✓ Índices carregados dos arquivos");
        } else {
            // Reconstroi índices a partir dos dados
            System.out.println("Reconstruindo índices a partir dos dados...");
            carregarDados(); // Já reconstrói os índices de relacionamentos N:N
            // Salva os índices reconstruídos
            salvarTodosIndicesPrimarios();
            salvarTodosIndicesSecundarios();
            System.out.println("✓ Índices reconstruídos e salvos");
        }
        
        System.out.println("Banco de dados inicializado");
        imprimirEstatisticas();
    }
    
    /**
     * Verifica se os arquivos de índice existem
     * Verifica todos os índices primários e secundários essenciais
     */
    private boolean verificarIndicesExistem() {
        // Verifica índices primários (B+ Tree) - essenciais
        File[] indicesPrimarios = {
            new File(IDX_USUARIOS),
            new File(IDX_CATEGORIAS),
            new File(IDX_GASTOS),
            new File(IDX_RECEITAS),
            new File(IDX_CONTAS),
            new File(IDX_ORCAMENTOS),
            new File(IDX_TAGS),
            new File(IDX_INVESTIMENTOS)
        };
        
        // Verifica índices secundários essenciais (Hash Extensível)
        File[] indicesSecundarios = {
            new File(IDX_USUARIO_CATEGORIAS),
            new File(IDX_USUARIO_GASTOS),
            new File(IDX_USUARIO_RECEITAS),
            new File(IDX_EMAIL_USUARIOS),
            new File(IDX_CATEGORIA_GASTOS),
            new File(IDX_GASTO_CATEGORIAS)
        };
        
        // Verifica se todos os índices primários existem
        for (File idx : indicesPrimarios) {
            if (!idx.exists()) {
                System.out.println("Índice primário não encontrado: " + idx.getName());
                return false;
            }
        }
        
        // Verifica se pelo menos os índices secundários essenciais existem
        // (alguns podem não existir se não houver dados ainda)
        int indicesSecundariosExistentes = 0;
        for (File idx : indicesSecundarios) {
            if (idx.exists()) {
                indicesSecundariosExistentes++;
            }
        }
        
        // Se nenhum índice secundário existe, considera que os índices não foram criados
        if (indicesSecundariosExistentes == 0) {
            System.out.println("Nenhum índice secundário encontrado - será necessário reconstruir");
            return false;
        }
        
        return true;
    }
    
    // ========== OPERAÇÕES DE USUÁRIO ==========
    
    public int cadastrarUsuario(String nome, String email, String senha) {
        // Verifica se email já existe
        if (buscarUsuarioPorEmail(email) != null) {
            throw new RuntimeException("Email já cadastrado!");
        }
        
        // Criptografa a senha usando RSA antes de salvar
        RSAEncryption rsa = RSAKeyManager.obterInstancia();
        String senhaCriptografada = rsa.criptografar(senha);
        
        int idUsuario = proximoIdUsuario++;
        Usuario usuario = new Usuario(idUsuario, nome, email, senhaCriptografada);
        
        // Insere na tabela principal
        tabelaUsuarios.inserir(idUsuario, usuario);
        
        // Atualiza índice de email
        indiceEmailUsuarios.inserir(email.hashCode(), usuario);
        
        // Persiste os dados e índices
        salvarUsuarios();
        salvarContadores();
        salvarIndiceArvoreBPlus(tabelaUsuarios, IDX_USUARIOS);
        salvarIndiceHashExtensivel(indiceEmailUsuarios, IDX_EMAIL_USUARIOS);
        
        return idUsuario;
    }
    
    public Usuario buscarUsuario(int idUsuario) {
        return (Usuario) tabelaUsuarios.buscar(idUsuario);
    }
    
    public Usuario buscarUsuarioPorEmail(String email) {
        List<Object> usuarios = indiceEmailUsuarios.buscar(email.hashCode());
        for (Object obj : usuarios) {
            Usuario usuario = (Usuario) obj;
            if (usuario.getEmail().equals(email)) {
                return usuario;
            }
        }
        return null;
    }
    
    public boolean autenticarUsuario(String email, String senha) {
        Usuario usuario = buscarUsuarioPorEmail(email);
        if (usuario == null) {
            return false;
        }
        
        // Descriptografa a senha armazenada e compara com a senha fornecida
        RSAEncryption rsa = RSAKeyManager.obterInstancia();
        String senhaDescriptografada = rsa.descriptografar(usuario.getSenha());
        return senhaDescriptografada.equals(senha);
    }
    
    // ========== OPERAÇÕES DE CATEGORIA ==========
    
    public int cadastrarCategoria(String nome, int idUsuario) {
        int idCategoria = proximoIdCategoria++;
        Categoria categoria = new Categoria(idCategoria, nome, idUsuario);
        
        // Insere na tabela principal
        tabelaCategorias.inserir(idCategoria, categoria);
        
        // Atualiza índice de relacionamento
        indiceUsuarioCategorias.inserir(idUsuario, categoria);
        
        // Persiste os dados e índices
        salvarCategorias();
        salvarContadores();
        salvarIndiceArvoreBPlus(tabelaCategorias, IDX_CATEGORIAS);
        salvarIndiceHashExtensivel(indiceUsuarioCategorias, IDX_USUARIO_CATEGORIAS);
        
        return idCategoria;
    }
    
    public Categoria buscarCategoria(int idCategoria) {
        return (Categoria) tabelaCategorias.buscar(idCategoria);
    }
    
    public List<Categoria> buscarCategoriasPorUsuario(int idUsuario) {
        List<Object> objetos = indiceUsuarioCategorias.buscar(idUsuario);
        List<Categoria> categorias = new ArrayList<>();
        for (Object obj : objetos) {
            Categoria catIndex = (Categoria) obj;
            Categoria catReal = buscarCategoria(catIndex.getIdCategoria());
            if (catReal != null && catReal.isAtivo()) { // Filtra apenas categorias ativas
                categorias.add(catReal);
            }
        }
        return categorias;
    }
    
    // ========== OPERAÇÕES DE GASTO ==========
    
    public int cadastrarGasto(String descricao, double valor, LocalDate data, String frequencia, int idUsuario, List<Integer> idsCategorias, int idConta, String[] observacoes) {
        int idGasto = proximoIdGasto++;
        // Gasto não tem mais idCategoria direto - relacionamento via CategoriaGasto
        Gasto gasto = new Gasto(idGasto, descricao, valor, data, frequencia, idUsuario, 0, idConta);
        
        // Define observações se fornecidas
        if (observacoes != null && observacoes.length > 0) {
            gasto.setObservacoes(observacoes);
        }
        
        // Insere na tabela principal
        tabelaGastos.inserir(idGasto, gasto);
        
        // Atualiza índices básicos
        indiceUsuarioGastos.inserir(idUsuario, gasto);
        indiceDataGastos.inserir(data.hashCode(), gasto);
        
        // Cria relacionamentos N:N com as categorias
        if (idsCategorias != null && !idsCategorias.isEmpty()) {
            for (int idCategoria : idsCategorias) {
                // Valida se a categoria existe
                Categoria cat = buscarCategoria(idCategoria);
                if (cat != null && cat.isAtivo()) {
                    adicionarCategoriaAoGastoSemSalvar(idCategoria, idGasto);
                }
            }
        }
        
        // Atualiza saldo da conta (subtrai o gasto)
        Conta conta = buscarConta(idConta);
        if (conta != null) {
            conta.setSaldoAtual(conta.getSaldoAtual() - valor);
            salvarContas();
        }
        
        // Persiste os dados e índices (uma vez só, depois do loop)
        salvarGastos();
        salvarCategoriaGasto();
        salvarContadores();
        salvarIndiceArvoreBPlus(tabelaGastos, IDX_GASTOS);
        salvarIndiceHashExtensivel(indiceUsuarioGastos, IDX_USUARIO_GASTOS);
        salvarIndiceHashExtensivel(indiceDataGastos, IDX_DATA_GASTOS);
        salvarIndiceHashExtensivel(indiceCategoriaGastos, IDX_CATEGORIA_GASTOS);
        salvarIndiceHashExtensivel(indiceGastoCategorias, IDX_GASTO_CATEGORIAS);
        
        return idGasto;
    }
    
    // Sobrecarga para manter compatibilidade (uma única categoria)
    public int cadastrarGasto(String descricao, double valor, LocalDate data, String frequencia, int idUsuario, int idCategoria, int idConta) {
        List<Integer> categorias = new ArrayList<>();
        if (idCategoria > 0) {
            categorias.add(idCategoria);
        }
        return cadastrarGasto(descricao, valor, data, frequencia, idUsuario, categorias, idConta, null);
    }
    
    // Sobrecarga para manter compatibilidade (sem observações)
    public int cadastrarGasto(String descricao, double valor, LocalDate data, String frequencia, int idUsuario, List<Integer> idsCategorias, int idConta) {
        return cadastrarGasto(descricao, valor, data, frequencia, idUsuario, idsCategorias, idConta, null);
    }
    
    public Gasto buscarGasto(int idGasto) {
        return (Gasto) tabelaGastos.buscar(idGasto);
    }
    
    public List<Gasto> buscarGastosPorUsuario(int idUsuario) {
        List<Object> objetos = indiceUsuarioGastos.buscar(idUsuario);
        List<Gasto> gastos = new ArrayList<>();
        for (Object obj : objetos) {
            Gasto gastoIndex = (Gasto) obj;
            Gasto gastoReal = buscarGasto(gastoIndex.getIdGasto());
            if (gastoReal != null && gastoReal.isAtivo()) { // Filtra apenas gastos ativos
                gastos.add(gastoReal);
            }
        }
        return gastos;
    }
    
    public List<Gasto> buscarGastosPorCategoria(int idCategoria) {
        // Busca via relacionamento N:N
        List<Object> relacionamentos = indiceCategoriaGastos.buscar(idCategoria);
        List<Gasto> gastos = new ArrayList<>();
        for (Object obj : relacionamentos) {
            if (obj instanceof CategoriaGasto) {
                CategoriaGasto rel = (CategoriaGasto) obj;
                if (rel.isAtivo()) {
                    Gasto gasto = buscarGasto(rel.getIdGasto());
                    if (gasto != null && gasto.isAtivo()) {
                        gastos.add(gasto);
                    }
                }
            }
        }
        return gastos;
    }
    
    // ========== OPERAÇÕES DE RELACIONAMENTO N:N (CATEGORIA-GASTO) ==========
    
    /**
     * Adiciona uma categoria a um gasto (cria relacionamento N:N) - SEM salvar (otimizado)
     */
    private int adicionarCategoriaAoGastoSemSalvar(int idCategoria, int idGasto) {
        int idRelacionamento = proximoIdCategoriaGasto++;
        CategoriaGasto relacionamento = new CategoriaGasto(idRelacionamento, idCategoria, idGasto);
        
        // Insere na lista de relacionamentos
        relacionamentosCategoriaGasto.add(relacionamento);
        
        // Atualiza índices bidirecionais
        indiceCategoriaGastos.inserir(idCategoria, relacionamento);
        indiceGastoCategorias.inserir(idGasto, relacionamento);
        
        return idRelacionamento;
    }
    
    /**
     * Adiciona uma categoria a um gasto (cria relacionamento N:N) - COM persistência
     */
    public int adicionarCategoriaAoGasto(int idCategoria, int idGasto) {
        int idRelacionamento = adicionarCategoriaAoGastoSemSalvar(idCategoria, idGasto);
        
        // Persiste
        salvarCategoriaGasto();
        salvarContadores();
        
        return idRelacionamento;
    }
    
    /**
     * Remove uma categoria de um gasto (exclusão lógica do relacionamento)
     */
    public void removerCategoriaDoGasto(int idCategoria, int idGasto) {
        List<Object> relacionamentos = indiceGastoCategorias.buscar(idGasto);
        for (Object obj : relacionamentos) {
            if (obj instanceof CategoriaGasto) {
                CategoriaGasto rel = (CategoriaGasto) obj;
                if (rel.getIdCategoria() == idCategoria && rel.isAtivo()) {
                    rel.setAtivo(false);
                }
            }
        }
        salvarCategoriaGasto();
    }
    
    /**
     * Busca todas as categorias de um gasto
     */
    public List<Categoria> buscarCategoriasDoGasto(int idGasto) {
        List<Object> relacionamentos = indiceGastoCategorias.buscar(idGasto);
        List<Categoria> categorias = new ArrayList<>();
        for (Object obj : relacionamentos) {
            if (obj instanceof CategoriaGasto) {
                CategoriaGasto rel = (CategoriaGasto) obj;
                if (rel.isAtivo()) {
                    Categoria categoria = buscarCategoria(rel.getIdCategoria());
                    if (categoria != null && categoria.isAtivo()) {
                        categorias.add(categoria);
                    }
                }
            }
        }
        return categorias;
    }
    
    public List<Gasto> buscarGastosPorData(LocalDate data) {
        List<Object> objetos = indiceDataGastos.buscar(data.hashCode());
        List<Gasto> gastos = new ArrayList<>();
        for (Object obj : objetos) {
            Gasto gasto = (Gasto) obj;
            if (gasto.getData().equals(data)) {
                gastos.add(gasto);
            }
        }
        return gastos;
    }
    
    // Métodos de busca com filtros combinados usando índices
    public List<Gasto> buscarGastosComFiltros(int idUsuario, Integer idCategoria, LocalDate data) {
        List<Gasto> gastosUsuario = buscarGastosPorUsuario(idUsuario);
        List<Gasto> resultado = new ArrayList<>();
        
        for (Gasto gasto : gastosUsuario) {
            boolean corresponde = true;
            
            // Filtro por categoria (usando relacionamento N:N)
            if (idCategoria != null) {
                boolean temCategoria = gastoTemCategoria(gasto.getIdGasto(), idCategoria);
                if (!temCategoria) {
                    corresponde = false;
                }
            }
            
            // Filtro por data
            if (data != null && !gasto.getData().equals(data)) {
                corresponde = false;
            }
            
            if (corresponde && gasto.isAtivo()) {
                resultado.add(gasto);
            }
        }
        
        return resultado;
    }
    
    /**
     * Verifica se um gasto tem uma categoria específica (relacionamento N:N)
     */
    private boolean gastoTemCategoria(int idGasto, int idCategoria) {
        List<Object> relacionamentos = indiceGastoCategorias.buscar(idGasto);
        for (Object obj : relacionamentos) {
            if (obj instanceof CategoriaGasto) {
                CategoriaGasto rel = (CategoriaGasto) obj;
                if (rel.isAtivo() && rel.getIdCategoria() == idCategoria) {
                    return true;
                }
            }
        }
        return false;
    }
    
    // ========== OPERAÇÕES DE RECEITA ==========
    
    public int cadastrarReceita(String descricao, double valor, LocalDate data, int idUsuario, int idConta) {
        int idReceita = proximoIdReceita++;
        Receita receita = new Receita(idReceita, descricao, valor, data, idUsuario, idConta);
        
        // Insere na tabela principal
        tabelaReceitas.inserir(idReceita, receita);
        
        // Atualiza índices
        indiceUsuarioReceitas.inserir(idUsuario, receita);
        indiceDataReceitas.inserir(data.hashCode(), receita);
        
        // Atualiza saldo da conta (adiciona a receita)
        Conta conta = buscarConta(idConta);
        if (conta != null) {
            conta.setSaldoAtual(conta.getSaldoAtual() + valor);
            salvarContas();
        }
        
        // Persiste os dados e índices
        salvarReceitas();
        salvarContadores();
        salvarIndiceArvoreBPlus(tabelaReceitas, IDX_RECEITAS);
        salvarIndiceHashExtensivel(indiceUsuarioReceitas, IDX_USUARIO_RECEITAS);
        salvarIndiceHashExtensivel(indiceDataReceitas, IDX_DATA_RECEITAS);
        
        return idReceita;
    }
    
    public Receita buscarReceita(int idReceita) {
        return (Receita) tabelaReceitas.buscar(idReceita);
    }
    
    public List<Receita> buscarReceitasPorUsuario(int idUsuario) {
        List<Object> objetos = indiceUsuarioReceitas.buscar(idUsuario);
        List<Receita> receitas = new ArrayList<>();
        for (Object obj : objetos) {
            Receita receitaIndex = (Receita) obj;
            Receita receitaReal = buscarReceita(receitaIndex.getIdReceita());
            if (receitaReal != null && receitaReal.isAtivo()) { // Filtra apenas receitas ativas
                receitas.add(receitaReal);
            }
        }
        return receitas;
    }
    
    public List<Receita> buscarReceitasPorData(LocalDate data) {
        List<Object> objetos = indiceDataReceitas.buscar(data.hashCode());
        List<Receita> receitas = new ArrayList<>();
        for (Object obj : objetos) {
            Receita receita = (Receita) obj;
            if (receita.getData().equals(data)) {
                receitas.add(receita);
            }
        }
        return receitas;
    }
    
    // Métodos de busca com filtros combinados
    public List<Receita> buscarReceitasComFiltros(int idUsuario, LocalDate data) {
        List<Receita> receitasUsuario = buscarReceitasPorUsuario(idUsuario);
        List<Receita> resultado = new ArrayList<>();
        
        for (Receita receita : receitasUsuario) {
            boolean corresponde = true;
            
            // Filtro por data
            if (data != null && !receita.getData().equals(data)) {
                corresponde = false;
            }
            
            if (corresponde && receita.isAtivo()) {
                resultado.add(receita);
            }
        }
        
        return resultado;
    }
    
    // ========== OPERAÇÕES DE CONTA ==========
    
    public int cadastrarConta(String nome, String tipo, double saldoAtual, int idUsuario) {
        int idConta = proximoIdConta++;
        Conta conta = new Conta(idConta, nome, tipo, saldoAtual, idUsuario);
        
        // Insere na tabela principal
        tabelaContas.inserir(idConta, conta);
        
        // Atualiza índices
        indiceUsuarioContas.inserir(idUsuario, conta);
        indiceTipoContas.inserir(tipo.hashCode(), conta);
        
        // Persiste os dados e índices
        salvarContas();
        salvarContadores();
        salvarIndiceArvoreBPlus(tabelaContas, IDX_CONTAS);
        salvarIndiceHashExtensivel(indiceUsuarioContas, IDX_USUARIO_CONTAS);
        salvarIndiceHashExtensivel(indiceTipoContas, IDX_TIPO_CONTAS);
        
        return idConta;
    }
    
    public Conta buscarConta(int idConta) {
        return (Conta) tabelaContas.buscar(idConta);
    }
    
    public List<Conta> buscarContasPorUsuario(int idUsuario) {
        List<Object> objetos = indiceUsuarioContas.buscar(idUsuario);
        List<Conta> contas = new ArrayList<>();
        for (Object obj : objetos) {
            Conta contaIndex = (Conta) obj;
            Conta contaReal = buscarConta(contaIndex.getIdConta());
            if (contaReal != null && contaReal.isAtivo()) { // Filtra apenas contas ativas
                contas.add(contaReal);
            }
        }
        return contas;
    }
    
    public List<Conta> buscarContasPorTipo(String tipo) {
        List<Object> objetos = indiceTipoContas.buscar(tipo.hashCode());
        List<Conta> contas = new ArrayList<>();
        for (Object obj : objetos) {
            Conta conta = (Conta) obj;
            if (conta.getTipo().equals(tipo)) {
                contas.add(conta);
            }
        }
        return contas;
    }
    
    // ========== OPERAÇÕES DE ORÇAMENTO ==========
    
    public int cadastrarOrcamento(double valorPlanejado, String periodo, int idCategoria, int idUsuario) {
        int idOrcamento = proximoIdOrcamento++;
        Orcamento orcamento = new Orcamento(idOrcamento, valorPlanejado, periodo, idCategoria, idUsuario);
        
        // Insere na tabela principal
        tabelaOrcamentos.inserir(idOrcamento, orcamento);
        
        // Atualiza índices
        indiceUsuarioOrcamentos.inserir(idUsuario, orcamento);
        indiceCategoriaOrcamentos.inserir(idCategoria, orcamento);
        
        // Persiste os dados e índices
        salvarOrcamentos();
        salvarContadores();
        salvarIndiceArvoreBPlus(tabelaOrcamentos, IDX_ORCAMENTOS);
        salvarIndiceHashExtensivel(indiceUsuarioOrcamentos, IDX_USUARIO_ORCAMENTOS);
        salvarIndiceHashExtensivel(indiceCategoriaOrcamentos, IDX_CATEGORIA_ORCAMENTOS);
        
        return idOrcamento;
    }
    
    public Orcamento buscarOrcamento(int idOrcamento) {
        return (Orcamento) tabelaOrcamentos.buscar(idOrcamento);
    }
    
    public List<Orcamento> buscarOrcamentosPorUsuario(int idUsuario) {
        List<Object> objetos = indiceUsuarioOrcamentos.buscar(idUsuario);
        List<Orcamento> orcamentos = new ArrayList<>();
        for (Object obj : objetos) {
            Orcamento orcIndex = (Orcamento) obj;
            Orcamento orcReal = buscarOrcamento(orcIndex.getIdOrcamento());
            if (orcReal != null && orcReal.isAtivo()) { // Filtra apenas orçamentos ativos
                orcamentos.add(orcReal);
            }
        }
        return orcamentos;
    }
    
    public List<Orcamento> buscarOrcamentosPorCategoria(int idCategoria) {
        List<Object> objetos = indiceCategoriaOrcamentos.buscar(idCategoria);
        List<Orcamento> orcamentos = new ArrayList<>();
        for (Object obj : objetos) {
            orcamentos.add((Orcamento) obj);
        }
        return orcamentos;
    }
    
    // ========== OPERAÇÕES DE RELATÓRIOS ==========
    
    public double calcularTotalGastosPorCategoria(int idCategoria) {
        List<Gasto> gastos = buscarGastosPorCategoria(idCategoria);
        return gastos.stream().mapToDouble(Gasto::getValor).sum();
    }
    
    /**
     * Calcula o total de gastos por categoria para um usuário específico
     */
    public double calcularTotalGastosPorCategoriaEUsuario(int idCategoria, int idUsuario) {
        List<Gasto> gastos = buscarGastosPorCategoria(idCategoria);
        // Filtra apenas os gastos do usuário
        return gastos.stream()
            .filter(gasto -> gasto.getIdUsuario() == idUsuario && gasto.isAtivo())
            .mapToDouble(Gasto::getValor)
            .sum();
    }
    
    public double calcularTotalGastosUsuario(int idUsuario) {
        List<Gasto> gastos = buscarGastosPorUsuario(idUsuario);
        return gastos.stream().mapToDouble(Gasto::getValor).sum();
    }
    
    public double calcularTotalReceitasUsuario(int idUsuario) {
        List<Receita> receitas = buscarReceitasPorUsuario(idUsuario);
        return receitas.stream().mapToDouble(Receita::getValor).sum();
    }
    
    public double calcularSaldoUsuario(int idUsuario) {
        return calcularTotalReceitasUsuario(idUsuario) - calcularTotalGastosUsuario(idUsuario);
    }
    
    public double calcularTotalSaldoContasUsuario(int idUsuario) {
        List<Conta> contas = buscarContasPorUsuario(idUsuario);
        return contas.stream().mapToDouble(Conta::getSaldoAtual).sum();
    }

    public double calcularSaldoContasUsuarioSemInvestimento(int idUsuario) {
        List<Conta> contas = buscarContasPorUsuario(idUsuario);
        return contas.stream()
            .filter(conta -> !conta.getTipo().equals("Investimento"))
            .mapToDouble(Conta::getSaldoAtual)
            .sum();
    }
    
    // ========== OPERAÇÕES DE RELATÓRIOS POR PERÍODO ==========
    
    public List<Gasto> buscarGastosPorPeriodo(int idUsuario, LocalDate dataInicio, LocalDate dataFim) {
        List<Gasto> gastosUsuario = buscarGastosPorUsuario(idUsuario);
        List<Gasto> resultado = new ArrayList<>();
        
        for (Gasto gasto : gastosUsuario) {
            if (gasto.isAtivo() && 
                !gasto.getData().isBefore(dataInicio) && 
                !gasto.getData().isAfter(dataFim)) {
                resultado.add(gasto);
            }
        }
        
        return resultado;
    }
    
    public List<Receita> buscarReceitasPorPeriodo(int idUsuario, LocalDate dataInicio, LocalDate dataFim) {
        List<Receita> receitasUsuario = buscarReceitasPorUsuario(idUsuario);
        List<Receita> resultado = new ArrayList<>();
        
        for (Receita receita : receitasUsuario) {
            if (receita.isAtivo() && 
                !receita.getData().isBefore(dataInicio) && 
                !receita.getData().isAfter(dataFim)) {
                resultado.add(receita);
            }
        }
        
        return resultado;
    }
    
    // ========== OPERAÇÕES DE ATUALIZAÇÃO ==========
    
    public void atualizarCategoria(int idCategoria, String novoNome) {
        Categoria categoria = buscarCategoria(idCategoria);
        if (categoria != null) {
            categoria.setNome(novoNome);
            // Persiste dados e índices
            salvarCategorias();
            salvarIndiceArvoreBPlus(tabelaCategorias, IDX_CATEGORIAS);
        }
    }
    
    public void atualizarConta(int idConta, String novoNome, String novoTipo, double novoSaldo) {
        Conta conta = buscarConta(idConta);
        if (conta != null) {
            // Remove dos índices antigos se o tipo mudou
            String tipoAntigo = conta.getTipo();
            if (!tipoAntigo.equals(novoTipo)) {
                indiceTipoContas.removerEspecifico(tipoAntigo.hashCode(), conta);
            }
            
            // Atualiza o objeto
            conta.setNome(novoNome);
            conta.setTipo(novoTipo);
            conta.setSaldoAtual(novoSaldo);
            
            // Atualiza índices com novo tipo
            if (!tipoAntigo.equals(novoTipo)) {
                indiceTipoContas.inserir(novoTipo.hashCode(), conta);
            }
            
            // Persiste dados e índices
            salvarContas();
            salvarIndiceArvoreBPlus(tabelaContas, IDX_CONTAS);
            salvarIndiceHashExtensivel(indiceTipoContas, IDX_TIPO_CONTAS);
        }
    }
    
    public void atualizarGasto(int idGasto, String novaDescricao, double novoValor, LocalDate novaData, String novaFrequencia) {
        Gasto gasto = buscarGasto(idGasto);
        if (gasto != null) {
            // Remove dos índices antigos se a data mudou
            LocalDate dataAntiga = gasto.getData();
            if (!dataAntiga.equals(novaData)) {
                indiceDataGastos.removerEspecifico(dataAntiga.hashCode(), gasto);
            }
            
            // Atualiza o objeto
            gasto.setDescricao(novaDescricao);
            gasto.setValor(novoValor);
            gasto.setData(novaData);
            gasto.setFrequencia(novaFrequencia);
            
            // Atualiza índices com nova data
            if (!dataAntiga.equals(novaData)) {
                indiceDataGastos.inserir(novaData.hashCode(), gasto);
            }
            
            // Persiste dados e índices
            salvarGastos();
            salvarIndiceArvoreBPlus(tabelaGastos, IDX_GASTOS);
            salvarIndiceHashExtensivel(indiceDataGastos, IDX_DATA_GASTOS);
        }
    }
    
    public void atualizarReceita(int idReceita, String novaDescricao, double novoValor, LocalDate novaData) {
        Receita receita = buscarReceita(idReceita);
        if (receita != null) {
            // Remove dos índices antigos se a data mudou
            LocalDate dataAntiga = receita.getData();
            if (!dataAntiga.equals(novaData)) {
                indiceDataReceitas.removerEspecifico(dataAntiga.hashCode(), receita);
            }
            
            // Atualiza o objeto
            receita.setDescricao(novaDescricao);
            receita.setValor(novoValor);
            receita.setData(novaData);
            
            // Atualiza índices com nova data
            if (!dataAntiga.equals(novaData)) {
                indiceDataReceitas.inserir(novaData.hashCode(), receita);
            }
            
            // Persiste dados e índices
            salvarReceitas();
            salvarIndiceArvoreBPlus(tabelaReceitas, IDX_RECEITAS);
            salvarIndiceHashExtensivel(indiceDataReceitas, IDX_DATA_RECEITAS);
        }
    }
    
    public void atualizarOrcamento(int idOrcamento, double novoValorPlanejado, String novoPeriodo) {
        Orcamento orcamento = buscarOrcamento(idOrcamento);
        if (orcamento != null) {
            orcamento.setValorPlanejado(novoValorPlanejado);
            orcamento.setPeriodo(novoPeriodo);
            // Persiste dados e índices
            salvarOrcamentos();
            salvarIndiceArvoreBPlus(tabelaOrcamentos, IDX_ORCAMENTOS);
        }
    }
    
    // ========== OPERAÇÕES DE EXCLUSÃO LÓGICA (LÁPIDE) ==========
    
    public void excluirCategoria(int idCategoria) {
        Categoria categoria = buscarCategoria(idCategoria);
        if (categoria != null) {
            categoria.setAtivo(false);
            // Persiste dados e índices
            salvarCategorias();
            salvarIndiceArvoreBPlus(tabelaCategorias, IDX_CATEGORIAS);
        }
    }
    
    public void excluirConta(int idConta) {
        Conta conta = buscarConta(idConta);
        if (conta != null) {
            conta.setAtivo(false);
            // Persiste dados e índices
            salvarContas();
            salvarIndiceArvoreBPlus(tabelaContas, IDX_CONTAS);
        }
    }
    
    public void excluirGasto(int idGasto) {
        Gasto gasto = buscarGasto(idGasto);
        if (gasto != null) {
            gasto.setAtivo(false);
            // Persiste dados e índices
            salvarGastos();
            salvarIndiceArvoreBPlus(tabelaGastos, IDX_GASTOS);
        }
    }
    
    public void excluirReceita(int idReceita) {
        Receita receita = buscarReceita(idReceita);
        if (receita != null) {
            receita.setAtivo(false);
            // Persiste dados e índices
            salvarReceitas();
            salvarIndiceArvoreBPlus(tabelaReceitas, IDX_RECEITAS);
        }
    }
    
    public void excluirOrcamento(int idOrcamento) {
        Orcamento orcamento = buscarOrcamento(idOrcamento);
        if (orcamento != null) {
            orcamento.setAtivo(false);
            // Persiste dados e índices
            salvarOrcamentos();
            salvarIndiceArvoreBPlus(tabelaOrcamentos, IDX_ORCAMENTOS);
        }
    }
    
    // ========== PROCESSAMENTO DE RECORRÊNCIAS ==========
    
    /**
     * Processa todas as recorrências pendentes (gastos e receitas)
     * Deve ser executado diariamente pelo scheduler
     */
    public int processarRecorrencias() {
        int recorrenciasCriadas = 0;
        LocalDate hoje = LocalDate.now();
        
        System.out.println("[RECORRÊNCIAS] Processando recorrências para " + hoje);
        
        // Processa gastos recorrentes
        recorrenciasCriadas += processarGastosRecorrentes(hoje);
        
        // Processa receitas recorrentes
        recorrenciasCriadas += processarReceitasRecorrentes(hoje);
        
        if (recorrenciasCriadas > 0) {
            System.out.println("[RECORRÊNCIAS] Total de " + recorrenciasCriadas + " transações criadas automaticamente");
        } else {
            System.out.println("[RECORRÊNCIAS] Nenhuma recorrência pendente para hoje");
        }
        
        return recorrenciasCriadas;
    }
    
    /**
     * Processa gastos recorrentes que vencem hoje ou antes
     */
    private int processarGastosRecorrentes(LocalDate hoje) {
        int criados = 0;
        
        // Percorre todos os gastos ativos
        for (int i = 1; i < proximoIdGasto; i++) {
            Gasto gastoOriginal = buscarGasto(i);
            
            if (gastoOriginal == null || !gastoOriginal.isAtivo()) {
                continue; // Ignora gastos excluídos
            }
            
            // Verifica se é recorrente e se chegou o momento
            if (gastoOriginal.getProximaRecorrencia() != null && 
                !gastoOriginal.getProximaRecorrencia().isAfter(hoje)) {
                
                System.out.println("[RECORRÊNCIAS] Criando recorrência de gasto: " + gastoOriginal.getDescricao());
                
                // Busca as categorias do gasto original
                List<Categoria> categorias = buscarCategoriasDoGasto(gastoOriginal.getIdGasto());
                List<Integer> idsCategorias = new ArrayList<>();
                for (Categoria cat : categorias) {
                    idsCategorias.add(cat.getIdCategoria());
                }
                
                // Cria novo gasto com a data de recorrência
                int novoIdGasto = cadastrarGasto(
                    gastoOriginal.getDescricao() + " (Recorrência)",
                    gastoOriginal.getValor(),
                    gastoOriginal.getProximaRecorrencia(),
                    gastoOriginal.getFrequencia(),
                    gastoOriginal.getIdUsuario(),
                    idsCategorias,
                    gastoOriginal.getIdConta(),
                    gastoOriginal.getObservacoes()
                );
                
                // Marca o novo gasto como recorrência do original
                Gasto novoGasto = buscarGasto(novoIdGasto);
                if (novoGasto != null) {
                    novoGasto.setIdGastoOriginal(gastoOriginal.getIdGasto());
                    salvarGastos();
                }
                
                // Busca e copia tags do gasto original
                List<Tag> tags = buscarTagsGasto(gastoOriginal.getIdGasto());
                for (Tag tag : tags) {
                    associarTagTransacao(novoIdGasto, "GASTO", tag.getIdTag());
                }
                
                // Atualiza a próxima recorrência do original
                gastoOriginal.avancarProximaRecorrencia();
                salvarGastos();
                
                criados++;
            }
        }
        
        return criados;
    }
    
    /**
     * Processa receitas recorrentes que vencem hoje ou antes
     */
    private int processarReceitasRecorrentes(LocalDate hoje) {
        int criados = 0;
        
        // Percorre todas as receitas ativas
        for (int i = 1; i < proximoIdReceita; i++) {
            Receita receitaOriginal = buscarReceita(i);
            
            if (receitaOriginal == null || !receitaOriginal.isAtivo()) {
                continue; // Ignora receitas excluídas
            }
            
            // Verifica se é recorrente e se chegou o momento
            if (receitaOriginal.getProximaRecorrencia() != null && 
                !receitaOriginal.getProximaRecorrencia().isAfter(hoje)) {
                
                System.out.println("[RECORRÊNCIAS] Criando recorrência de receita: " + receitaOriginal.getDescricao());
                
                // Cria nova receita com a data de recorrência
                int novoIdReceita = cadastrarReceita(
                    receitaOriginal.getDescricao() + " (Recorrência)",
                    receitaOriginal.getValor(),
                    receitaOriginal.getProximaRecorrencia(),
                    receitaOriginal.getIdUsuario(),
                    receitaOriginal.getIdConta()
                );
                
                // Marca a nova receita como recorrência da original
                Receita novaReceita = buscarReceita(novoIdReceita);
                if (novaReceita != null) {
                    novaReceita.setFrequencia(receitaOriginal.getFrequencia());
                    novaReceita.setIdReceitaOriginal(receitaOriginal.getIdReceita());
                    novaReceita.setProximaRecorrencia(
                        receitaOriginal.getProximaRecorrencia()
                    );
                    salvarReceitas();
                }
                
                // Busca e copia tags da receita original
                List<Tag> tags = buscarTagsReceita(receitaOriginal.getIdReceita());
                for (Tag tag : tags) {
                    associarTagTransacao(novoIdReceita, "RECEITA", tag.getIdTag());
                }
                
                // Atualiza a próxima recorrência da original
                receitaOriginal.avancarProximaRecorrencia();
                salvarReceitas();
                
                criados++;
            }
        }
        
        return criados;
    }
    
    // ========== OPERAÇÕES DE TAG (ATRIBUTO MULTIVALORADO) ==========
    
    public int cadastrarTag(String nome, String cor, int idUsuario) {
        int idTag = proximoIdTag++;
        Tag tag = new Tag(idTag, nome, cor, idUsuario);
        
        // Insere na tabela principal
        tabelaTags.inserir(idTag, tag);
        
        // Atualiza índice
        indiceUsuarioTags.inserir(idUsuario, tag);
        
        // Persiste os dados e índices
        salvarTags();
        salvarContadores();
        salvarIndiceArvoreBPlus(tabelaTags, IDX_TAGS);
        salvarIndiceHashExtensivel(indiceUsuarioTags, IDX_USUARIO_TAGS);
        
        return idTag;
    }
    
    public Tag buscarTag(int idTag) {
        return (Tag) tabelaTags.buscar(idTag);
    }
    
    public List<Tag> buscarTagsPorUsuario(int idUsuario) {
        List<Object> objetos = indiceUsuarioTags.buscar(idUsuario);
        List<Tag> tags = new ArrayList<>();
        for (Object obj : objetos) {
            Tag tag = (Tag) obj;
            if (tag.isAtivo()) {
                tags.add(tag);
            }
        }
        return tags;
    }
    
    public void atualizarTag(int idTag, String novoNome, String novaCor) {
        Tag tag = buscarTag(idTag);
        if (tag != null) {
            tag.setNome(novoNome);
            tag.setCor(novaCor);
            // Persiste dados e índices
            salvarTags();
            salvarIndiceArvoreBPlus(tabelaTags, IDX_TAGS);
        }
    }
    
    public void excluirTag(int idTag) {
        Tag tag = buscarTag(idTag);
        if (tag != null) {
            tag.setAtivo(false);
            // Persiste dados e índices
            salvarTags();
            salvarIndiceArvoreBPlus(tabelaTags, IDX_TAGS);
        }
    }
    
    // Associar tag a uma transação (gasto ou receita)
    public void associarTagTransacao(int idTransacao, String tipoTransacao, int idTag) {
        int idTransacaoTag = proximoIdTransacaoTag++;
        TransacaoTag transacaoTag = new TransacaoTag(idTransacaoTag, idTransacao, tipoTransacao, idTag);
        
        relacionamentosTransacaoTag.add(transacaoTag);
        
        // Atualiza índices
        if (tipoTransacao.equals("GASTO")) {
            indiceTagGastos.inserir(idTag, idTransacao);
            indiceGastoTags.inserir(idTransacao, idTag);
            // Salva apenas os índices relevantes para GASTO
            salvarIndiceHashExtensivel(indiceTagGastos, IDX_TAG_GASTOS);
            salvarIndiceHashExtensivel(indiceGastoTags, IDX_GASTO_TAGS);
        } else if (tipoTransacao.equals("RECEITA")) {
            indiceTagReceitas.inserir(idTag, idTransacao);
            indiceReceitaTags.inserir(idTransacao, idTag);
            // Salva apenas os índices relevantes para RECEITA
            salvarIndiceHashExtensivel(indiceTagReceitas, IDX_TAG_RECEITAS);
            salvarIndiceHashExtensivel(indiceReceitaTags, IDX_RECEITA_TAGS);
        }
        
        salvarTransacaoTag();
        salvarContadores();
    }
    
    // Buscar tags de um gasto
    public List<Tag> buscarTagsGasto(int idGasto) {
        List<Object> objetos = indiceGastoTags.buscar(idGasto);
        List<Tag> tags = new ArrayList<>();
        for (Object obj : objetos) {
            int idTag = (Integer) obj;
            Tag tag = buscarTag(idTag);
            if (tag != null && tag.isAtivo()) {
                tags.add(tag);
            }
        }
        return tags;
    }
    
    // Buscar tags de uma receita
    public List<Tag> buscarTagsReceita(int idReceita) {
        List<Object> objetos = indiceReceitaTags.buscar(idReceita);
        List<Tag> tags = new ArrayList<>();
        for (Object obj : objetos) {
            int idTag = (Integer) obj;
            Tag tag = buscarTag(idTag);
            if (tag != null && tag.isAtivo()) {
                tags.add(tag);
            }
        }
        return tags;
    }
    
    // Buscar gastos por tag
    public List<Gasto> buscarGastosPorTag(int idTag) {
        List<Object> objetos = indiceTagGastos.buscar(idTag);
        List<Gasto> gastos = new ArrayList<>();
        for (Object obj : objetos) {
            int idGasto = (Integer) obj;
            Gasto gasto = buscarGasto(idGasto);
            if (gasto != null && gasto.isAtivo()) {
                gastos.add(gasto);
            }
        }
        return gastos;
    }
    
    // Buscar receitas por tag
    public List<Receita> buscarReceitasPorTag(int idTag) {
        List<Object> objetos = indiceTagReceitas.buscar(idTag);
        List<Receita> receitas = new ArrayList<>();
        for (Object obj : objetos) {
            int idReceita = (Integer) obj;
            Receita receita = buscarReceita(idReceita);
            if (receita != null && receita.isAtivo()) {
                receitas.add(receita);
            }
        }
        return receitas;
    }
    
    // ========== PERSISTÊNCIA DE DADOS ==========
    
    private void carregarDados() {
        try {
            carregarContadores();
            carregarUsuarios();
            carregarCategorias();
            carregarGastos();
            carregarCategoriaGasto(); // Carrega relacionamentos N:N
            carregarReceitas();
            carregarContas();
            carregarOrcamentos();
            carregarTags();
            carregarTransacaoTag(); // Carrega relacionamentos N:N Tags
            carregarInvestimentos();
            System.out.println("✓ Dados carregados do disco");
        } catch (Exception e) {
            // System.out.println("⚠ Primeira execução ou erro ao carregar dados: " + e.getMessage());
        }
    }
    
    private void salvarContadores() {
        try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(CONTADORES_DB))) {
            oos.writeInt(proximoIdUsuario);
            oos.writeInt(proximoIdCategoria);
            oos.writeInt(proximoIdGasto);
            oos.writeInt(proximoIdReceita);
            oos.writeInt(proximoIdConta);
            oos.writeInt(proximoIdOrcamento);
            oos.writeInt(proximoIdCategoriaGasto);
            oos.writeInt(proximoIdTag);
            oos.writeInt(proximoIdTransacaoTag);
            oos.writeInt(proximoIdInvestimento);
        } catch (IOException e) {
            System.err.println("Erro ao salvar contadores: " + e.getMessage());
        }
    }
    
    private void carregarContadores() throws IOException {
        File file = new File(CONTADORES_DB);
        if (!file.exists()) return;
        
        try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(CONTADORES_DB))) {
            proximoIdUsuario = ois.readInt();
            proximoIdCategoria = ois.readInt();
            proximoIdGasto = ois.readInt();
            proximoIdReceita = ois.readInt();
            proximoIdConta = ois.readInt();
            proximoIdOrcamento = ois.readInt();
            try {
                proximoIdCategoriaGasto = ois.readInt();
                proximoIdTag = ois.readInt();
                proximoIdTransacaoTag = ois.readInt();
                try {
                    proximoIdInvestimento = ois.readInt();
                } catch (EOFException e) {
                    proximoIdInvestimento = 1;
                }
            } catch (EOFException e) {
                // Arquivo antigo sem estes contadores
                proximoIdCategoriaGasto = 1;
                proximoIdTag = 1;
                proximoIdTransacaoTag = 1;
                proximoIdInvestimento = 1;
            }
        }
    }
    
    private void salvarUsuarios() {
        try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(USUARIOS_DB))) {
            List<Usuario> usuarios = new ArrayList<>();
            for (int i = 1; i < proximoIdUsuario; i++) {
                Usuario usuario = buscarUsuario(i);
                if (usuario != null) {
                    usuarios.add(usuario);
                }
            }
            oos.writeObject(usuarios);
        } catch (IOException e) {
            System.err.println("Erro ao salvar usuários: " + e.getMessage());
        }
    }
    
    @SuppressWarnings("unchecked")
    private void carregarUsuarios() throws IOException, ClassNotFoundException {
        File file = new File(USUARIOS_DB);
        if (!file.exists()) return;
        
        try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(USUARIOS_DB))) {
            List<Usuario> usuarios = (List<Usuario>) ois.readObject();
            for (Usuario usuario : usuarios) {
                tabelaUsuarios.inserir(usuario.getIdUsuario(), usuario);
                indiceEmailUsuarios.inserir(usuario.getEmail().hashCode(), usuario);
            }
        }
    }
    
    private void salvarCategorias() {
        try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(CATEGORIAS_DB))) {
            List<Categoria> categorias = new ArrayList<>();
            for (int i = 1; i < proximoIdCategoria; i++) {
                Categoria categoria = buscarCategoria(i);
                if (categoria != null) {
                    categorias.add(categoria);
                }
            }
            oos.writeObject(categorias);
        } catch (IOException e) {
            System.err.println("Erro ao salvar categorias: " + e.getMessage());
        }
    }
    
    @SuppressWarnings("unchecked")
    private void carregarCategorias() throws IOException, ClassNotFoundException {
        File file = new File(CATEGORIAS_DB);
        if (!file.exists()) return;
        
        try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(CATEGORIAS_DB))) {
            List<Categoria> categorias = (List<Categoria>) ois.readObject();
            for (Categoria categoria : categorias) {
                tabelaCategorias.inserir(categoria.getIdCategoria(), categoria);
                indiceUsuarioCategorias.inserir(categoria.getIdUsuario(), categoria);
            }
        }
    }
    
    private void salvarGastos() {
        try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(GASTOS_DB))) {
            List<Gasto> gastos = new ArrayList<>();
            for (int i = 1; i < proximoIdGasto; i++) {
                Gasto gasto = buscarGasto(i);
                if (gasto != null) {
                    gastos.add(gasto);
                }
            }
            oos.writeObject(gastos);
        } catch (IOException e) {
            System.err.println("Erro ao salvar gastos: " + e.getMessage());
        }
    }
    
    @SuppressWarnings("unchecked")
    private void carregarGastos() throws IOException, ClassNotFoundException {
        File file = new File(GASTOS_DB);
        if (!file.exists()) {
            System.out.println("Arquivo de gastos não encontrado: " + GASTOS_DB);
            return;
        }
        
        try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(GASTOS_DB))) {
            List<Gasto> gastos = (List<Gasto>) ois.readObject();
            // System.out.println("Carregando " + gastos.size() + " gastos...");
            
            for (Gasto gasto : gastos) {
                // Inicializa observacoes se for null (compatibilidade com dados antigos)
                if (gasto.getObservacoes() == null) {
                    gasto.setObservacoes(new String[0]);
                }
                
                tabelaGastos.inserir(gasto.getIdGasto(), gasto);
                indiceUsuarioGastos.inserir(gasto.getIdUsuario(), gasto);
                indiceDataGastos.inserir(gasto.getData().hashCode(), gasto);
                // Não insere mais no indiceCategoriaGastos - isso é feito via CategoriaGasto
            }
            // System.out.println("✓ " + gastos.size() + " gastos carregados com sucesso");
        } catch (Exception e) {
            System.err.println("Erro ao carregar gastos: " + e.getMessage());
            e.printStackTrace();
            throw e;
        }
    }
    
    private void salvarCategoriaGasto() {
        try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(CATEGORIA_GASTO_DB))) {
            oos.writeObject(relacionamentosCategoriaGasto);
        } catch (IOException e) {
            System.err.println("Erro ao salvar relacionamentos categoria-gasto: " + e.getMessage());
        }
    }
    
    @SuppressWarnings("unchecked")
    private void carregarCategoriaGasto() throws IOException, ClassNotFoundException {
        carregarCategoriaGasto(true); // Por padrão, reconstrói índices
    }
    
    @SuppressWarnings("unchecked")
    private void carregarCategoriaGasto(boolean reconstruirIndices) throws IOException, ClassNotFoundException {
        File file = new File(CATEGORIA_GASTO_DB);
        if (!file.exists()) return;
        
        try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(CATEGORIA_GASTO_DB))) {
            relacionamentosCategoriaGasto = (List<CategoriaGasto>) ois.readObject();
            
            // Se deve reconstruir índices (quando carregando dados para reconstruir)
            if (reconstruirIndices) {
                for (CategoriaGasto rel : relacionamentosCategoriaGasto) {
                    if (rel.isAtivo()) {
                        indiceCategoriaGastos.inserir(rel.getIdCategoria(), rel);
                        indiceGastoCategorias.inserir(rel.getIdGasto(), rel);
                    }
                }
            }
            // Se não deve reconstruir (quando índices já foram carregados dos arquivos),
            // apenas carrega a lista para ter os objetos em memória
        }
    }
    
    private void salvarReceitas() {
        try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(RECEITAS_DB))) {
            List<Receita> receitas = new ArrayList<>();
            for (int i = 1; i < proximoIdReceita; i++) {
                Receita receita = buscarReceita(i);
                if (receita != null) {
                    receitas.add(receita);
                }
            }
            oos.writeObject(receitas);
        } catch (IOException e) {
            System.err.println("Erro ao salvar receitas: " + e.getMessage());
        }
    }
    
    @SuppressWarnings("unchecked")
    private void carregarReceitas() throws IOException, ClassNotFoundException {
        File file = new File(RECEITAS_DB);
        if (!file.exists()) return;
        
        try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(RECEITAS_DB))) {
            List<Receita> receitas = (List<Receita>) ois.readObject();
            for (Receita receita : receitas) {
                tabelaReceitas.inserir(receita.getIdReceita(), receita);
                indiceUsuarioReceitas.inserir(receita.getIdUsuario(), receita);
                indiceDataReceitas.inserir(receita.getData().hashCode(), receita);
            }
        }
    }
    
    private void salvarContas() {
        try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(CONTAS_DB))) {
            List<Conta> contas = new ArrayList<>();
            for (int i = 1; i < proximoIdConta; i++) {
                Conta conta = buscarConta(i);
                if (conta != null) {
                    contas.add(conta);
                }
            }
            oos.writeObject(contas);
        } catch (IOException e) {
            System.err.println("Erro ao salvar contas: " + e.getMessage());
        }
    }
    
    @SuppressWarnings("unchecked")
    private void carregarContas() throws IOException, ClassNotFoundException {
        File file = new File(CONTAS_DB);
        if (!file.exists()) return;
        
        try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(CONTAS_DB))) {
            List<Conta> contas = (List<Conta>) ois.readObject();
            for (Conta conta : contas) {
                tabelaContas.inserir(conta.getIdConta(), conta);
                indiceUsuarioContas.inserir(conta.getIdUsuario(), conta);
                indiceTipoContas.inserir(conta.getTipo().hashCode(), conta);
            }
        }
    }
    
    private void salvarOrcamentos() {
        try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(ORCAMENTOS_DB))) {
            List<Orcamento> orcamentos = new ArrayList<>();
            for (int i = 1; i < proximoIdOrcamento; i++) {
                Orcamento orcamento = buscarOrcamento(i);
                if (orcamento != null) {
                    orcamentos.add(orcamento);
                }
            }
            oos.writeObject(orcamentos);
        } catch (IOException e) {
            System.err.println("Erro ao salvar orçamentos: " + e.getMessage());
        }
    }
    
    @SuppressWarnings("unchecked")
    private void carregarOrcamentos() throws IOException, ClassNotFoundException {
        File file = new File(ORCAMENTOS_DB);
        if (!file.exists()) return;
        
        try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(ORCAMENTOS_DB))) {
            List<Orcamento> orcamentos = (List<Orcamento>) ois.readObject();
            for (Orcamento orcamento : orcamentos) {
                tabelaOrcamentos.inserir(orcamento.getIdOrcamento(), orcamento);
                indiceUsuarioOrcamentos.inserir(orcamento.getIdUsuario(), orcamento);
                indiceCategoriaOrcamentos.inserir(orcamento.getIdCategoria(), orcamento);
            }
        }
    }
    
    private void salvarTags() {
        try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(TAGS_DB))) {
            List<Tag> tags = new ArrayList<>();
            for (int i = 1; i < proximoIdTag; i++) {
                Tag tag = buscarTag(i);
                if (tag != null) {
                    tags.add(tag);
                }
            }
            oos.writeObject(tags);
        } catch (IOException e) {
            System.err.println("Erro ao salvar tags: " + e.getMessage());
        }
    }
    
    @SuppressWarnings("unchecked")
    private void carregarTags() throws IOException, ClassNotFoundException {
        File file = new File(TAGS_DB);
        if (!file.exists()) return;
        
        try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(TAGS_DB))) {
            List<Tag> tags = (List<Tag>) ois.readObject();
            for (Tag tag : tags) {
                tabelaTags.inserir(tag.getIdTag(), tag);
                indiceUsuarioTags.inserir(tag.getIdUsuario(), tag);
            }
        }
    }
    
    private void salvarTransacaoTag() {
        try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(TRANSACAO_TAG_DB))) {
            List<TransacaoTag> transacoesTags = new ArrayList<>();
            for (TransacaoTag transacaoTag : relacionamentosTransacaoTag) {
                if (transacaoTag.isAtivo()) {
                    transacoesTags.add(transacaoTag);
                }
            }
            oos.writeObject(transacoesTags);
        } catch (IOException e) {
            System.err.println("Erro ao salvar transação-tag: " + e.getMessage());
        }
    }
    
    @SuppressWarnings("unchecked")
    private void carregarTransacaoTag() throws IOException, ClassNotFoundException {
        carregarTransacaoTag(true); // Por padrão, reconstrói índices
    }
    
    @SuppressWarnings("unchecked")
    private void carregarTransacaoTag(boolean reconstruirIndices) throws IOException, ClassNotFoundException {
        File file = new File(TRANSACAO_TAG_DB);
        if (!file.exists()) return;
        
        try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(TRANSACAO_TAG_DB))) {
            List<TransacaoTag> transacoesTags = (List<TransacaoTag>) ois.readObject();
            relacionamentosTransacaoTag = transacoesTags;
            
            // Se deve reconstruir índices (quando carregando dados para reconstruir)
            if (reconstruirIndices) {
                for (TransacaoTag transacaoTag : relacionamentosTransacaoTag) {
                    if (transacaoTag.isAtivo()) {
                        // Reconstrói índices
                        if (transacaoTag.getTipoTransacao().equals("GASTO")) {
                            indiceTagGastos.inserir(transacaoTag.getIdTag(), transacaoTag.getIdTransacao());
                            indiceGastoTags.inserir(transacaoTag.getIdTransacao(), transacaoTag.getIdTag());
                        } else if (transacaoTag.getTipoTransacao().equals("RECEITA")) {
                            indiceTagReceitas.inserir(transacaoTag.getIdTag(), transacaoTag.getIdTransacao());
                            indiceReceitaTags.inserir(transacaoTag.getIdTransacao(), transacaoTag.getIdTag());
                        }
                    }
                }
            }
            // Se não deve reconstruir (quando índices já foram carregados dos arquivos),
            // apenas carrega a lista para ter os objetos em memória
        }
    }
    
    // ========== PERSISTÊNCIA DE ÍNDICES ==========
    
    /**
     * Salva um índice Árvore B+ em arquivo
     */
    private void salvarIndiceArvoreBPlus(ArvoreBPlus arvore, String arquivo) {
        try {
            // Garante que o diretório existe
            File file = new File(arquivo);
            File parentDir = file.getParentFile();
            if (parentDir != null && !parentDir.exists()) {
                parentDir.mkdirs();
            }
            
            try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(arquivo))) {
                List<Registro> registros = arvore.listarRegistros();
                oos.writeObject(registros);
            }
        } catch (IOException e) {
            System.err.println("Erro ao salvar índice " + arquivo + ": " + e.getMessage());
        }
    }
    
    /**
     * Carrega um índice Árvore B+ de arquivo
     */
    @SuppressWarnings("unchecked")
    private void carregarIndiceArvoreBPlus(ArvoreBPlus arvore, String arquivo) {
        File file = new File(arquivo);
        if (!file.exists()) return;
        
        try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(arquivo))) {
            List<Registro> registros = (List<Registro>) ois.readObject();
            for (Registro registro : registros) {
                arvore.inserir(registro.getChave(), registro.getDados());
            }
        } catch (IOException | ClassNotFoundException e) {
            System.err.println("Erro ao carregar índice " + arquivo + ": " + e.getMessage());
        }
    }
    
    /**
     * Salva um índice Hash Extensível em arquivo
     */
    private void salvarIndiceHashExtensivel(HashExtensivel hash, String arquivo) {
        try {
            // Garante que o diretório existe
            File file = new File(arquivo);
            File parentDir = file.getParentFile();
            if (parentDir != null && !parentDir.exists()) {
                parentDir.mkdirs();
            }
            
            try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(arquivo))) {
                List<EntradaHash> entradas = hash.listarEntradas();
                if (entradas == null || entradas.isEmpty()) {
                    // System.out.println("Aviso: Tentando salvar índice vazio: " + arquivo);
                } else {
                    // System.out.println("Salvando índice " + arquivo + " com " + entradas.size() + " entradas");
                }
                oos.writeObject(entradas);
            }
        } catch (IOException e) {
            System.err.println("Erro ao salvar índice " + arquivo + ": " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * Carrega um índice Hash Extensível de arquivo
     */
    @SuppressWarnings("unchecked")
    private void carregarIndiceHashExtensivel(HashExtensivel hash, String arquivo) {
        File file = new File(arquivo);
        if (!file.exists()) return;
        
        try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(arquivo))) {
            List<EntradaHash> entradas = (List<EntradaHash>) ois.readObject();
            for (EntradaHash entrada : entradas) {
                hash.inserir(entrada.getChave(), entrada.getValor());
            }
        } catch (IOException | ClassNotFoundException e) {
            System.err.println("Erro ao carregar índice " + arquivo + ": " + e.getMessage());
        }
    }
    
    /**
     * Salva todos os índices primários (Árvore B+)
     */
    private void salvarTodosIndicesPrimarios() {
        salvarIndiceArvoreBPlus(tabelaUsuarios, IDX_USUARIOS);
        salvarIndiceArvoreBPlus(tabelaCategorias, IDX_CATEGORIAS);
        salvarIndiceArvoreBPlus(tabelaGastos, IDX_GASTOS);
        salvarIndiceArvoreBPlus(tabelaReceitas, IDX_RECEITAS);
        salvarIndiceArvoreBPlus(tabelaContas, IDX_CONTAS);
        salvarIndiceArvoreBPlus(tabelaOrcamentos, IDX_ORCAMENTOS);
        salvarIndiceArvoreBPlus(tabelaTags, IDX_TAGS);
        salvarIndiceArvoreBPlus(tabelaInvestimentos, IDX_INVESTIMENTOS);
    }
    
    /**
     * Carrega todos os índices primários (Árvore B+)
     */
    private void carregarTodosIndicesPrimarios() {
        carregarIndiceArvoreBPlus(tabelaUsuarios, IDX_USUARIOS);
        carregarIndiceArvoreBPlus(tabelaCategorias, IDX_CATEGORIAS);
        carregarIndiceArvoreBPlus(tabelaGastos, IDX_GASTOS);
        carregarIndiceArvoreBPlus(tabelaReceitas, IDX_RECEITAS);
        carregarIndiceArvoreBPlus(tabelaContas, IDX_CONTAS);
        carregarIndiceArvoreBPlus(tabelaOrcamentos, IDX_ORCAMENTOS);
        carregarIndiceArvoreBPlus(tabelaTags, IDX_TAGS);
        carregarIndiceArvoreBPlus(tabelaInvestimentos, IDX_INVESTIMENTOS);
    }
    
    /**
     * Salva todos os índices secundários (Hash Extensível)
     */
    private void salvarTodosIndicesSecundarios() {
        salvarIndiceHashExtensivel(indiceUsuarioCategorias, IDX_USUARIO_CATEGORIAS);
        salvarIndiceHashExtensivel(indiceUsuarioGastos, IDX_USUARIO_GASTOS);
        salvarIndiceHashExtensivel(indiceUsuarioReceitas, IDX_USUARIO_RECEITAS);
        salvarIndiceHashExtensivel(indiceUsuarioContas, IDX_USUARIO_CONTAS);
        salvarIndiceHashExtensivel(indiceUsuarioOrcamentos, IDX_USUARIO_ORCAMENTOS);
        salvarIndiceHashExtensivel(indiceCategoriaOrcamentos, IDX_CATEGORIA_ORCAMENTOS);
        salvarIndiceHashExtensivel(indiceEmailUsuarios, IDX_EMAIL_USUARIOS);
        salvarIndiceHashExtensivel(indiceDataGastos, IDX_DATA_GASTOS);
        salvarIndiceHashExtensivel(indiceDataReceitas, IDX_DATA_RECEITAS);
        salvarIndiceHashExtensivel(indiceTipoContas, IDX_TIPO_CONTAS);
        salvarIndiceHashExtensivel(indiceCategoriaGastos, IDX_CATEGORIA_GASTOS);
        salvarIndiceHashExtensivel(indiceGastoCategorias, IDX_GASTO_CATEGORIAS);
        salvarIndiceHashExtensivel(indiceUsuarioTags, IDX_USUARIO_TAGS);
        salvarIndiceHashExtensivel(indiceTagGastos, IDX_TAG_GASTOS);
        salvarIndiceHashExtensivel(indiceTagReceitas, IDX_TAG_RECEITAS);
        salvarIndiceHashExtensivel(indiceGastoTags, IDX_GASTO_TAGS);
        salvarIndiceHashExtensivel(indiceReceitaTags, IDX_RECEITA_TAGS);
        salvarIndiceHashExtensivel(indiceUsuarioInvestimentos, IDX_USUARIO_INVESTIMENTOS);
    }
    
    /**
     * Carrega todos os índices secundários (Hash Extensível)
     */
    private void carregarTodosIndicesSecundarios() {
        carregarIndiceHashExtensivel(indiceUsuarioCategorias, IDX_USUARIO_CATEGORIAS);
        carregarIndiceHashExtensivel(indiceUsuarioGastos, IDX_USUARIO_GASTOS);
        carregarIndiceHashExtensivel(indiceUsuarioReceitas, IDX_USUARIO_RECEITAS);
        carregarIndiceHashExtensivel(indiceUsuarioContas, IDX_USUARIO_CONTAS);
        carregarIndiceHashExtensivel(indiceUsuarioOrcamentos, IDX_USUARIO_ORCAMENTOS);
        carregarIndiceHashExtensivel(indiceCategoriaOrcamentos, IDX_CATEGORIA_ORCAMENTOS);
        carregarIndiceHashExtensivel(indiceEmailUsuarios, IDX_EMAIL_USUARIOS);
        carregarIndiceHashExtensivel(indiceDataGastos, IDX_DATA_GASTOS);
        carregarIndiceHashExtensivel(indiceDataReceitas, IDX_DATA_RECEITAS);
        carregarIndiceHashExtensivel(indiceTipoContas, IDX_TIPO_CONTAS);
        carregarIndiceHashExtensivel(indiceCategoriaGastos, IDX_CATEGORIA_GASTOS);
        carregarIndiceHashExtensivel(indiceGastoCategorias, IDX_GASTO_CATEGORIAS);
        carregarIndiceHashExtensivel(indiceUsuarioTags, IDX_USUARIO_TAGS);
        carregarIndiceHashExtensivel(indiceTagGastos, IDX_TAG_GASTOS);
        carregarIndiceHashExtensivel(indiceTagReceitas, IDX_TAG_RECEITAS);
        carregarIndiceHashExtensivel(indiceGastoTags, IDX_GASTO_TAGS);
        carregarIndiceHashExtensivel(indiceReceitaTags, IDX_RECEITA_TAGS);
        carregarIndiceHashExtensivel(indiceUsuarioInvestimentos, IDX_USUARIO_INVESTIMENTOS);
    }
    
    // ========== OPERAÇÕES DE INVESTIMENTO ==========
    
    public int cadastrarInvestimento(String nome, String categoria, double quantidade, 
                                    double precoAporte, double corretagem, String corretora,
                                    LocalDate dataAporte, int idUsuario, int idConta, String moeda) {
        return cadastrarInvestimento(nome, null, categoria, quantidade, precoAporte, corretagem, corretora, dataAporte, idUsuario, idConta, moeda);
    }
    
    public int cadastrarInvestimento(String nome, String nomeAtivo, String categoria, double quantidade, 
                                    double precoAporte, double corretagem, String corretora,
                                    LocalDate dataAporte, int idUsuario, int idConta, String moeda) {
        int idInvestimento = proximoIdInvestimento++;
        Investimento investimento = new Investimento(idInvestimento, nome, nomeAtivo, categoria, quantidade,
                                                   precoAporte, corretagem, corretora, dataAporte,
                                                   idUsuario, idConta, moeda);
        
        // Insere na tabela principal
        tabelaInvestimentos.inserir(idInvestimento, investimento);
        
        // Atualiza índice de relacionamento
        indiceUsuarioInvestimentos.inserir(idUsuario, investimento);
        
        // Persiste os dados e índices
        salvarInvestimentos();
        salvarContadores();
        salvarIndiceArvoreBPlus(tabelaInvestimentos, IDX_INVESTIMENTOS);
        salvarIndiceHashExtensivel(indiceUsuarioInvestimentos, IDX_USUARIO_INVESTIMENTOS);
        
        return idInvestimento;
    }
    
    public Investimento buscarInvestimento(int idInvestimento) {
        return (Investimento) tabelaInvestimentos.buscar(idInvestimento);
    }
    
    public List<Investimento> buscarInvestimentosPorUsuario(int idUsuario) {
        List<Object> objetos = indiceUsuarioInvestimentos.buscar(idUsuario);
        List<Investimento> investimentos = new ArrayList<>();
        for (Object obj : objetos) {
            Investimento invIndex = (Investimento) obj;
            // Busca a versão atualizada na tabela principal
            Investimento invReal = buscarInvestimento(invIndex.getIdInvestimento());
            if (invReal != null && invReal.isAtivo()) {
                investimentos.add(invReal);
            }
        }
        return investimentos;
    }
    
    public void atualizarInvestimento(int idInvestimento, String nome, String nomeAtivo, String categoria, 
                                     double quantidade, double precoAporte, double corretagem, 
                                     String corretora, LocalDate dataAporte, String moeda) {
        Investimento investimento = buscarInvestimento(idInvestimento);
        if (investimento == null) {
            throw new RuntimeException("Investimento não encontrado!");
        }
        
        investimento.setNome(nome);
        if (nomeAtivo != null) {
            investimento.setNomeAtivo(nomeAtivo);
        }
        investimento.setCategoria(categoria);
        investimento.setQuantidade(quantidade);
        investimento.setPrecoAporte(precoAporte);
        investimento.setCorretagem(corretagem);
        investimento.setCorretora(corretora);
        investimento.setDataAporte(dataAporte);
        investimento.setMoeda(moeda);
        
        // Atualiza na tabela
        tabelaInvestimentos.inserir(idInvestimento, investimento);
        
        // Persiste
        salvarInvestimentos();
        salvarIndiceArvoreBPlus(tabelaInvestimentos, IDX_INVESTIMENTOS);
    }
    
    public void excluirInvestimento(int idInvestimento) {
        Investimento investimento = buscarInvestimento(idInvestimento);
        if (investimento == null) {
            throw new RuntimeException("Investimento não encontrado!");
        }
        
        // Exclusão lógica
        investimento.setAtivo(false);
        
        // Atualiza na tabela
        tabelaInvestimentos.inserir(idInvestimento, investimento);
        
        // Persiste
        salvarInvestimentos();
        salvarIndiceArvoreBPlus(tabelaInvestimentos, IDX_INVESTIMENTOS);
    }
    
    private void salvarInvestimentos() {
        try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(INVESTIMENTOS_DB))) {
            List<Investimento> investimentos = new ArrayList<>();
            for (int i = 1; i < proximoIdInvestimento; i++) {
                Investimento investimento = buscarInvestimento(i);
                if (investimento != null) {
                    investimentos.add(investimento);
                }
            }
            oos.writeObject(investimentos);
        } catch (IOException e) {
            System.err.println("Erro ao salvar investimentos: " + e.getMessage());
        }
    }
    
    @SuppressWarnings("unchecked")
    private void carregarInvestimentos() throws IOException, ClassNotFoundException {
        File file = new File(INVESTIMENTOS_DB);
        if (!file.exists()) return;
        
        try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(INVESTIMENTOS_DB))) {
            List<Investimento> investimentos = (List<Investimento>) ois.readObject();
            for (Investimento investimento : investimentos) {
                tabelaInvestimentos.inserir(investimento.getIdInvestimento(), investimento);
                indiceUsuarioInvestimentos.inserir(investimento.getIdUsuario(), investimento);
            }
        }
    }
    
    // ========== OPERAÇÕES DE ESTATÍSTICAS ==========
    
    public void imprimirEstatisticas() {
        System.out.println("\n=== ESTATÍSTICAS DO BANCO DE DADOS ===");
        System.out.println("Usuários cadastrados: " + tabelaUsuarios.tamanho());
        System.out.println("Categorias cadastradas: " + tabelaCategorias.tamanho());
        System.out.println("Gastos registrados: " + tabelaGastos.tamanho());
        System.out.println("Receitas registradas: " + tabelaReceitas.tamanho());
        System.out.println("Contas cadastradas: " + tabelaContas.tamanho());
        System.out.println("Orçamentos definidos: " + tabelaOrcamentos.tamanho());
        System.out.println("Investimentos cadastrados: " + tabelaInvestimentos.tamanho());
    }
    
    public void imprimirEstruturasIndices() {
        System.out.println("\n=== ESTRUTURAS DE ÍNDICES ===");
        System.out.println("Tabela Usuários (Árvore B+):");
        tabelaUsuarios.imprimir();
        
        System.out.println("\nÍndice Email Usuários (Hash Extensível):");
        indiceEmailUsuarios.imprimir();
        
        System.out.println("\nÍndice Usuario->Gastos (Hash Extensível):");
        indiceUsuarioGastos.imprimir();
        
        System.out.println("\nÍndice Categoria->Gastos (Hash Extensível):");
        indiceCategoriaGastos.imprimir();
    }
}
