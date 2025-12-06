package server.handlers;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import server.model.*;
import server.repository.*;
import server.services.ImportTransactionsService;
import server.services.InstallmentService;
import server.utils.*;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.*;

public class ImportTransactionsHandler implements HttpHandler {
    private final ImportTransactionsService importService;
    private final ExpenseRepository expenseRepository;
    private final IncomeRepository incomeRepository;
    private final TagRepository tagRepository;
    private final AccountRepository accountRepository;
    private final InstallmentService installmentService;
    
    public ImportTransactionsHandler() {
        this.importService = new ImportTransactionsService();
        this.expenseRepository = new ExpenseRepository();
        this.incomeRepository = new IncomeRepository();
        this.tagRepository = new TagRepository();
        this.accountRepository = new AccountRepository();
        this.installmentService = new InstallmentService();
    }
    
    @Override
    public void handle(HttpExchange exchange) throws IOException {
        try {
            String method = exchange.getRequestMethod();
            if (method != null) {
                method = method.toUpperCase().trim();
            }
            String path = exchange.getRequestURI().getPath();
            
            if ("OPTIONS".equals(method)) {
                handleOptions(exchange);
            } else if ("GET".equals(method)) {
                if (path != null && path.contains("/template")) {
                    // Template não precisa de autenticação
                    handleGetTemplate(exchange);
                    return;
                } else {
                    ResponseUtil.sendErrorResponse(exchange, 404, "Endpoint não encontrado");
                }
            } else if ("POST".equals(method)) {
                if (path != null && path.contains("/confirm")) {
                    handleConfirm(exchange);
                } else {
                    handleImport(exchange);
                }
            } else {
                ResponseUtil.sendErrorResponse(exchange, 405, "Método não permitido: " + method);
            }
        } catch (AuthUtil.UnauthorizedException e) {
            ResponseUtil.sendErrorResponse(exchange, 401, e.getMessage());
        } catch (Exception e) {
            e.printStackTrace();
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "Erro: " + e.getMessage());
            ResponseUtil.sendJsonResponse(exchange, 500, response);
        }
    }
    
    private void handleOptions(HttpExchange exchange) throws IOException {
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
        exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type, Authorization");
        exchange.sendResponseHeaders(200, -1);
        exchange.close();
    }
    
    /**
     * Endpoint para importar CSV (processa e retorna preview)
     * Requer autenticação
     */
    private void handleImport(HttpExchange exchange) throws IOException {
        try {
            int userId = AuthUtil.requireUserId(exchange);
            String requestBody = RequestUtil.readRequestBody(exchange);
            Map<String, Object> data = JsonUtil.parseJsonWithNested(requestBody);
            
            Object csvContentObj = data.get("csvContent");
            String csvContent = null;
            
            if (csvContentObj != null) {
                if (csvContentObj instanceof String) {
                    csvContent = (String) csvContentObj;
                    // Desescapar quebras de linha que podem ter sido escapadas no JSON
                    csvContent = csvContent.replace("\\n", "\n").replace("\\r", "\r");
                } else {
                    csvContent = csvContentObj.toString();
                }
            }
            
            if (csvContent == null || csvContent.trim().isEmpty()) {
                Map<String, Object> response = new HashMap<>();
                response.put("success", false);
                response.put("message", "Conteúdo CSV não fornecido");
                ResponseUtil.sendJsonResponse(exchange, 400, response);
                return;
            }
            
            // Processa o CSV
            ImportTransactionsService.ImportResult result = importService.processCSV(csvContent, userId);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", result.isSuccess());
            response.put("transactions", result.getTransactions());
            response.put("errors", result.getErrors());
            response.put("count", result.getTransactions().size());
            
            ResponseUtil.sendJsonResponse(exchange, 200, response);
        } catch (Exception e) {
            e.printStackTrace();
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "Erro ao processar CSV: " + e.getMessage());
            ResponseUtil.sendJsonResponse(exchange, 500, response);
        }
    }
    
    /**
     * Endpoint para confirmar e salvar transações
     * Requer autenticação
     */
    private void handleConfirm(HttpExchange exchange) throws IOException {
        try {
            int userId = AuthUtil.requireUserId(exchange);
            String requestBody = RequestUtil.readRequestBody(exchange);
            Map<String, Object> data = JsonUtil.parseJsonWithNested(requestBody);
            
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> transactions = (List<Map<String, Object>>) data.get("transactions");
            
            if (transactions == null || transactions.isEmpty()) {
                Map<String, Object> response = new HashMap<>();
                response.put("success", false);
                response.put("message", "Nenhuma transação fornecida");
                ResponseUtil.sendJsonResponse(exchange, 400, response);
                return;
            }
            
            int successCount = 0;
            int errorCount = 0;
            List<String> errors = new ArrayList<>();
            
            for (Map<String, Object> transaction : transactions) {
                try {
                    String type = (String) transaction.get("type");
                    if ("gasto".equals(type)) {
                        createExpense(transaction, userId);
                    } else if ("receita".equals(type)) {
                        createIncome(transaction, userId);
                    }
                    successCount++;
                } catch (Exception e) {
                    errorCount++;
                    String descricao = (String) transaction.getOrDefault("description", "Desconhecido");
                    errors.add(String.format("Erro ao criar transação '%s': %s", descricao, e.getMessage()));
                }
            }
            
            // Invalida cache - invalida todos os caches relacionados ao usuário
            CacheUtil.invalidateCache("overview_" + userId);
            CacheUtil.invalidateCache("categories_" + userId);
            CacheUtil.invalidateCache("totalExpense_" + userId);
            CacheUtil.invalidateCache("totalIncome_" + userId);
            CacheUtil.invalidateCache("balance_" + userId);
            CacheUtil.invalidateCache("recent-transactions-" + userId);
            CacheUtil.invalidateCache("totalCredito_" + userId);
            CacheUtil.invalidateCache("totalAccounts_" + userId);
            CacheUtil.invalidateCache("investmentAccounts_" + userId);
            CacheUtil.invalidateCache("accounts_" + userId);
            // Invalida cache de contas para forçar recálculo das informações da fatura
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", errorCount == 0);
            response.put("successCount", successCount);
            response.put("errorCount", errorCount);
            response.put("errors", errors);
            response.put("message", String.format("Importação concluída: %d sucesso(s), %d erro(s)", successCount, errorCount));
            
            ResponseUtil.sendJsonResponse(exchange, 200, response);
        } catch (Exception e) {
            e.printStackTrace();
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "Erro ao confirmar importação: " + e.getMessage());
            ResponseUtil.sendJsonResponse(exchange, 500, response);
        }
    }
    
    /**
     * Cria um gasto a partir dos dados da transação
     */
    private void createExpense(Map<String, Object> transaction, int userId) {
        String description = (String) transaction.get("description");
        double value = ((Number) transaction.get("value")).doubleValue();
        LocalDate date = LocalDate.parse((String) transaction.get("date"));
        int accountId = ((Number) transaction.get("accountId")).intValue();
        
        @SuppressWarnings("unchecked")
        List<Integer> categoryIds = (List<Integer>) transaction.getOrDefault("categoryIds", new ArrayList<>());
        @SuppressWarnings("unchecked")
        List<Integer> tagIds = (List<Integer>) transaction.getOrDefault("tagIds", new ArrayList<>());
        
        String[] observacoes = null;
        Object obsObj = transaction.get("observacoes");
        if (obsObj instanceof List) {
            @SuppressWarnings("unchecked")
            List<Object> obsList = (List<Object>) obsObj;
            observacoes = obsList.stream()
                .map(Object::toString)
                .toArray(String[]::new);
        } else if (obsObj instanceof String[]) {
            observacoes = (String[]) obsObj;
        }
        
        LocalDate dataEntradaFatura = null;
        Object dataEntradaFaturaObj = transaction.get("dataEntradaFatura");
        if (dataEntradaFaturaObj != null && !dataEntradaFaturaObj.toString().trim().isEmpty()) {
            dataEntradaFatura = LocalDate.parse((String) dataEntradaFaturaObj);
        }
        
        String frequency = (String) transaction.getOrDefault("frequency", "UNICA");
        
        // Verifica se é compra parcelada
        Boolean isParcelado = null;
        Object isParceladoObj = transaction.get("isParcelado");
        if (isParceladoObj != null) {
            if (isParceladoObj instanceof Boolean) {
                isParcelado = (Boolean) isParceladoObj;
            } else if (isParceladoObj instanceof String) {
                String str = ((String) isParceladoObj).toLowerCase().trim();
                isParcelado = str.equals("true") || str.equals("1") || str.equals("sim") || str.equals("verdadeiro");
            } else if (isParceladoObj instanceof Number) {
                isParcelado = ((Number) isParceladoObj).intValue() != 0;
            }
        }
        if (isParcelado != null && isParcelado) {
            Integer numeroParcelas = ((Number) transaction.get("numeroParcelas")).intValue();
            Integer intervaloDias = ((Number) transaction.getOrDefault("intervaloDias", 30)).intValue();
            
            // Usa a data da compra original como primeira parcela
            // As parcelas serão criadas mantendo o dia da compra (ex: compra em 05/10, parcelas em 05/10, 05/11, 05/12, etc.)
            // O cálculo de qual fatura cada parcela pertence será feito baseado na data da parcela
            LocalDate dataPrimeiraParcela = date;
            
            try {
                installmentService.criarCompraParcelada(
                    description, value, numeroParcelas, dataPrimeiraParcela, intervaloDias,
                    userId, accountId, categoryIds, tagIds, observacoes
                );
            } catch (Exception e) {
                throw new RuntimeException("Erro ao criar compra parcelada: " + e.getMessage(), e);
            }
        } else {
            // Gasto único ou recorrência
            int expenseId = expenseRepository.cadastrarGasto(
                description, value, date, frequency, userId, categoryIds, accountId, observacoes, dataEntradaFatura
            );
            
            // Associa tags
            for (int tagId : tagIds) {
                tagRepository.associarTagTransacao(expenseId, "GASTO", tagId);
            }
        }
    }
    
    /**
     * Cria uma receita a partir dos dados da transação
     */
    private void createIncome(Map<String, Object> transaction, int userId) {
        String description = (String) transaction.get("description");
        double value = ((Number) transaction.get("value")).doubleValue();
        LocalDate date = LocalDate.parse((String) transaction.get("date"));
        int accountId = ((Number) transaction.get("accountId")).intValue();
        
        @SuppressWarnings("unchecked")
        List<Integer> tagIds = (List<Integer>) transaction.getOrDefault("tagIds", new ArrayList<>());
        
        String[] observacoes = null;
        Object obsObj = transaction.get("observacoes");
        if (obsObj instanceof List) {
            @SuppressWarnings("unchecked")
            List<Object> obsList = (List<Object>) obsObj;
            observacoes = obsList.stream()
                .map(Object::toString)
                .toArray(String[]::new);
        } else if (obsObj instanceof String[]) {
            observacoes = (String[]) obsObj;
        }
        
        int incomeId = incomeRepository.cadastrarReceita(description, value, date, userId, accountId, observacoes);
        
        // Associa tags
        for (int tagId : tagIds) {
            tagRepository.associarTagTransacao(incomeId, "RECEITA", tagId);
        }
        
        // Se for pagamento de fatura, processa transferência
        Boolean pagamentoFatura = null;
        Object pagamentoFaturaObj = transaction.get("pagamentoFatura");
        if (pagamentoFaturaObj != null) {
            if (pagamentoFaturaObj instanceof Boolean) {
                pagamentoFatura = (Boolean) pagamentoFaturaObj;
            } else if (pagamentoFaturaObj instanceof String) {
                String str = ((String) pagamentoFaturaObj).toLowerCase().trim();
                pagamentoFatura = str.equals("true") || str.equals("1") || str.equals("sim") || str.equals("verdadeiro");
            } else if (pagamentoFaturaObj instanceof Number) {
                pagamentoFatura = ((Number) pagamentoFaturaObj).intValue() != 0;
            }
        }
        if (pagamentoFatura != null && pagamentoFatura) {
            Object contaOrigemIdObj = transaction.get("contaOrigemId");
            if (contaOrigemIdObj != null) {
                int contaOrigemId = ((Number) contaOrigemIdObj).intValue();
                accountRepository.decrementarSaldo(contaOrigemId, value);
            }
        }
    }
    
    /**
     * Endpoint para baixar template CSV (público, não precisa autenticação)
     */
    private void handleGetTemplate(HttpExchange exchange) throws IOException {
        try {
            // Template é público, não precisa de autenticação
            // Cria template CSV com exemplos
            StringBuilder csv = new StringBuilder();
            
            // Cabeçalho
            csv.append("tipo,descricao,valor,data,conta,tipo_conta,categoria,tags,observacoes,numero_parcelas,intervalo_dias,frequencia,data_entrada_fatura,pagamento_fatura,conta_origem\n");
            
            // Exemplos
            csv.append("gasto,Supermercado,150.50,2024-01-15,Conta Corrente,corrente,Alimentação,compras,Compra mensal,1,30,UNICA,,,\n");
            csv.append("gasto,Notebook,3000.00,2024-01-20,Cartão Nubank,cartao_credito,Eletrônicos,compras,Compra parcelada,12,30,UNICA,,,\n");
            csv.append("gasto,Netflix,29.90,2024-01-01,Cartão Itau,cartao_credito,Entretenimento,assinatura,Assinatura mensal,1,30,MENSAL,,,\n");
            csv.append("gasto,Compra retida,500.00,2024-01-10,Cartão Visa,cartao_credito,Compras,,,1,30,UNICA,2024-02-15,,,\n");
            csv.append("receita,Salário,5000.00,2024-01-05,Conta Corrente,corrente,,trabalho,Salário mensal,1,30,MENSAL,,,\n");
            csv.append("receita,Pagamento Fatura,1500.00,2024-01-25,Cartão Nubank,cartao_credito,,,Pagamento da fatura,1,30,UNICA,,sim,Conta Corrente\n");
            
            String csvContent = csv.toString();
            byte[] csvBytes = csvContent.getBytes(StandardCharsets.UTF_8);
            
            exchange.getResponseHeaders().set("Content-Type", "text/csv; charset=UTF-8");
            exchange.getResponseHeaders().set("Content-Disposition", "attachment; filename=transactions_import_template.csv");
            exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
            exchange.sendResponseHeaders(200, csvBytes.length);
            
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(csvBytes);
            }
        } catch (Exception e) {
            e.printStackTrace();
            ResponseUtil.sendErrorResponse(exchange, 500, "Erro ao gerar template: " + e.getMessage());
        }
    }
}

