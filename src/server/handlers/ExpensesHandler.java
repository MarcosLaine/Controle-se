package server.handlers;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import server.model.*;
import server.repository.*;
import server.services.InstallmentService;
import server.utils.*;
import java.io.IOException;
import java.time.LocalDate;
import java.util.*;

public class ExpensesHandler implements HttpHandler {
    private final ExpenseRepository expenseRepository;
    private final AccountRepository accountRepository;
    private final TagRepository tagRepository;
    private final IncomeRepository incomeRepository;

    public ExpensesHandler() {
        this.expenseRepository = new ExpenseRepository();
        this.accountRepository = new AccountRepository();
        this.tagRepository = new TagRepository();
        this.incomeRepository = new IncomeRepository();
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
                // Verifica se é busca de parcelas de um grupo
                if (path != null && path.contains("/installments")) {
                    handleGetInstallments(exchange);
                } else {
                    ResponseUtil.sendErrorResponse(exchange, 404, "Endpoint não encontrado");
                }
            } else if ("POST".equals(method)) {
                // Verifica se é pagamento antecipado de parcela
                if (path != null && path.contains("/pay-installment")) {
                    handlePayInstallment(exchange);
                } else {
                    handlePost(exchange);
                }
            } else if ("DELETE".equals(method)) {
                handleDelete(exchange);
            } else {
                ResponseUtil.sendErrorResponse(exchange, 405, "Método não permitido: " + method);
            }
        } catch (AuthUtil.UnauthorizedException e) {
            ResponseUtil.sendErrorResponse(exchange, 401, e.getMessage());
        }
    }
    
    private void handleOptions(HttpExchange exchange) throws IOException {
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
        exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type, Authorization");
        exchange.sendResponseHeaders(200, -1);
        exchange.close();
    }
    
    private void handlePost(HttpExchange exchange) throws IOException {
        try {
            int userId = AuthUtil.requireUserId(exchange);
            String requestBody = RequestUtil.readRequestBody(exchange);
            Map<String, Object> data = JsonUtil.parseJsonWithNested(requestBody);
            data.put("userId", userId);
            
            String description = (String) data.get("description");
            double value = ((Number) data.get("value")).doubleValue();
            LocalDate date = LocalDate.parse((String) data.get("date"));
            int accountId = ((Number) data.get("accountId")).intValue();
            String frequency = (String) data.get("frequency");
            
            List<Integer> categoryIds = parseCategoryIds(data);
            List<Integer> tagIds = parseTagIds(data);
            String[] observacoes = parseObservacoes(data);
            
            // Extrai dataEntradaFatura se fornecida
            LocalDate dataEntradaFatura = null;
            Object dataEntradaFaturaObj = data.get("dataEntradaFatura");
            if (dataEntradaFaturaObj != null && !dataEntradaFaturaObj.toString().trim().isEmpty()) {
                try {
                    dataEntradaFatura = LocalDate.parse((String) dataEntradaFaturaObj);
                } catch (Exception e) {
                    // Ignora se a data não for válida
                }
            }
            
            Conta conta = accountRepository.buscarConta(accountId);
            if (conta == null) {
                Map<String, Object> response = new HashMap<>();
                response.put("success", false);
                response.put("message", "Conta não encontrada");
                ResponseUtil.sendJsonResponse(exchange, 400, response);
                return;
            }
            
            // Valida que a conta pertence ao usuário autenticado
            if (conta.getIdUsuario() != userId) {
                Map<String, Object> response = new HashMap<>();
                response.put("success", false);
                response.put("message", "Você não tem permissão para usar esta conta");
                ResponseUtil.sendJsonResponse(exchange, 403, response);
                return;
            }
            
            String tipoConta = conta.getTipo() != null ? conta.getTipo().toLowerCase().trim() : "";
            if (tipoConta.equals("investimento") || tipoConta.equals("investimento (corretora)") || tipoConta.startsWith("investimento")) {
                Map<String, Object> response = new HashMap<>();
                response.put("success", false);
                response.put("message", "Contas de investimento não podem ser usadas para gastos");
                ResponseUtil.sendJsonResponse(exchange, 400, response);
                return;
            }
            
            // Verifica se é uma compra parcelada
            Object numeroParcelasObj = data.get("numeroParcelas");
            if (numeroParcelasObj != null) {
                int numeroParcelas = ((Number) numeroParcelasObj).intValue();
                if (numeroParcelas > 1) {
                    // É uma compra parcelada
                    Object intervaloDiasObj = data.get("intervaloDias");
                    int intervaloDias = intervaloDiasObj != null ? ((Number) intervaloDiasObj).intValue() : 30;
                    
                    // A data do gasto permanece a original (date)
                    // A lógica de cálculo da fatura usa a data da compra original (data_primeira_parcela do InstallmentGroup)
                    // para determinar em qual fatura a parcela aparece
                    InstallmentService installmentService = new InstallmentService();
                    int idGrupo = installmentService.criarCompraParcelada(
                        description, value, numeroParcelas, date, intervaloDias,
                        userId, accountId, categoryIds, tagIds, observacoes
                    );
                    
                    CacheUtil.invalidateCache("overview_" + userId);
                    CacheUtil.invalidateCache("categories_" + userId);
                    
                    Map<String, Object> response = new HashMap<>();
                    response.put("success", true);
                    response.put("message", "Compra parcelada registrada com sucesso");
                    response.put("idGrupo", idGrupo);
                    response.put("numeroParcelas", numeroParcelas);
                    ResponseUtil.sendJsonResponse(exchange, 201, response);
                    return;
                }
            }
            
            // Gasto único ou recorrência periódica
            int expenseId = expenseRepository.cadastrarGasto(description, value, date, frequency, userId, categoryIds, accountId, observacoes, dataEntradaFatura);
            
            for (int tagId : tagIds) {
                tagRepository.associarTagTransacao(expenseId, "GASTO", tagId);
            }
            
            CacheUtil.invalidateCache("overview_" + userId);
            CacheUtil.invalidateCache("categories_" + userId);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Gasto registrado com sucesso");
            response.put("expenseId", expenseId);
            
            ResponseUtil.sendJsonResponse(exchange, 201, response);
        } catch (AuthUtil.UnauthorizedException e) {
            ResponseUtil.sendErrorResponse(exchange, 401, e.getMessage());
        } catch (Exception e) {
            e.printStackTrace();
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "Erro: " + e.getMessage());
            ResponseUtil.sendJsonResponse(exchange, 400, response);
        }
    }
    
    private void handleGetInstallments(HttpExchange exchange) throws IOException {
        try {
            int userId = AuthUtil.requireUserId(exchange);
            String groupIdParam = RequestUtil.getQueryParam(exchange, "groupId");
            
            if (groupIdParam == null || groupIdParam.isEmpty()) {
                Map<String, Object> response = new HashMap<>();
                response.put("success", false);
                response.put("message", "groupId é obrigatório");
                ResponseUtil.sendJsonResponse(exchange, 400, response);
                return;
            }
            
            int groupId = Integer.parseInt(groupIdParam);
            
            // Busca todas as parcelas do grupo (incluindo pagas)
            List<Gasto> gastos = expenseRepository.buscarGastosPorGrupoParcela(groupId);
            
            // Valida que o grupo pertence ao usuário
            if (!gastos.isEmpty() && gastos.get(0).getIdUsuario() != userId) {
                Map<String, Object> response = new HashMap<>();
                response.put("success", false);
                response.put("message", "Acesso negado");
                ResponseUtil.sendJsonResponse(exchange, 403, response);
                return;
            }
            
            // Busca categorias, tags e observações
            List<Integer> idsGastos = new ArrayList<>();
            for (Gasto gasto : gastos) {
                idsGastos.add(gasto.getIdGasto());
            }
            
            Map<Integer, List<Categoria>> categoriasPorGasto = new HashMap<>();
            Map<Integer, List<Tag>> tagsPorGasto = new HashMap<>();
            Map<Integer, String[]> observacoesPorGasto = new HashMap<>();
            
            if (!idsGastos.isEmpty()) {
                CategoryRepository categoryRepository = new CategoryRepository();
                TagRepository tagRepository = new TagRepository();
                
                categoriasPorGasto = categoryRepository.buscarCategoriasPorGastos(idsGastos);
                tagsPorGasto = tagRepository.buscarTagsDeGastos(idsGastos);
                observacoesPorGasto = expenseRepository.buscarObservacoesDeGastos(idsGastos);
            }
            
            // Formata as parcelas como transações
            List<Map<String, Object>> transactions = new ArrayList<>();
            AccountRepository accountRepository = new AccountRepository();
            Map<Integer, Conta> contasMap = new HashMap<>();
            
            for (Gasto gasto : gastos) {
                if (!contasMap.containsKey(gasto.getIdConta())) {
                    Conta conta = accountRepository.buscarConta(gasto.getIdConta());
                    if (conta != null) {
                        contasMap.put(gasto.getIdConta(), conta);
                    }
                }
                
                List<Categoria> categorias = categoriasPorGasto.getOrDefault(gasto.getIdGasto(), new ArrayList<>());
                List<String> nomesCategorias = new ArrayList<>();
                List<Integer> idsCategorias = new ArrayList<>();
                
                for (Categoria cat : categorias) {
                    nomesCategorias.add(cat.getNome());
                    idsCategorias.add(cat.getIdCategoria());
                }
                
                String categoriasStr = nomesCategorias.isEmpty() ? "Sem Categoria" : String.join(", ", nomesCategorias);
                
                List<Tag> tags = tagsPorGasto.getOrDefault(gasto.getIdGasto(), new ArrayList<>());
                List<Map<String, Object>> tagsList = new ArrayList<>();
                for (Tag tag : tags) {
                    Map<String, Object> tagMap = new HashMap<>();
                    tagMap.put("idTag", tag.getIdTag());
                    tagMap.put("nome", tag.getNome());
                    tagMap.put("cor", tag.getCor());
                    tagsList.add(tagMap);
                }
                
                String[] observacoes = observacoesPorGasto.getOrDefault(gasto.getIdGasto(), new String[0]);
                List<String> observacoesList = new ArrayList<>();
                for (String obs : observacoes) {
                    observacoesList.add(obs);
                }
                
                Map<String, Object> transaction = new HashMap<>();
                transaction.put("id", gasto.getIdGasto());
                transaction.put("type", "expense");
                transaction.put("description", gasto.getDescricao());
                transaction.put("value", gasto.getValor());
                transaction.put("date", gasto.getData().toString());
                transaction.put("category", categoriasStr);
                transaction.put("categoryIds", idsCategorias);
                transaction.put("categories", nomesCategorias);
                transaction.put("tags", tagsList);
                transaction.put("observacoes", observacoesList);
                transaction.put("ativo", gasto.isAtivo());
                
                // Campo de data de entrada na fatura (para compras retidas)
                if (gasto.getDataEntradaFatura() != null) {
                    transaction.put("dataEntradaFatura", gasto.getDataEntradaFatura().toString());
                    Conta conta = contasMap.get(gasto.getIdConta());
                    if (conta != null && conta.isCartaoCredito()) {
                        transaction.put("accountId", conta.getIdConta());
                        if (conta.getDiaFechamento() != null) {
                            transaction.put("diaFechamento", conta.getDiaFechamento());
                        }
                        if (conta.getDiaPagamento() != null) {
                            transaction.put("diaPagamento", conta.getDiaPagamento());
                        }
                    }
                }
                
                // Campos de parcelas
                if (gasto.getIdGrupoParcela() != null) {
                    transaction.put("idGrupoParcela", gasto.getIdGrupoParcela());
                    transaction.put("numeroParcela", gasto.getNumeroParcela());
                    transaction.put("totalParcelas", gasto.getTotalParcelas());
                    
                    // Determina se foi paga baseado na data (parcelas com data passada são consideradas pagas)
                    boolean foiPaga = !gasto.isAtivo() || gasto.getData().isBefore(LocalDate.now());
                    transaction.put("parcelaPaga", foiPaga);
                }
                
                transactions.add(transaction);
            }
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("data", transactions);
            
            ResponseUtil.sendJsonResponse(exchange, 200, response);
        } catch (AuthUtil.UnauthorizedException e) {
            ResponseUtil.sendErrorResponse(exchange, 401, e.getMessage());
        } catch (Exception e) {
            e.printStackTrace();
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "Erro ao buscar parcelas: " + e.getMessage());
            ResponseUtil.sendJsonResponse(exchange, 500, response);
        }
    }
    
    private void handlePayInstallment(HttpExchange exchange) throws IOException {
        try {
            int userId = AuthUtil.requireUserId(exchange);
            String requestBody = RequestUtil.readRequestBody(exchange);
            Map<String, Object> data = JsonUtil.parseJsonWithNested(requestBody);
            
            Object expenseIdObj = data.get("expenseId");
            Object contaOrigemIdObj = data.get("contaOrigemId");
            
            if (expenseIdObj == null) {
                Map<String, Object> response = new HashMap<>();
                response.put("success", false);
                response.put("message", "ID da parcela não fornecido");
                ResponseUtil.sendJsonResponse(exchange, 400, response);
                return;
            }
            
            int expenseId = ((Number) expenseIdObj).intValue();
            Integer contaOrigemId = null;
            if (contaOrigemIdObj != null) {
                contaOrigemId = ((Number) contaOrigemIdObj).intValue();
            }
            
            // Busca o gasto (parcela)
            Gasto gasto = expenseRepository.buscarGasto(expenseId);
            if (gasto == null) {
                Map<String, Object> response = new HashMap<>();
                response.put("success", false);
                response.put("message", "Parcela não encontrada");
                ResponseUtil.sendJsonResponse(exchange, 404, response);
                return;
            }
            
            // Valida que o gasto pertence ao usuário autenticado
            if (gasto.getIdUsuario() != userId) {
                Map<String, Object> response = new HashMap<>();
                response.put("success", false);
                response.put("message", "Você não tem permissão para pagar esta parcela");
                ResponseUtil.sendJsonResponse(exchange, 403, response);
                return;
            }
            
            // Verifica se é uma parcela
            if (gasto.getIdGrupoParcela() == null || gasto.getNumeroParcela() == null) {
                Map<String, Object> response = new HashMap<>();
                response.put("success", false);
                response.put("message", "Esta transação não é uma parcela");
                ResponseUtil.sendJsonResponse(exchange, 400, response);
                return;
            }
            
            // Verifica se já foi paga (não está ativa)
            if (!gasto.isAtivo()) {
                Map<String, Object> response = new HashMap<>();
                response.put("success", false);
                response.put("message", "Esta parcela já foi paga ou cancelada");
                ResponseUtil.sendJsonResponse(exchange, 400, response);
                return;
            }
            
            // Valida a conta de origem (se fornecida)
            if (contaOrigemId != null) {
                Conta contaOrigem = accountRepository.buscarConta(contaOrigemId);
                if (contaOrigem == null) {
                    Map<String, Object> response = new HashMap<>();
                    response.put("success", false);
                    response.put("message", "Conta de origem não encontrada");
                    ResponseUtil.sendJsonResponse(exchange, 404, response);
                    return;
                }
                
                // Valida que a conta origem pertence ao usuário autenticado
                if (contaOrigem.getIdUsuario() != userId) {
                    Map<String, Object> response = new HashMap<>();
                    response.put("success", false);
                    response.put("message", "Você não tem permissão para usar esta conta de origem");
                    ResponseUtil.sendJsonResponse(exchange, 403, response);
                    return;
                }
                
                // Valida que não é investimento
                String tipoContaOrigem = contaOrigem.getTipo() != null ? contaOrigem.getTipo().toLowerCase().trim() : "";
                if (tipoContaOrigem.equals("investimento") || tipoContaOrigem.equals("investimento (corretora)") || tipoContaOrigem.startsWith("investimento")) {
                    Map<String, Object> response = new HashMap<>();
                    response.put("success", false);
                    response.put("message", "Contas de investimento não podem ser usadas como conta de origem");
                    ResponseUtil.sendJsonResponse(exchange, 400, response);
                    return;
                }
                
                // Valida que não é a mesma conta do gasto
                if (contaOrigemId == gasto.getIdConta()) {
                    Map<String, Object> response = new HashMap<>();
                    response.put("success", false);
                    response.put("message", "A conta de origem não pode ser a mesma da parcela");
                    ResponseUtil.sendJsonResponse(exchange, 400, response);
                    return;
                }
                
                // Decrementa o saldo da conta origem (dinheiro sai da conta origem)
                accountRepository.decrementarSaldo(contaOrigemId, gasto.getValor());
            }
            // Se não houver conta de origem, apenas marca como paga sem decrementar saldo
            
            // 2. Marca a parcela como paga e estorna o saldo ao cartão (aumenta limite disponível)
            expenseRepository.marcarParcelaComoPaga(expenseId);
            
            // Invalida cache
            CacheUtil.invalidateCache("overview_" + userId);
            CacheUtil.invalidateCache("categories_" + userId);
            CacheUtil.invalidateCache("totalExpense_" + userId);
            CacheUtil.invalidateCache("balance_" + userId);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Parcela paga antecipadamente com sucesso");
            
            ResponseUtil.sendJsonResponse(exchange, 200, response);
        } catch (AuthUtil.UnauthorizedException e) {
            ResponseUtil.sendErrorResponse(exchange, 401, e.getMessage());
        } catch (Exception e) {
            e.printStackTrace();
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "Erro ao pagar parcela: " + e.getMessage());
            ResponseUtil.sendJsonResponse(exchange, 400, response);
        }
    }
    
    private void handleDelete(HttpExchange exchange) throws IOException {
        try {
            int userId = AuthUtil.requireUserId(exchange);
            String idParam = RequestUtil.getQueryParam(exchange, "id");
            
            if (idParam == null) {
                Map<String, Object> response = new HashMap<>();
                response.put("success", false);
                response.put("message", "ID do gasto não fornecido");
                ResponseUtil.sendJsonResponse(exchange, 400, response);
                return;
            }
            
            int expenseId;
            try {
                expenseId = Integer.parseInt(idParam);
            } catch (NumberFormatException e) {
                Map<String, Object> response = new HashMap<>();
                response.put("success", false);
                response.put("message", "ID do gasto inválido");
                ResponseUtil.sendJsonResponse(exchange, 400, response);
                return;
            }
            
            // Busca o gasto (incluindo parcelas pagas/inativas)
            Gasto gasto = expenseRepository.buscarGasto(expenseId);
            if (gasto == null) {
                Map<String, Object> response = new HashMap<>();
                response.put("success", false);
                response.put("message", "Gasto não encontrado");
                ResponseUtil.sendJsonResponse(exchange, 404, response);
                return;
            }
            
            // Verifica se o usuário tem permissão para excluir este gasto
            if (gasto.getIdUsuario() != userId) {
                Map<String, Object> response = new HashMap<>();
                response.put("success", false);
                response.put("message", "Você não tem permissão para excluir este gasto");
                ResponseUtil.sendJsonResponse(exchange, 403, response);
                return;
            }
            
            boolean isParcela = gasto.isParcela();
            boolean parcelaPaga = isParcela && !gasto.isAtivo();
            
            // Se for uma parcela paga, cria uma receita oculta para manter o valor da fatura
            // O pagamento já foi feito, então o valor deve continuar sendo contabilizado
            // Usa prefixo especial [SISTEMA] para que não apareça na listagem
            // IMPORTANTE: não incrementa o saldo porque o estorno já foi feito quando a parcela foi paga
            if (parcelaPaga) {
                Conta conta = accountRepository.buscarConta(gasto.getIdConta());
                if (conta != null && conta.isCartaoCredito()) {
                    String descricaoReceita = "[SISTEMA] Pagamento de parcela excluída: " + gasto.getDescricao();
                    LocalDate dataPagamento = LocalDate.now();
                    incomeRepository.cadastrarReceita(
                        descricaoReceita,
                        gasto.getValor(),
                        dataPagamento,
                        userId,
                        gasto.getIdConta(),
                        null, // sem observações
                        false // NÃO incrementa o saldo - o estorno já foi feito quando a parcela foi paga
                    );
                }
            }
            
            expenseRepository.excluirGasto(expenseId, userId);
            
            CacheUtil.invalidateCache("overview_" + userId);
            CacheUtil.invalidateCache("categories_" + userId);
            CacheUtil.invalidateCache("totalExpense_" + userId);
            CacheUtil.invalidateCache("totalIncome_" + userId);
            CacheUtil.invalidateCache("balance_" + userId);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Gasto excluído com sucesso");
            
            ResponseUtil.sendJsonResponse(exchange, 200, response);
        } catch (AuthUtil.UnauthorizedException e) {
            ResponseUtil.sendErrorResponse(exchange, 401, e.getMessage());
        } catch (Exception e) {
            e.printStackTrace();
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "Erro: " + e.getMessage());
            ResponseUtil.sendJsonResponse(exchange, 400, response);
        }
    }
    
    private List<Integer> parseCategoryIds(Map<String, Object> data) {
        List<Integer> categoryIds = new ArrayList<>();
        Object categoryIdsObj = data.get("categoryIds");
        
        if (categoryIdsObj != null) {
            if (categoryIdsObj instanceof List) {
                @SuppressWarnings("unchecked")
                List<Object> categoryList = (List<Object>) categoryIdsObj;
                for (Object catId : categoryList) {
                    if (catId instanceof Number) {
                        categoryIds.add(((Number) catId).intValue());
                    }
                }
            } else if (categoryIdsObj instanceof String) {
                String categoryIdsStr = (String) categoryIdsObj;
                categoryIdsStr = categoryIdsStr.replaceAll("[\\[\\]\\s]", "");
                for (String idStr : categoryIdsStr.split(",")) {
                    if (!idStr.isEmpty()) {
                        categoryIds.add(Integer.parseInt(idStr));
                    }
                }
            } else if (categoryIdsObj instanceof Number) {
                categoryIds.add(((Number) categoryIdsObj).intValue());
            }
        } else {
            Object categoryIdObj = data.get("categoryId");
            if (categoryIdObj instanceof Number) {
                categoryIds.add(((Number) categoryIdObj).intValue());
            } else if (categoryIdObj instanceof String) {
                categoryIds.add(Integer.parseInt((String) categoryIdObj));
            }
        }
        return categoryIds;
    }
    
    private List<Integer> parseTagIds(Map<String, Object> data) {
        List<Integer> tagIds = new ArrayList<>();
        Object tagIdsObj = data.get("tagIds");
        
        if (tagIdsObj != null) {
            if (tagIdsObj instanceof List) {
                @SuppressWarnings("unchecked")
                List<Object> tagList = (List<Object>) tagIdsObj;
                for (Object tagId : tagList) {
                    if (tagId instanceof Number) {
                        tagIds.add(((Number) tagId).intValue());
                    }
                }
            } else if (tagIdsObj instanceof String) {
                String tagIdsStr = (String) tagIdsObj;
                tagIdsStr = tagIdsStr.replaceAll("[\\[\\]\\s]", "");
                for (String idStr : tagIdsStr.split(",")) {
                    if (!idStr.isEmpty()) {
                        tagIds.add(Integer.parseInt(idStr));
                    }
                }
            } else if (tagIdsObj instanceof Number) {
                tagIds.add(((Number) tagIdsObj).intValue());
            }
        }
        return tagIds;
    }
    
    private String[] parseObservacoes(Map<String, Object> data) {
        String[] observacoes = null;
        Object observacoesObj = data.get("observacoes");
        if (observacoesObj != null) {
            if (observacoesObj instanceof String) {
                String observacoesStr = (String) observacoesObj;
                if (!observacoesStr.trim().isEmpty()) {
                    observacoesStr = observacoesStr.replace("\\n", "\n");
                    String[] obsArray = observacoesStr.split("[\\r\\n]+");
                    List<String> obsList = new ArrayList<>();
                    for (String obs : obsArray) {
                        String trimmed = obs.trim();
                        if (!trimmed.isEmpty()) {
                            obsList.add(trimmed);
                        }
                    }
                    if (!obsList.isEmpty()) {
                        observacoes = obsList.toArray(new String[0]);
                    }
                }
            } else if (observacoesObj instanceof List) {
                @SuppressWarnings("unchecked")
                List<Object> obsList = (List<Object>) observacoesObj;
                List<String> obsStringList = new ArrayList<>();
                for (Object obs : obsList) {
                    if (obs != null) {
                        obsStringList.add(obs.toString().trim());
                    }
                }
                if (!obsStringList.isEmpty()) {
                    observacoes = obsStringList.toArray(new String[0]);
                }
            }
        }
        return observacoes;
    }
}

