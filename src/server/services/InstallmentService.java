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
     * Retorna a data de fechamento da fatura que contém a cobrança na data informada.
     * Ex.: diaFechamento=15, dataCobranca=2025-02-10 → fecha 2025-02-15; dataCobranca=2025-02-20 → fecha 2025-03-15.
     */
    private static LocalDate fechamentoFaturaParaData(LocalDate dataCobranca, int diaFechamento) {
        int dia = Math.min(diaFechamento, dataCobranca.lengthOfMonth());
        if (dataCobranca.getDayOfMonth() <= dia) {
            return dataCobranca.withDayOfMonth(dia);
        }
        LocalDate proximoMes = dataCobranca.plusMonths(1);
        return proximoMes.withDayOfMonth(Math.min(diaFechamento, proximoMes.lengthOfMonth()));
    }

    /**
     * Cria uma compra parcelada (gasto)
     * Cria o grupo e todas as parcelas de uma vez.
     * Parcelas cuja fatura já fechou (cadastro retroativo) são marcadas como pagas e contabilizadas corretamente.
     */
    public int criarCompraParcelada(String descricao, double valorTotal, int numeroParcelas,
                                    LocalDate dataPrimeiraParcela, int intervaloDias,
                                    int idUsuario, int idConta, List<Integer> idsCategorias,
                                    List<Integer> idsTags, String[] observacoes,
                                    LocalDate dataEntradaFaturaPrimeiraParcela) throws Exception {
        
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
        
        Conta conta = accountRepository.buscarConta(idConta);
        boolean isCartao = conta != null && conta.isCartaoCredito();
        Integer diaFechamento = (conta != null && conta.getDiaFechamento() != null) ? conta.getDiaFechamento() : null;
        
        // Calcula o valor base da parcela (arredondado para 2 casas decimais)
        double valorParcelaBase = Math.round((valorTotal / numeroParcelas) * 100.0) / 100.0;
        double totalParcelasBase = valorParcelaBase * numeroParcelas;
        double diferenca = Math.round((valorTotal - totalParcelasBase) * 100.0) / 100.0;
        
        LocalDate hoje = LocalDate.now();
        
        for (int i = 1; i <= numeroParcelas; i++) {
            LocalDate dataParcela = grupo.calcularDataParcela(i);
            double valorParcela = (i == numeroParcelas) 
                ? Math.round((valorParcelaBase + diferenca) * 100.0) / 100.0
                : valorParcelaBase;
            
            // Primeira parcela pode ter compra retida: data de entrada na fatura
            LocalDate dataEntradaFaturaParcela = (i == 1 && dataEntradaFaturaPrimeiraParcela != null) 
                ? dataEntradaFaturaPrimeiraParcela : null;
            
            int idGasto = expenseRepository.cadastrarGasto(
                descricao,
                valorParcela,
                dataParcela,
                "UNICA",
                idUsuario,
                idsCategorias,
                idConta,
                observacoes,
                dataEntradaFaturaParcela
            );
            
            expenseRepository.atualizarInformacoesParcela(idGasto, idGrupo, i, numeroParcelas);
            
            if (idsTags != null) {
                for (int tagId : idsTags) {
                    tagRepository.associarTagTransacao(idGasto, "GASTO", tagId);
                }
            }
            
            // Cadastro retroativo: parcela cuja fatura já fechou é marcada como paga (já foi contabilizada na vida real)
            if (isCartao && diaFechamento != null) {
                LocalDate dataParaFatura = (i == 1 && dataEntradaFaturaPrimeiraParcela != null) 
                    ? dataEntradaFaturaPrimeiraParcela : dataParcela;
                LocalDate fechamentoFatura = fechamentoFaturaParaData(dataParaFatura, diaFechamento);
                if (hoje.isAfter(fechamentoFatura)) {
                    try {
                        expenseRepository.marcarParcelaComoPaga(idGasto);
                    } catch (Exception e) {
                        LOGGER.warning("Ao marcar parcela " + i + "/" + numeroParcelas + " como já paga (fatura fechada): " + e.getMessage());
                    }
                }
            }
        }
        
        // LOGGER.info("Compra parcelada criada com sucesso: " + numeroParcelas + " parcelas");
        return idGrupo;
    }
    
    /**
     * Atualiza um grupo de compra parcelada (gasto) como um todo: descrição, conta, categorias, tags, observações, data entrada fatura.
     */
    public void atualizarGrupoGasto(int idGrupo, int idUsuario, String descricao, int idConta,
                                   List<Integer> idsCategorias, List<Integer> idsTags,
                                   String[] observacoes, LocalDate dataEntradaFatura) throws Exception {
        List<Gasto> gastos = expenseRepository.buscarGastosPorGrupoParcela(idGrupo);
        if (gastos == null || gastos.isEmpty()) {
            throw new IllegalArgumentException("Grupo de parcelas não encontrado");
        }
        if (gastos.get(0).getIdUsuario() != idUsuario) {
            throw new IllegalArgumentException("Grupo não pertence ao usuário");
        }
        expenseRepository.atualizarGrupoParcelado(idGrupo, idUsuario, descricao, idConta, idsCategorias, observacoes, dataEntradaFatura);
        for (Gasto gasto : gastos) {
            tagRepository.removerTodasTagsTransacao(gasto.getIdGasto(), "GASTO");
            if (idsTags != null) {
                for (int tagId : idsTags) {
                    tagRepository.associarTagTransacao(gasto.getIdGasto(), "GASTO", tagId);
                }
            }
        }
        try {
            installmentRepository.atualizarGrupo(idGrupo, descricao, idConta);
        } catch (Exception e) {
            LOGGER.warning("Erro ao atualizar metadados do grupo (installment_groups): " + e.getMessage());
        }
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

