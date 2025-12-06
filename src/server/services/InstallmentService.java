package server.services;

import server.model.InstallmentGroup;
import server.model.Gasto;
import server.model.Receita;
import server.model.Conta;
import server.repository.InstallmentRepository;
import server.repository.ExpenseRepository;
import server.repository.IncomeRepository;
import server.repository.CategoryRepository;
import server.repository.TagRepository;
import server.repository.AccountRepository;
import server.model.Categoria;
import server.model.Tag;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

/**
 * Serviço para gerenciar compras parceladas e receitas parceladas
 */
public class InstallmentService {
    private static final Logger LOGGER = Logger.getLogger(InstallmentService.class.getName());
    private final InstallmentRepository installmentRepository;
    private final ExpenseRepository expenseRepository;
    private final IncomeRepository incomeRepository;
    private final CategoryRepository categoryRepository;
    private final TagRepository tagRepository;
    private final AccountRepository accountRepository;
    
    public InstallmentService() {
        this.installmentRepository = new InstallmentRepository();
        this.expenseRepository = new ExpenseRepository();
        this.incomeRepository = new IncomeRepository();
        this.tagRepository = new TagRepository();
        this.categoryRepository = new CategoryRepository();
        this.accountRepository = new AccountRepository();
    }
    
    /**
     * Cria uma compra parcelada (gasto)
     * Cria o grupo e todas as parcelas de uma vez
     */
    public int criarCompraParcelada(String descricao, double valorTotal, int numeroParcelas,
                                    LocalDate dataPrimeiraParcela, int intervaloDias,
                                    int idUsuario, int idConta, List<Integer> idsCategorias,
                                    List<Integer> idsTags, String[] observacoes) throws Exception {
        
        // Validações
        if (numeroParcelas < 2) {
            throw new IllegalArgumentException("Número de parcelas deve ser pelo menos 2");
        }
        if (valorTotal <= 0) {
            throw new IllegalArgumentException("Valor total deve ser maior que zero");
        }
        if (intervaloDias <= 0) {
            throw new IllegalArgumentException("Intervalo entre parcelas deve ser maior que zero");
        }
        
        // Cria o grupo de parcelas
        InstallmentGroup grupo = new InstallmentGroup(
            descricao, valorTotal, numeroParcelas, dataPrimeiraParcela,
            intervaloDias, idUsuario, idConta, "GASTO"
        );
        
        int idGrupo = installmentRepository.criarGrupoParcelas(grupo);
        grupo.setIdGrupo(idGrupo);
        
        // LOGGER.info("Criando compra parcelada: " + descricao + " - " + numeroParcelas + "x de R$ " + grupo.getValorParcela());
        
        // Calcula o valor base da parcela (arredondado para 2 casas decimais)
        double valorParcelaBase = Math.round((valorTotal / numeroParcelas) * 100.0) / 100.0;
        // Calcula o total que será cobrado com as parcelas base
        double totalParcelasBase = valorParcelaBase * numeroParcelas;
        // Calcula a diferença (pode ser positiva ou negativa devido ao arredondamento)
        double diferenca = Math.round((valorTotal - totalParcelasBase) * 100.0) / 100.0;
        
        // Cria cada parcela
        for (int i = 1; i <= numeroParcelas; i++) {
            LocalDate dataParcela = grupo.calcularDataParcela(i);
            // Mantém a descrição original sem adicionar (X/Y)
            
            // A última parcela recebe a diferença para garantir que a soma seja exatamente o valor total
            double valorParcela = (i == numeroParcelas) 
                ? Math.round((valorParcelaBase + diferenca) * 100.0) / 100.0
                : valorParcelaBase;
            
            // Cria o gasto da parcela
            int idGasto = expenseRepository.cadastrarGasto(
                descricao,
                valorParcela,
                dataParcela,
                "UNICA", // Parcelas não são recorrências
                idUsuario,
                idsCategorias,
                idConta,
                observacoes
            );
            
            // Atualiza o gasto com informações da parcela
            expenseRepository.atualizarInformacoesParcela(idGasto, idGrupo, i, numeroParcelas);
            
            // Associa tags se houver
            if (idsTags != null) {
                for (int tagId : idsTags) {
                    tagRepository.associarTagTransacao(idGasto, "GASTO", tagId);
                }
            }
            
            // Se a parcela já passou (data no passado ou hoje), marca automaticamente como paga
            // Isso faz o estorno automático do valor ao cartão de crédito
            LocalDate hoje = LocalDate.now();
            if (!dataParcela.isAfter(hoje)) { // Se a data não é futura (passado ou hoje)
                Conta conta = accountRepository.buscarConta(idConta);
                // Verifica se é cartão de crédito de forma mais robusta
                boolean isCartao = false;
                if (conta != null && conta.getTipo() != null) {
                    String tipoLower = conta.getTipo().toLowerCase().trim();
                    isCartao = tipoLower.contains("cartao") || tipoLower.contains("cartão") || 
                               tipoLower.contains("credito") || tipoLower.contains("crédito") ||
                               tipoLower.equals("cartao_credito");
                }
                
                if (isCartao) {
                    try {
                        // Marca a parcela como paga e estorna o saldo ao cartão de crédito
                        // O método marcarParcelaComoPaga já faz a verificação e o estorno
                        expenseRepository.marcarParcelaComoPaga(idGasto);
                        // LOGGER.info("Parcela " + i + "/" + numeroParcelas + " marcada automaticamente como paga (data passada: " + dataParcela + ") - Valor estornado: R$ " + valorParcela);
                    } catch (Exception e) {
                        LOGGER.severe("ERRO CRÍTICO ao marcar parcela " + i + " como paga automaticamente: " + e.getMessage());
                        e.printStackTrace();
                        // Re-lança a exceção para garantir que o problema seja visível
                        throw new RuntimeException("Erro ao processar parcela passada automaticamente: " + e.getMessage(), e);
                    }
                } else {
                    LOGGER.warning("Parcela " + i + "/" + numeroParcelas + " não marcada como paga - conta " + idConta + " não é cartão de crédito (tipo: " + (conta != null ? conta.getTipo() : "null") + ")");
                }
            }
        }
        
        // LOGGER.info("Compra parcelada criada com sucesso: " + numeroParcelas + " parcelas");
        return idGrupo;
    }
    
    /**
     * Cria uma receita parcelada
     */
    public int criarReceitaParcelada(String descricao, double valorTotal, int numeroParcelas,
                                     LocalDate dataPrimeiraParcela, int intervaloDias,
                                     int idUsuario, int idConta, List<Integer> idsTags,
                                     String[] observacoes) throws Exception {
        
        // Validações
        if (numeroParcelas < 2) {
            throw new IllegalArgumentException("Número de parcelas deve ser pelo menos 2");
        }
        if (valorTotal <= 0) {
            throw new IllegalArgumentException("Valor total deve ser maior que zero");
        }
        if (intervaloDias <= 0) {
            throw new IllegalArgumentException("Intervalo entre parcelas deve ser maior que zero");
        }
        
        // Cria o grupo de parcelas
        InstallmentGroup grupo = new InstallmentGroup(
            descricao, valorTotal, numeroParcelas, dataPrimeiraParcela,
            intervaloDias, idUsuario, idConta, "RECEITA"
        );
        
        int idGrupo = installmentRepository.criarGrupoParcelas(grupo);
        grupo.setIdGrupo(idGrupo);
        
        // LOGGER.info("Criando receita parcelada: " + descricao + " - " + numeroParcelas + "x de R$ " + grupo.getValorParcela());
        
        // Calcula o valor base da parcela (arredondado para 2 casas decimais)
        double valorParcelaBase = Math.round((valorTotal / numeroParcelas) * 100.0) / 100.0;
        // Calcula o total que será cobrado com as parcelas base
        double totalParcelasBase = valorParcelaBase * numeroParcelas;
        // Calcula a diferença (pode ser positiva ou negativa devido ao arredondamento)
        double diferenca = Math.round((valorTotal - totalParcelasBase) * 100.0) / 100.0;
        
        // Cria cada parcela
        for (int i = 1; i <= numeroParcelas; i++) {
            LocalDate dataParcela = grupo.calcularDataParcela(i);
            // Mantém a descrição original sem adicionar (X/Y)
            
            // A última parcela recebe a diferença para garantir que a soma seja exatamente o valor total
            double valorParcela = (i == numeroParcelas) 
                ? Math.round((valorParcelaBase + diferenca) * 100.0) / 100.0
                : valorParcelaBase;
            
            // Cria a receita da parcela
            int idReceita = incomeRepository.cadastrarReceita(
                descricao,
                valorParcela,
                dataParcela,
                idUsuario,
                idConta,
                observacoes
            );
            
            // Atualiza a receita com informações da parcela
            incomeRepository.atualizarInformacoesParcela(idReceita, idGrupo, i, numeroParcelas);
            
            // Associa tags se houver
            if (idsTags != null) {
                for (int tagId : idsTags) {
                    tagRepository.associarTagTransacao(idReceita, "RECEITA", tagId);
                }
            }
        }
        
        LOGGER.info("Receita parcelada criada com sucesso: " + numeroParcelas + " parcelas");
        return idGrupo;
    }
    
    /**
     * Lista todas as parcelas de um grupo
     */
    public List<Object> listarParcelasDoGrupo(int idGrupo, String tipoTransacao) throws Exception {
        InstallmentGroup grupo = installmentRepository.buscarGrupoPorId(idGrupo);
        if (grupo == null) {
            throw new IllegalArgumentException("Grupo de parcelas não encontrado");
        }
        
        List<Object> parcelas = new ArrayList<>();
        
        if ("GASTO".equals(tipoTransacao)) {
            List<Gasto> gastos = expenseRepository.buscarGastosPorGrupoParcela(idGrupo);
            parcelas.addAll(gastos);
        } else if ("RECEITA".equals(tipoTransacao)) {
            List<Receita> receitas = incomeRepository.buscarReceitasPorGrupoParcela(idGrupo);
            parcelas.addAll(receitas);
        }
        
        return parcelas;
    }
    
    /**
     * Cancela parcelas futuras de um grupo
     */
    public int cancelarParcelasFuturas(int idGrupo, LocalDate dataLimite) throws Exception {
        InstallmentGroup grupo = installmentRepository.buscarGrupoPorId(idGrupo);
        if (grupo == null) {
            throw new IllegalArgumentException("Grupo de parcelas não encontrado");
        }
        
        int canceladas = 0;
        
        if ("GASTO".equals(grupo.getTipoTransacao())) {
            canceladas = expenseRepository.cancelarParcelasFuturas(idGrupo, dataLimite);
        } else if ("RECEITA".equals(grupo.getTipoTransacao())) {
            canceladas = incomeRepository.cancelarParcelasFuturas(idGrupo, dataLimite);
        }
        
        LOGGER.info("Canceladas " + canceladas + " parcelas futuras do grupo " + idGrupo);
        return canceladas;
    }
}

