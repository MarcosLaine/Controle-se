package server.handlers;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import server.model.*;
import server.repository.*;
import server.utils.*;
import server.utils.CreditCardUtil;
import java.io.IOException;
import java.time.LocalDate;
import java.util.*;

public class IncomesHandler implements HttpHandler {
    private final IncomeRepository incomeRepository;
    private final AccountRepository accountRepository;
    private final ExpenseRepository expenseRepository;
    private final TagRepository tagRepository;

    public IncomesHandler() {
        this.incomeRepository = new IncomeRepository();
        this.accountRepository = new AccountRepository();
        this.expenseRepository = new ExpenseRepository();
        this.tagRepository = new TagRepository();
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        String method = exchange.getRequestMethod().toUpperCase();
        
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
            handlePost(exchange);
        } else if ("PUT".equals(method)) {
            handlePut(exchange);
        } else if ("DELETE".equals(method)) {
            handleDelete(exchange);
        } else {
            ResponseUtil.sendErrorResponse(exchange, 405, "Método não permitido: " + method);
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
            
            String description = (String) data.getOrDefault("description", "Receita");
            double value = ((Number) data.get("value")).doubleValue();
            LocalDate date = LocalDate.parse((String) data.get("date"));
            int accountId = ((Number) data.get("accountId")).intValue();
            
            List<Integer> tagIds = parseTagIds(data);
            String[] observacoes = parseObservacoes(data);
            
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
                response.put("message", "Contas de investimento não podem ser usadas para receitas");
                ResponseUtil.sendJsonResponse(exchange, 400, response);
                return;
            }
            
            // Validação: se a conta for cartão de crédito, deve ter flag de pagamento de fatura
            boolean pagamentoFatura = false;
            if (conta.isCartaoCredito()) {
                Object pagamentoFaturaObj = data.get("pagamentoFatura");
                if (pagamentoFaturaObj != null) {
                    if (pagamentoFaturaObj instanceof Boolean) {
                        pagamentoFatura = (Boolean) pagamentoFaturaObj;
                    } else if (pagamentoFaturaObj instanceof String) {
                        pagamentoFatura = Boolean.parseBoolean((String) pagamentoFaturaObj);
                    } else if (pagamentoFaturaObj instanceof Number) {
                        pagamentoFatura = ((Number) pagamentoFaturaObj).intValue() != 0;
                    }
                }
                
                if (!pagamentoFatura) {
                    Map<String, Object> response = new HashMap<>();
                    response.put("success", false);
                    response.put("message", "Para cadastrar receitas em cartão de crédito, você deve marcar a opção 'Pagamento de Fatura'");
                    ResponseUtil.sendJsonResponse(exchange, 400, response);
                    return;
                }
                
                // Validação: verifica se o valor da receita não excede o valor da fatura atual
                if (conta.getDiaFechamento() != null && conta.getDiaPagamento() != null) {
                    Map<String, Object> faturaInfo = CreditCardUtil.calcularInfoFatura(
                        conta.getDiaFechamento(), 
                        conta.getDiaPagamento()
                    );
                    
                    LocalDate ultimoFechamento = LocalDate.parse((String) faturaInfo.get("ultimoFechamento"));
                    LocalDate proximoFechamento = LocalDate.parse((String) faturaInfo.get("proximoFechamento"));
                    LocalDate proximoPagamento = LocalDate.parse((String) faturaInfo.get("proximoPagamento"));
                    
                    double valorFaturaAtual = expenseRepository.calcularValorFaturaAtual(
                        accountId, 
                        userId,
                        ultimoFechamento, 
                        proximoFechamento
                    );
                    
                    // Calcula o total já pago nesta fatura (soma das receitas e parcelas pagas no período)
                    // Pagamentos são contados até a data de pagamento, não até o fechamento
                    double totalJaPago = incomeRepository.calcularTotalPagoFatura(
                        userId, 
                        accountId, 
                        ultimoFechamento, 
                        proximoPagamento,
                        expenseRepository
                    );
                    
                    double valorDisponivelParaPagamento = valorFaturaAtual - totalJaPago;
                    // Garante que o valor disponível nunca seja negativo
                    if (valorDisponivelParaPagamento < 0) {
                        valorDisponivelParaPagamento = 0;
                    }
                    
                    if (value > valorDisponivelParaPagamento + 0.001) { // Adiciona margem de erro para floats
                        Map<String, Object> response = new HashMap<>();
                        response.put("success", false);
                        if (valorFaturaAtual <= 0) {
                            response.put("message", String.format(
                                "Não há fatura pendente para pagar. A fatura atual está em R$ %.2f.",
                                valorFaturaAtual
                            ));
                        } else if (totalJaPago >= valorFaturaAtual) {
                            response.put("message", String.format(
                                "A fatura já foi totalmente paga. Valor da fatura: R$ %.2f. Valor já pago: R$ %.2f.",
                                valorFaturaAtual, totalJaPago
                            ));
                        } else {
                            response.put("message", String.format(
                                "O valor informado (R$ %.2f) excede o valor disponível para pagamento (R$ %.2f).",
                                value, valorDisponivelParaPagamento
                            ));
                        }
                        response.put("valorFatura", valorFaturaAtual);
                        response.put("valorJaPago", totalJaPago);
                        response.put("valorDisponivel", valorDisponivelParaPagamento);
                        ResponseUtil.sendJsonResponse(exchange, 400, response);
                        return;
                    }
                }
            }
            
            // Verifica se há conta origem para pagamento de fatura
            Object contaOrigemIdObj = data.get("contaOrigemId");
            Integer contaOrigemId = null;
            if (contaOrigemIdObj != null) {
                if (contaOrigemIdObj instanceof Number) {
                    contaOrigemId = ((Number) contaOrigemIdObj).intValue();
                } else if (contaOrigemIdObj instanceof String && !((String) contaOrigemIdObj).isEmpty()) {
                    contaOrigemId = Integer.parseInt((String) contaOrigemIdObj);
                }
            }
            
            // Se for pagamento de fatura com conta origem, valida a conta origem
            if (conta.isCartaoCredito() && pagamentoFatura && contaOrigemId != null && contaOrigemId > 0) {
                Conta contaOrigem = accountRepository.buscarConta(contaOrigemId);
                if (contaOrigem == null) {
                    Map<String, Object> response = new HashMap<>();
                    response.put("success", false);
                    response.put("message", "Conta de origem não encontrada");
                    ResponseUtil.sendJsonResponse(exchange, 400, response);
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
                
                // Valida que a conta origem não é investimento
                String tipoContaOrigem = contaOrigem.getTipo() != null ? contaOrigem.getTipo().toLowerCase().trim() : "";
                if (tipoContaOrigem.equals("investimento") || tipoContaOrigem.equals("investimento (corretora)") || tipoContaOrigem.startsWith("investimento")) {
                    Map<String, Object> response = new HashMap<>();
                    response.put("success", false);
                    response.put("message", "Contas de investimento não podem ser usadas como conta de origem");
                    ResponseUtil.sendJsonResponse(exchange, 400, response);
                    return;
                }
                
                // Valida que não é a mesma conta
                if (contaOrigemId == accountId) {
                    Map<String, Object> response = new HashMap<>();
                    response.put("success", false);
                    response.put("message", "A conta de origem não pode ser a mesma do cartão de crédito");
                    ResponseUtil.sendJsonResponse(exchange, 400, response);
                    return;
                }
            }
            
            int incomeId = incomeRepository.cadastrarReceita(description, value, date, userId, accountId, observacoes);
            
            // Se houver conta origem, faz a transferência (decrementa da conta origem)
            if (contaOrigemId != null && contaOrigemId > 0) {
                accountRepository.decrementarSaldo(contaOrigemId, value);
            }
            
            CacheUtil.invalidateCache("overview_" + userId);
            CacheUtil.invalidateCache("totalIncome_" + userId);
            CacheUtil.invalidateCache("balance_" + userId);
            
            for (int tagId : tagIds) {
                tagRepository.associarTagTransacao(incomeId, "RECEITA", tagId);
            }
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Receita registrada com sucesso");
            response.put("incomeId", incomeId);
            if (contaOrigemId != null && contaOrigemId > 0) {
                response.put("message", "Pagamento de fatura registrado e transferência realizada com sucesso");
            }
            
            ResponseUtil.sendJsonResponse(exchange, 201, response);
        } catch (AuthUtil.UnauthorizedException e) {
            ResponseUtil.sendErrorResponse(exchange, 401, e.getMessage());
        } catch (Exception e) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", e.getMessage());
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
            List<Receita> receitas = incomeRepository.buscarReceitasPorGrupoParcela(groupId);
            
            // Valida que o grupo pertence ao usuário
            if (!receitas.isEmpty() && receitas.get(0).getIdUsuario() != userId) {
                Map<String, Object> response = new HashMap<>();
                response.put("success", false);
                response.put("message", "Acesso negado");
                ResponseUtil.sendJsonResponse(exchange, 403, response);
                return;
            }
            
            // Busca tags e observações
            List<Integer> idsReceitas = new ArrayList<>();
            for (Receita receita : receitas) {
                idsReceitas.add(receita.getIdReceita());
            }
            
            Map<Integer, List<Tag>> tagsPorReceita = new HashMap<>();
            Map<Integer, String[]> observacoesPorReceita = new HashMap<>();
            
            if (!idsReceitas.isEmpty()) {
                tagsPorReceita = tagRepository.buscarTagsDeReceitas(idsReceitas);
                observacoesPorReceita = incomeRepository.buscarObservacoesDeReceitas(idsReceitas);
            }
            
            // Formata as parcelas como transações
            List<Map<String, Object>> transactions = new ArrayList<>();
            
            for (Receita receita : receitas) {
                List<Tag> tags = tagsPorReceita.getOrDefault(receita.getIdReceita(), new ArrayList<>());
                List<Map<String, Object>> tagsList = new ArrayList<>();
                for (Tag tag : tags) {
                    Map<String, Object> tagMap = new HashMap<>();
                    tagMap.put("idTag", tag.getIdTag());
                    tagMap.put("nome", tag.getNome());
                    tagMap.put("cor", tag.getCor());
                    tagsList.add(tagMap);
                }
                
                String[] observacoes = observacoesPorReceita.getOrDefault(receita.getIdReceita(), new String[0]);
                List<String> observacoesList = new ArrayList<>();
                for (String obs : observacoes) {
                    observacoesList.add(obs);
                }
                
                Map<String, Object> transaction = new HashMap<>();
                transaction.put("id", receita.getIdReceita());
                transaction.put("type", "income");
                transaction.put("description", receita.getDescricao());
                transaction.put("value", receita.getValor());
                transaction.put("date", receita.getData().toString());
                transaction.put("category", "Receita");
                transaction.put("categoryId", null);
                transaction.put("tags", tagsList);
                transaction.put("observacoes", observacoesList);
                transaction.put("ativo", receita.isAtivo());
                
                // Campos de parcelas
                if (receita.getIdGrupoParcela() != null) {
                    transaction.put("idGrupoParcela", receita.getIdGrupoParcela());
                    transaction.put("numeroParcela", receita.getNumeroParcela());
                    transaction.put("totalParcelas", receita.getTotalParcelas());
                    
                    // Parcela paga apenas quando inativa (pagamento registrado). Data da parcela é vencimento, não data de pagamento.
                    boolean foiPaga = !receita.isAtivo();
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
    
    private void handlePut(HttpExchange exchange) throws IOException {
        try {
            int userId = AuthUtil.requireUserId(exchange);
            String idParam = RequestUtil.getQueryParam(exchange, "id");
            if (idParam == null || idParam.isEmpty()) {
                ResponseUtil.sendJsonResponse(exchange, 400, Map.of("success", false, "message", "ID da receita é obrigatório"));
                return;
            }
            int incomeId = Integer.parseInt(idParam);
            String requestBody = RequestUtil.readRequestBody(exchange);
            Map<String, Object> data = JsonUtil.parseJsonWithNested(requestBody);
            
            String description = (String) data.getOrDefault("description", "Receita");
            double value = ((Number) data.get("value")).doubleValue();
            LocalDate date = LocalDate.parse((String) data.get("date"));
            int accountId = ((Number) data.get("accountId")).intValue();
            List<Integer> tagIds = parseTagIds(data);
            String[] observacoes = parseObservacoes(data);
            
            Conta conta = accountRepository.buscarConta(accountId);
            if (conta == null) {
                ResponseUtil.sendJsonResponse(exchange, 400, Map.of("success", false, "message", "Conta não encontrada"));
                return;
            }
            if (conta.getIdUsuario() != userId) {
                ResponseUtil.sendJsonResponse(exchange, 403, Map.of("success", false, "message", "Conta não pertence ao usuário"));
                return;
            }
            if (conta.getTipo() != null && conta.getTipo().equalsIgnoreCase("INVESTIMENTO")) {
                ResponseUtil.sendJsonResponse(exchange, 400, Map.of("success", false, "message", "Contas de investimento não podem ser usadas para receitas"));
                return;
            }
            
            incomeRepository.atualizarReceita(incomeId, userId, description, value, date, accountId, observacoes);
            tagRepository.removerTodasTagsTransacao(incomeId, "RECEITA");
            for (int tagId : tagIds) {
                tagRepository.associarTagTransacao(incomeId, "RECEITA", tagId);
            }
            
            CacheUtil.invalidateCache("overview_" + userId);
            CacheUtil.invalidateCache("totalIncome_" + userId);
            CacheUtil.invalidateCache("balance_" + userId);
            
            ResponseUtil.sendJsonResponse(exchange, 200, Map.of("success", true, "message", "Receita atualizada com sucesso"));
        } catch (AuthUtil.UnauthorizedException e) {
            ResponseUtil.sendErrorResponse(exchange, 401, e.getMessage());
        } catch (Exception e) {
            e.printStackTrace();
            ResponseUtil.sendJsonResponse(exchange, 400, Map.of("success", false, "message", e.getMessage() != null ? e.getMessage() : "Erro ao atualizar receita"));
        }
    }
    
    private void handleDelete(HttpExchange exchange) throws IOException {
        try {
            int userId = AuthUtil.requireUserId(exchange);
            String idParam = RequestUtil.getQueryParam(exchange, "id");
            if (idParam == null || !idParam.matches("\\d+")) {
                ResponseUtil.sendErrorResponse(exchange, 400, "ID da receita inválido");
                return;
            }
            
            int incomeId = Integer.parseInt(idParam);
            
            // Verifica se a receita existe e pertence ao usuário autenticado
            Receita receita = incomeRepository.buscarReceita(incomeId);
            if (receita == null) {
                Map<String, Object> response = new HashMap<>();
                response.put("success", false);
                response.put("message", "Receita não encontrada");
                ResponseUtil.sendJsonResponse(exchange, 404, response);
                return;
            }
            
            if (receita.getIdUsuario() != userId) {
                Map<String, Object> response = new HashMap<>();
                response.put("success", false);
                response.put("message", "Você não tem permissão para excluir esta receita");
                ResponseUtil.sendJsonResponse(exchange, 403, response);
                return;
            }
            
            incomeRepository.excluirReceita(incomeId, userId);
            
            CacheUtil.invalidateCache("overview_" + userId);
            CacheUtil.invalidateCache("totalIncome_" + userId);
            CacheUtil.invalidateCache("balance_" + userId);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Receita excluída com sucesso");
            
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

