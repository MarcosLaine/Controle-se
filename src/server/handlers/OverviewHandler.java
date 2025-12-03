package server.handlers;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import server.model.*;
import server.repository.*;
import server.utils.*;
import server.services.QuoteService;

import java.io.IOException;
import java.time.LocalDate;
import java.util.*;

public class OverviewHandler implements HttpHandler {
    private final ExpenseRepository expenseRepository;
    private final IncomeRepository incomeRepository;
    private final AccountRepository accountRepository;
    private final CategoryRepository categoryRepository;
    private final InvestmentRepository investmentRepository;

    public OverviewHandler() {
        this.expenseRepository = new ExpenseRepository();
        this.incomeRepository = new IncomeRepository();
        this.accountRepository = new AccountRepository();
        this.categoryRepository = new CategoryRepository();
        this.investmentRepository = new InvestmentRepository();
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        if (!"GET".equals(exchange.getRequestMethod())) {
            ResponseUtil.sendErrorResponse(exchange, 405, "Método não permitido");
            return;
        }
        
        try {
            int userId = AuthUtil.requireUserId(exchange);
            
            // Cache de valores totais (TTL curto pois podem mudar com frequência)
            String cacheKeyIncome = "totalIncome_" + userId;
            String cacheKeyExpense = "totalExpense_" + userId;
            String cacheKeyBalance = "balance_" + userId;
            String cacheKeyCredito = "totalCredito_" + userId;
            
            Double totalIncome = CacheUtil.getCached(cacheKeyIncome);
            Double totalExpense = CacheUtil.getCached(cacheKeyExpense);
            Double balance = CacheUtil.getCached(cacheKeyBalance);
            Double totalCreditoDisponivel = CacheUtil.getCached(cacheKeyCredito);
            
            if (totalIncome == null) {
                totalIncome = incomeRepository.calcularTotalReceitasUsuario(userId);
                CacheUtil.setCached(cacheKeyIncome, totalIncome);
            }
            if (totalExpense == null) {
                totalExpense = expenseRepository.calcularTotalGastosUsuario(userId);
                CacheUtil.setCached(cacheKeyExpense, totalExpense);
            }
            if (balance == null) {
                // Saldo = (Conta Corrente + Dinheiro + Poupança) - Gastos
                // Garante que totalExpense não seja null
                double gastos = totalExpense != null ? totalExpense : expenseRepository.calcularTotalGastosUsuario(userId);
                balance = accountRepository.calcularSaldoDisponivel(userId, gastos);
                CacheUtil.setCached(cacheKeyBalance, balance);
            }
            if (totalCreditoDisponivel == null) {
                totalCreditoDisponivel = accountRepository.calcularTotalCreditoDisponivelCartoes(userId);
                CacheUtil.setCached(cacheKeyCredito, totalCreditoDisponivel);
            }
            
            // Calcula informações agregadas de faturas de cartões de crédito
            String cacheKeyAccounts = "accounts_" + userId;
            List<Conta> allAccounts = CacheUtil.getCached(cacheKeyAccounts);
            if (allAccounts == null) {
                allAccounts = accountRepository.buscarContasPorUsuario(userId);
                CacheUtil.setCached(cacheKeyAccounts, allAccounts);
            }
            
            List<Conta> contasCartao = new ArrayList<>(allAccounts);
            contasCartao.removeIf(c -> !c.isCartaoCredito() || c.getDiaFechamento() == null || c.getDiaPagamento() == null);
            
            Map<String, Object> cartoesInfo = null;
            Double valorFaturaAPagar = null;
            if (!contasCartao.isEmpty()) {
                LocalDate proximoPagamentoMaisProximo = null;
                LocalDate proximoFechamentoMaisProximo = null;
                long menorDiasAtePagamento = Long.MAX_VALUE;
                long menorDiasAteFechamento = Long.MAX_VALUE;
                
                for (Conta cartao : contasCartao) {
                    Map<String, Object> faturaInfo = CreditCardUtil.calcularInfoFatura(cartao.getDiaFechamento(), cartao.getDiaPagamento());
                    long diasAtePagamento = (Long) faturaInfo.get("diasAtePagamento");
                    long diasAteFechamento = (Long) faturaInfo.get("diasAteFechamento");
                    
                    // Calcula o valor da fatura a pagar para este cartão
                    LocalDate ultimoFechamento = LocalDate.parse((String) faturaInfo.get("ultimoFechamento"));
                    LocalDate proximoFechamento = LocalDate.parse((String) faturaInfo.get("proximoFechamento"));
                    
                    double valorFaturaAtual = expenseRepository.calcularValorFaturaAtual(
                        cartao.getIdConta(), 
                        ultimoFechamento, 
                        proximoFechamento
                    );
                    
                    double totalJaPago = incomeRepository.calcularTotalPagoFatura(
                        userId, 
                        cartao.getIdConta(), 
                        ultimoFechamento, 
                        proximoFechamento,
                        expenseRepository
                    );
                    
                    double valorAPagar = valorFaturaAtual - totalJaPago;
                    if (valorAPagar < 0) {
                        valorAPagar = 0;
                    }
                    
                    // Encontra a fatura mais próxima do fechamento (menor diasAteFechamento)
                    if (diasAteFechamento < menorDiasAteFechamento) {
                        menorDiasAteFechamento = diasAteFechamento;
                        menorDiasAtePagamento = diasAtePagamento;
                        proximoPagamentoMaisProximo = LocalDate.parse((String) faturaInfo.get("proximoPagamento"));
                        proximoFechamentoMaisProximo = LocalDate.parse((String) faturaInfo.get("proximoFechamento"));
                        valorFaturaAPagar = valorAPagar;
                    }
                }
                
                if (proximoPagamentoMaisProximo != null && proximoFechamentoMaisProximo != null) {
                    cartoesInfo = new HashMap<>();
                    cartoesInfo.put("proximoPagamento", proximoPagamentoMaisProximo.toString());
                    cartoesInfo.put("proximoFechamento", proximoFechamentoMaisProximo.toString());
                    cartoesInfo.put("diasAtePagamento", menorDiasAtePagamento);
                    cartoesInfo.put("diasAteFechamento", menorDiasAteFechamento);
                }
            }
            
            // Calcula valor total dos investimentos (com cache)
            String cacheKeyInvestments = "totalInvestments_" + userId;
            Double totalInvestmentsValue = CacheUtil.getCached(cacheKeyInvestments);
            
            if (totalInvestmentsValue == null) {
                totalInvestmentsValue = 0.0;
                List<Investimento> investments = investmentRepository.buscarInvestimentosPorUsuario(userId);
                
                if (!investments.isEmpty()) {
                    QuoteService quoteService = QuoteService.getInstance();
                    
                    for (Investimento inv : investments) {
                        QuoteService.QuoteResult quote = quoteService.getQuote(inv.getNome(), inv.getCategoria(), null);
                        double currentPrice = quote != null && quote.success ? quote.price : inv.getPrecoAporte();
                        
                        if (quote != null && quote.success && !"BRL".equals(quote.currency)) {
                            double exchangeRate = quoteService.getExchangeRate(quote.currency, "BRL");
                            currentPrice *= exchangeRate;
                        } else if (!"BRL".equals(inv.getMoeda())) {
                            double exchangeRate = quoteService.getExchangeRate(inv.getMoeda(), "BRL");
                            currentPrice *= exchangeRate;
                        }
                        
                        totalInvestmentsValue += inv.getQuantidade() * currentPrice;
                    }
                }
                
                CacheUtil.setCached(cacheKeyInvestments, totalInvestmentsValue);
            }
            
            String cacheKeyTotalAccounts = "totalAccounts_" + userId;
            Double totalAccounts = CacheUtil.getCached(cacheKeyTotalAccounts);
            if (totalAccounts == null) {
                totalAccounts = accountRepository.calcularTotalSaldoContasUsuario(userId);
                CacheUtil.setCached(cacheKeyTotalAccounts, totalAccounts);
            }
            
            // Calcula o saldo das contas de investimento (que já inclui o valor atual dos investimentos)
            String cacheKeyInvestmentAccounts = "investmentAccounts_" + userId;
            Double investmentAccountsBalance = CacheUtil.getCached(cacheKeyInvestmentAccounts);
            if (investmentAccountsBalance == null) {
                investmentAccountsBalance = 0.0;
                // Reutiliza a lista de contas já carregada anteriormente
                for (Conta conta : allAccounts) {
                    String tipoConta = conta.getTipo() != null ? conta.getTipo().toLowerCase().trim() : "";
                    if (tipoConta.equals("investimento") || tipoConta.equals("investimento (corretora)") || tipoConta.startsWith("investimento")) {
                        investmentAccountsBalance += accountRepository.calcularValorAtualInvestimentos(conta.getIdConta());
                    }
                }
                CacheUtil.setCached(cacheKeyInvestmentAccounts, investmentAccountsBalance);
            }
            
            // O netWorth agora inclui: contas normais + saldo das contas de investimento (que já inclui os investimentos)
            // Não somamos totalInvestmentsValue novamente para evitar duplicação
            double netWorth = totalAccounts + investmentAccountsBalance;
            
            // Get category breakdown
            String cacheKey = "categories_" + userId;
            List<Categoria> categories = CacheUtil.getCached(cacheKey);
            if (categories == null) {
                categories = categoryRepository.buscarCategoriasPorUsuario(userId);
                CacheUtil.setCached(cacheKey, categories);
            }
            
            Map<Integer, Double> gastosPorCategoria = categoryRepository.calcularTotalGastosPorTodasCategoriasEUsuario(userId);
            
            int idCategoriaSemCategoria = categoryRepository.obterOuCriarCategoriaSemCategoria(userId);
            Categoria categoriaSemCategoria = categoryRepository.buscarCategoria(idCategoriaSemCategoria);
            
            List<Map<String, Object>> categoryBreakdown = new ArrayList<>();
            for (Categoria categoria : categories) {
                Double categoryTotal = gastosPorCategoria.get(categoria.getIdCategoria());
                if (categoryTotal != null && categoryTotal > 0) {
                    Map<String, Object> categoryData = new HashMap<>();
                    categoryData.put("name", categoria.getNome());
                    categoryData.put("value", categoryTotal);
                    categoryBreakdown.add(categoryData);
                }
            }
            
            if (categoriaSemCategoria != null) {
                Double semCategoriaTotal = gastosPorCategoria.get(idCategoriaSemCategoria);
                if (semCategoriaTotal != null && semCategoriaTotal > 0) {
                    Map<String, Object> categoryData = new HashMap<>();
                    categoryData.put("name", categoriaSemCategoria.getNome());
                    categoryData.put("value", semCategoriaTotal);
                    categoryBreakdown.add(categoryData);
                }
            }
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            Map<String, Object> dataMap = new HashMap<>();
            dataMap.put("totalIncome", totalIncome);
            dataMap.put("totalExpense", totalExpense);
            dataMap.put("balance", balance);
            dataMap.put("netWorth", netWorth);
            dataMap.put("totalCreditoDisponivel", totalCreditoDisponivel);
            if (valorFaturaAPagar != null) {
                dataMap.put("valorFaturaAPagar", valorFaturaAPagar);
            }
            if (cartoesInfo != null) {
                dataMap.put("cartoesInfo", cartoesInfo);
            }
            dataMap.put("categoryBreakdown", categoryBreakdown);
            response.put("data", dataMap);
            
            ResponseUtil.sendJsonResponse(exchange, 200, response);
        } catch (AuthUtil.UnauthorizedException e) {
            ResponseUtil.sendErrorResponse(exchange, 401, e.getMessage());
        } catch (Exception e) {
            e.printStackTrace();
            ResponseUtil.sendErrorResponse(exchange, 500, "Erro interno do servidor");
        }
    }
}

