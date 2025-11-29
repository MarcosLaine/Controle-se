import java.io.*;
import java.sql.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.HashSet;
import java.util.Set;

/**
 * Script de migração de dados dos arquivos .db para PostgreSQL
 * Execute este script uma vez para migrar todos os dados existentes
 */
public class MigracaoDados {
    
    private BancoDados bancoAntigo;
    private BancoDadosPostgreSQL bancoNovo;
    
    public static void main(String[] args) {
        System.out.println("=== MIGRAÇÃO DE DADOS PARA POSTGRESQL ===\n");
        
        try {
            MigracaoDados migracao = new MigracaoDados();
            migracao.executar();
        } catch (Exception e) {
            System.err.println("ERRO na migração: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
    
    public MigracaoDados() {
        System.out.println("Carregando banco de dados antigo (arquivos .db)...");
        bancoAntigo = new BancoDados();
        
        System.out.println("Conectando ao PostgreSQL...");
        bancoNovo = new BancoDadosPostgreSQL();
    }
    
    public void executar() {
        System.out.println("\nIniciando migração...\n");
        
        int total = 0;
        
        // Migra usuários
        total += migrarUsuarios();
        
        // Migra categorias
        total += migrarCategorias();
        
        // Migra contas
        total += migrarContas();
        
        // Migra gastos
        total += migrarGastos();
        
        // Migra receitas
        total += migrarReceitas();
        
        // Migra orçamentos
        total += migrarOrcamentos();
        
        // Migra tags
        total += migrarTags();
        
        // Migra investimentos
        total += migrarInvestimentos();
        
        System.out.println("\n=== MIGRAÇÃO CONCLUÍDA ===");
        System.out.println("Total de registros migrados: " + total);
        System.out.println("\nVerifique os dados no PostgreSQL antes de desativar o banco antigo.");
    }
    
    private int migrarUsuarios() {
        System.out.println("Migrando usuários...");
        int count = 0;
        
        // Busca todos os usuários (precisa iterar pelos IDs)
        for (int i = 1; i < 10000; i++) { // Ajuste o limite conforme necessário
            try {
                Usuario usuario = bancoAntigo.buscarUsuario(i);
                if (usuario == null || !usuario.isAtivo()) {
                    continue;
                }
                
                // Verifica se já existe no novo banco
                if (bancoNovo.buscarUsuarioPorEmail(usuario.getEmail()) != null) {
                    System.out.println("  Usuário " + usuario.getEmail() + " já existe, pulando...");
                    continue;
                }
                
                // Migra o usuário preservando a senha criptografada
                // Nota: As senhas no banco antigo já estão criptografadas com RSA
                // Usamos método especial que aceita senha já criptografada
                try {
                    bancoNovo.cadastrarUsuarioComSenhaCriptografada(
                        usuario.getNome(), 
                        usuario.getEmail(), 
                        usuario.getSenha() // Senha já criptografada - preserva original
                    );
                    count++;
                    System.out.println("  ✓ Usuário migrado: " + usuario.getEmail() + " (senha preservada)");
                } catch (Exception e2) {
                    // Se falhar, tenta método normal (senha temporária)
                    System.out.println("  AVISO: Não foi possível preservar senha para " + usuario.getEmail() + ", usando senha temporária");
                    try {
                        bancoNovo.cadastrarUsuario(usuario.getNome(), usuario.getEmail(), 
                            "migrado_" + System.currentTimeMillis());
                        count++;
                        System.out.println("  ✓ Usuário migrado: " + usuario.getEmail() + " (senha temporária - usuário precisa redefinir)");
                    } catch (Exception e3) {
                        System.err.println("  ERRO ao migrar usuário " + usuario.getEmail() + ": " + e3.getMessage());
                    }
                }
            } catch (Exception e) {
                // Usuário não existe ou erro - continua
            }
        }
        
        System.out.println("  Total: " + count + " usuários migrados\n");
        return count;
    }
    
    private int migrarCategorias() {
        System.out.println("Migrando categorias...");
        int count = 0;
        
        // Busca todos os usuários primeiro
        List<Integer> usuarios = new ArrayList<>();
        for (int i = 1; i < 10000; i++) {
            try {
                Usuario u = bancoAntigo.buscarUsuario(i);
                if (u != null && u.isAtivo()) {
                    usuarios.add(i);
                }
            } catch (Exception e) {}
        }
        
        for (int idUsuario : usuarios) {
            try {
                List<Categoria> categorias = bancoAntigo.buscarCategoriasPorUsuario(idUsuario);
                
                // Busca o usuário correspondente no novo banco
                Usuario usuarioAntigo = bancoAntigo.buscarUsuario(idUsuario);
                if (usuarioAntigo == null) continue;
                
                Usuario usuarioNovo = bancoNovo.buscarUsuarioPorEmail(usuarioAntigo.getEmail());
                if (usuarioNovo == null) continue;
                
                for (Categoria cat : categorias) {
                    try {
                        bancoNovo.cadastrarCategoria(cat.getNome(), usuarioNovo.getIdUsuario());
                        count++;
                    } catch (Exception eCat) {
                        System.err.println("  ERRO ao migrar categoria '" + cat.getNome() + "': " + eCat.getMessage());
                        // Continua com a próxima categoria
                    }
                }
            } catch (Exception e) {
                System.err.println("  Erro ao migrar categorias do usuário " + idUsuario + ": " + e.getMessage());
            }
        }
        
        System.out.println("  Total: " + count + " categorias migradas\n");
        return count;
    }
    
    private int migrarContas() {
        System.out.println("Migrando contas...");
        int count = 0;
        
        List<Integer> usuarios = new ArrayList<>();
        for (int i = 1; i < 10000; i++) {
            try {
                Usuario u = bancoAntigo.buscarUsuario(i);
                if (u != null && u.isAtivo()) {
                    usuarios.add(i);
                }
            } catch (Exception e) {}
        }
        
        for (int idUsuario : usuarios) {
            try {
                List<Conta> contas = bancoAntigo.buscarContasPorUsuario(idUsuario);
                
                Usuario usuarioAntigo = bancoAntigo.buscarUsuario(idUsuario);
                if (usuarioAntigo == null) continue;
                
                Usuario usuarioNovo = bancoNovo.buscarUsuarioPorEmail(usuarioAntigo.getEmail());
                if (usuarioNovo == null) continue;
                
                for (Conta conta : contas) {
                    try {
                        bancoNovo.cadastrarConta(conta.getNome(), conta.getTipo(), 
                            conta.getSaldoAtual(), usuarioNovo.getIdUsuario());
                        count++;
                    } catch (Exception eConta) {
                        System.err.println("  ERRO ao migrar conta '" + conta.getNome() + "': " + eConta.getMessage());
                        // Continua com a próxima conta
                    }
                }
            } catch (Exception e) {
                System.err.println("  Erro ao migrar contas do usuário " + idUsuario + ": " + e.getMessage());
            }
        }
        
        System.out.println("  Total: " + count + " contas migradas\n");
        return count;
    }
    
    private int migrarGastos() {
        System.out.println("Migrando gastos...");
        int count = 0;
        
        List<Integer> usuarios = new ArrayList<>();
        for (int i = 1; i < 10000; i++) {
            try {
                Usuario u = bancoAntigo.buscarUsuario(i);
                if (u != null && u.isAtivo()) {
                    usuarios.add(i);
                }
            } catch (Exception e) {}
        }
        
        for (int idUsuario : usuarios) {
            try {
                List<Gasto> gastos = bancoAntigo.buscarGastosPorUsuario(idUsuario);
                
                Usuario usuarioAntigo = bancoAntigo.buscarUsuario(idUsuario);
                if (usuarioAntigo == null) continue;
                
                Usuario usuarioNovo = bancoNovo.buscarUsuarioPorEmail(usuarioAntigo.getEmail());
                if (usuarioNovo == null) continue;
                
                List<Conta> contasNovo = bancoNovo.buscarContasPorUsuario(usuarioNovo.getIdUsuario());
                if (contasNovo.isEmpty()) continue;
                
                for (Gasto gasto : gastos) {
                    try {
                        // Busca categorias do gasto
                        List<Categoria> categorias = bancoAntigo.buscarCategoriasDoGasto(gasto.getIdGasto());
                        List<Integer> idsCategorias = new ArrayList<>();
                        
                        for (Categoria cat : categorias) {
                            // Busca categoria correspondente no novo banco
                            List<Categoria> catsNovo = bancoNovo.buscarCategoriasPorUsuario(usuarioNovo.getIdUsuario());
                            for (Categoria catNovo : catsNovo) {
                                if (catNovo.getNome().equals(cat.getNome())) {
                                    idsCategorias.add(catNovo.getIdCategoria());
                                    break;
                                }
                            }
                        }
                        
                        // Usa a primeira conta disponível (ou mapeia por nome)
                        int idContaNovo = contasNovo.get(0).getIdConta();
                        for (Conta conta : contasNovo) {
                            Conta contaAntiga = bancoAntigo.buscarConta(gasto.getIdConta());
                            if (contaAntiga != null && conta.getNome().equals(contaAntiga.getNome())) {
                                idContaNovo = conta.getIdConta();
                                break;
                            }
                        }
                        
                        bancoNovo.cadastrarGasto(
                            gasto.getDescricao(),
                            gasto.getValor(),
                            gasto.getData(),
                            gasto.getFrequencia(),
                            usuarioNovo.getIdUsuario(),
                            idsCategorias,
                            idContaNovo,
                            gasto.getObservacoes()
                        );
                        count++;
                    } catch (Exception eGasto) {
                        System.err.println("  ERRO ao migrar gasto '" + gasto.getDescricao() + "': " + eGasto.getMessage());
                        // Continua com o próximo gasto
                    }
                }
            } catch (Exception e) {
                System.err.println("  Erro ao migrar gastos do usuário " + idUsuario + ": " + e.getMessage());
                e.printStackTrace();
            }
        }
        
        System.out.println("  Total: " + count + " gastos migrados\n");
        return count;
    }
    
    private int migrarReceitas() {
        System.out.println("Migrando receitas...");
        int count = 0;
        
        List<Integer> usuarios = new ArrayList<>();
        for (int i = 1; i < 10000; i++) {
            try {
                Usuario u = bancoAntigo.buscarUsuario(i);
                if (u != null && u.isAtivo()) {
                    usuarios.add(i);
                }
            } catch (Exception e) {}
        }
        
        for (int idUsuario : usuarios) {
            try {
                List<Receita> receitas = bancoAntigo.buscarReceitasPorUsuario(idUsuario);
                
                Usuario usuarioAntigo = bancoAntigo.buscarUsuario(idUsuario);
                if (usuarioAntigo == null) continue;
                
                Usuario usuarioNovo = bancoNovo.buscarUsuarioPorEmail(usuarioAntigo.getEmail());
                if (usuarioNovo == null) continue;
                
                List<Conta> contasNovo = bancoNovo.buscarContasPorUsuario(usuarioNovo.getIdUsuario());
                if (contasNovo.isEmpty()) continue;
                
                for (Receita receita : receitas) {
                    try {
                        int idContaNovo = contasNovo.get(0).getIdConta();
                        for (Conta conta : contasNovo) {
                            Conta contaAntiga = bancoAntigo.buscarConta(receita.getIdConta());
                            if (contaAntiga != null && conta.getNome().equals(contaAntiga.getNome())) {
                                idContaNovo = conta.getIdConta();
                                break;
                            }
                        }
                        
                        bancoNovo.cadastrarReceita(
                            receita.getDescricao(),
                            receita.getValor(),
                            receita.getData(),
                            usuarioNovo.getIdUsuario(),
                            idContaNovo
                        );
                        count++;
                    } catch (Exception eRec) {
                        System.err.println("  ERRO ao migrar receita '" + receita.getDescricao() + "': " + eRec.getMessage());
                        // Continua com a próxima receita
                    }
                }
            } catch (Exception e) {
                System.err.println("  Erro ao migrar receitas do usuário " + idUsuario + ": " + e.getMessage());
            }
        }
        
        System.out.println("  Total: " + count + " receitas migradas\n");
        return count;
    }
    
    private int migrarOrcamentos() {
        System.out.println("Migrando orçamentos...");
        int count = 0;
        
        List<Integer> usuarios = new ArrayList<>();
        for (int i = 1; i < 10000; i++) {
            try {
                Usuario u = bancoAntigo.buscarUsuario(i);
                if (u != null && u.isAtivo()) {
                    usuarios.add(i);
                }
            } catch (Exception e) {}
        }
        
        for (int idUsuario : usuarios) {
            try {
                List<Orcamento> orcamentos = bancoAntigo.buscarOrcamentosPorUsuario(idUsuario);
                
                Usuario usuarioAntigo = bancoAntigo.buscarUsuario(idUsuario);
                if (usuarioAntigo == null) continue;
                
                Usuario usuarioNovo = bancoNovo.buscarUsuarioPorEmail(usuarioAntigo.getEmail());
                if (usuarioNovo == null) continue;
                
                for (Orcamento orc : orcamentos) {
                    try {
                        Categoria catAntiga = bancoAntigo.buscarCategoria(orc.getIdCategoria());
                        if (catAntiga == null) {
                            System.err.println("  AVISO: Categoria não encontrada para orçamento, pulando...");
                            continue;
                        }
                        
                        List<Categoria> catsNovo = bancoNovo.buscarCategoriasPorUsuario(usuarioNovo.getIdUsuario());
                        Integer idCatNovo = null;
                        for (Categoria catNovo : catsNovo) {
                            if (catNovo.getNome().equals(catAntiga.getNome())) {
                                idCatNovo = catNovo.getIdCategoria();
                                break;
                            }
                        }
                        
                        if (idCatNovo != null) {
                            bancoNovo.cadastrarOrcamento(
                                orc.getValorPlanejado(),
                                orc.getPeriodo(),
                                idCatNovo,
                                usuarioNovo.getIdUsuario()
                            );
                            count++;
                        } else {
                            System.err.println("  AVISO: Categoria '" + catAntiga.getNome() + "' não encontrada no novo banco para orçamento");
                        }
                    } catch (Exception eOrc) {
                        System.err.println("  ERRO ao migrar orçamento: " + eOrc.getMessage());
                        // Continua com o próximo orçamento
                    }
                }
            } catch (Exception e) {
                System.err.println("  Erro ao migrar orçamentos do usuário " + idUsuario + ": " + e.getMessage());
            }
        }
        
        System.out.println("  Total: " + count + " orçamentos migrados\n");
        return count;
    }
    
    private int migrarTags() {
        System.out.println("Migrando tags...");
        int count = 0;
        
        List<Integer> usuarios = new ArrayList<>();
        for (int i = 1; i < 10000; i++) {
            try {
                Usuario u = bancoAntigo.buscarUsuario(i);
                if (u != null && u.isAtivo()) {
                    usuarios.add(i);
                }
            } catch (Exception e) {}
        }
        
        for (int idUsuario : usuarios) {
            try {
                List<Tag> tags = bancoAntigo.buscarTagsPorUsuario(idUsuario);
                
                Usuario usuarioAntigo = bancoAntigo.buscarUsuario(idUsuario);
                if (usuarioAntigo == null) continue;
                
                Usuario usuarioNovo = bancoNovo.buscarUsuarioPorEmail(usuarioAntigo.getEmail());
                if (usuarioNovo == null) continue;
                
                for (Tag tag : tags) {
                    try {
                        bancoNovo.cadastrarTag(tag.getNome(), tag.getCor(), usuarioNovo.getIdUsuario());
                        count++;
                    } catch (Exception eTag) {
                        System.err.println("  ERRO ao migrar tag '" + tag.getNome() + "': " + eTag.getMessage());
                        // Continua com a próxima tag
                    }
                }
            } catch (Exception e) {
                System.err.println("  Erro ao migrar tags do usuário " + idUsuario + ": " + e.getMessage());
            }
        }
        
        System.out.println("  Total: " + count + " tags migradas\n");
        return count;
    }
    
    private int migrarInvestimentos() {
        System.out.println("Migrando investimentos...");
        int count = 0;
        Set<String> investimentosJaMigrados = new HashSet<>(); // Para evitar duplicatas
        
        // Tenta ler TODOS os investimentos diretamente do arquivo serializado
        // Isso garante que nenhum investimento seja perdido
        List<Investimento> todosInvestimentos = new ArrayList<>();
        Set<Integer> idsLidosDoArquivo = new HashSet<>();
        
        try {
            java.io.File investimentosFile = new java.io.File("data/investimentos.db");
            if (investimentosFile.exists()) {
                System.out.println("  Lendo investimentos diretamente do arquivo data/investimentos.db...");
                try (java.io.ObjectInputStream ois = new java.io.ObjectInputStream(
                        new java.io.FileInputStream(investimentosFile))) {
                    @SuppressWarnings("unchecked")
                    List<Investimento> investimentosArquivo = (List<Investimento>) ois.readObject();
                    todosInvestimentos.addAll(investimentosArquivo);
                    for (Investimento inv : investimentosArquivo) {
                        idsLidosDoArquivo.add(inv.getIdInvestimento());
                    }
                    System.out.println("  Encontrados " + investimentosArquivo.size() + " investimentos no arquivo");
                } catch (Exception e) {
                    System.err.println("  AVISO: Não foi possível ler arquivo investimentos.db: " + e.getMessage());
                }
            }
        } catch (Exception e) {
            System.err.println("  AVISO: Erro ao acessar arquivo investimentos.db: " + e.getMessage());
        }
        
        // Busca investimentos adicionais que podem não estar no arquivo
        // (investimentos que foram adicionados mas não salvos, ou que estão na árvore mas não no arquivo)
        System.out.println("  Buscando investimentos adicionais por ID (que podem não estar no arquivo)...");
        int encontradosAdicionais = 0;
        for (int idInv = 1; idInv < 50000; idInv++) {
            try {
                // Se já foi lido do arquivo, pula
                if (idsLidosDoArquivo.contains(idInv)) {
                    continue;
                }
                
                Investimento inv = bancoAntigo.buscarInvestimento(idInv);
                if (inv != null) {
                    todosInvestimentos.add(inv);
                    encontradosAdicionais++;
                    System.out.println("  ✓ Investimento adicional encontrado (ID " + idInv + "): " + inv.getNome());
                }
            } catch (Exception e) {
                // Investimento não existe, continua
            }
        }
        
        if (encontradosAdicionais > 0) {
            System.out.println("  Encontrados " + encontradosAdicionais + " investimentos adicionais que não estavam no arquivo");
        }
        
        System.out.println("  Total de investimentos encontrados no banco antigo: " + todosInvestimentos.size());
        
        // Mapeia usuários e contas
        List<Integer> usuarios = new ArrayList<>();
        for (int i = 1; i < 10000; i++) {
            try {
                Usuario u = bancoAntigo.buscarUsuario(i);
                if (u != null && u.isAtivo()) {
                    usuarios.add(i);
                }
            } catch (Exception e) {}
        }
        
        java.util.Map<Integer, Integer> mapaUsuarios = new java.util.HashMap<>();
        java.util.Map<Integer, Integer> mapaContas = new java.util.HashMap<>();
        
        for (int idUsuario : usuarios) {
            try {
                Usuario usuarioAntigo = bancoAntigo.buscarUsuario(idUsuario);
                if (usuarioAntigo == null) continue;
                
                Usuario usuarioNovo = bancoNovo.buscarUsuarioPorEmail(usuarioAntigo.getEmail());
                if (usuarioNovo != null) {
                    mapaUsuarios.put(idUsuario, usuarioNovo.getIdUsuario());
                    
                    // Mapeia contas
                    List<Conta> contasAntigas = bancoAntigo.buscarContasPorUsuario(idUsuario);
                    List<Conta> contasNovas = bancoNovo.buscarContasPorUsuario(usuarioNovo.getIdUsuario());
                    
                    for (Conta contaAntiga : contasAntigas) {
                        for (Conta contaNova : contasNovas) {
                            if (contaNova.getNome().equals(contaAntiga.getNome())) {
                                mapaContas.put(contaAntiga.getIdConta(), contaNova.getIdConta());
                                break;
                            }
                        }
                    }
                }
            } catch (Exception e) {}
        }
        
        // Migra todos os investimentos encontrados
        for (Investimento inv : todosInvestimentos) {
            try {
                // Cria chave única para evitar duplicatas
                String chave = inv.getIdUsuario() + "|" + inv.getNome() + "|" + inv.getDataAporte() + "|" + inv.getQuantidade() + "|" + inv.getPrecoAporte();
                if (investimentosJaMigrados.contains(chave)) {
                    continue; // Já foi migrado nesta execução, pula
                }
                
                Integer idUsuarioNovo = mapaUsuarios.get(inv.getIdUsuario());
                if (idUsuarioNovo == null) {
                    System.err.println("  AVISO: Usuário " + inv.getIdUsuario() + " não encontrado no novo banco para investimento '" + inv.getNome() + "'");
                    continue;
                }
                
                // Verifica se já existe no banco novo
                try {
                    List<Investimento> investimentosExistentes = bancoNovo.buscarInvestimentosPorUsuario(idUsuarioNovo);
                    boolean jaExiste = false;
                    for (Investimento invExistente : investimentosExistentes) {
                        if (invExistente.getNome().equals(inv.getNome()) &&
                            invExistente.getDataAporte().equals(inv.getDataAporte()) &&
                            Math.abs(invExistente.getQuantidade() - inv.getQuantidade()) < 0.0001 &&
                            Math.abs(invExistente.getPrecoAporte() - inv.getPrecoAporte()) < 0.0001) {
                            jaExiste = true;
                            break;
                        }
                    }
                    if (jaExiste) {
                        System.out.println("  Investimento '" + inv.getNome() + "' já existe no banco novo, pulando...");
                        continue;
                    }
                } catch (Exception e) {
                    // Se não conseguir verificar, continua tentando migrar
                }
                
                Integer idContaNovo = mapaContas.get(inv.getIdConta());
                if (idContaNovo == null) {
                    // Usa primeira conta disponível como fallback
                    List<Conta> contasNovo = bancoNovo.buscarContasPorUsuario(idUsuarioNovo);
                    if (contasNovo.isEmpty()) {
                        System.err.println("  AVISO: Nenhuma conta encontrada para investimento '" + inv.getNome() + "'");
                        continue;
                    }
                    idContaNovo = contasNovo.get(0).getIdConta();
                }
                
                // Migra o investimento
                bancoNovo.cadastrarInvestimento(
                    inv.getNome(),
                    inv.getNomeAtivo(),
                    inv.getCategoria(),
                    inv.getQuantidade(),
                    inv.getPrecoAporte(),
                    inv.getCorretagem(),
                    inv.getCorretora(),
                    inv.getDataAporte(),
                    idUsuarioNovo,
                    idContaNovo,
                    inv.getMoeda(),
                    inv.getTipoInvestimento(),
                    inv.getTipoRentabilidade(),
                    inv.getIndice(),
                    inv.getPercentualIndice(),
                    inv.getTaxaFixa(),
                    inv.getDataVencimento()
                );
                count++;
                investimentosJaMigrados.add(chave); // Marca como migrado
                System.out.println("  ✓ Investimento migrado (ID " + inv.getIdInvestimento() + "): " + inv.getNome() + " (" + inv.getCategoria() + ")");
            } catch (Exception eInv) {
                System.err.println("  ERRO ao migrar investimento ID " + inv.getIdInvestimento() + " '" + inv.getNome() + "': " + eInv.getMessage());
            }
        }
        
        System.out.println("  Total encontrados no banco antigo: " + todosInvestimentos.size());
        System.out.println("  Total migrados: " + count);
        System.out.println("  Total: " + count + " investimentos migrados\n");
        return count;
    }
}

