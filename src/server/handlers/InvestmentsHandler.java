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
import java.util.stream.Collectors;

public class InvestmentsHandler implements HttpHandler {
    private final InvestmentRepository investmentRepository;
    private final AccountRepository accountRepository;
    
    public InvestmentsHandler() {
        this.investmentRepository = new InvestmentRepository();
        this.accountRepository = new AccountRepository();
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        String method = exchange.getRequestMethod();
        
        if ("GET".equals(method)) {
            handleGetInvestments(exchange);
        } else if ("POST".equals(method)) {
            handleCreateInvestment(exchange);
        } else if ("PUT".equals(method)) {
            handleUpdateInvestment(exchange);
        } else if ("DELETE".equals(method)) {
            handleDeleteInvestment(exchange);
        } else {
            ResponseUtil.sendErrorResponse(exchange, 405, "Método não permitido");
        }
    }
    
    private void handleGetInvestments(HttpExchange exchange) throws IOException {
        try {
            // Usa o userId do token JWT autenticado, não do parâmetro da query string
            // Isso previne que usuários vejam investimentos de outros usuários
            int userId = AuthUtil.requireUserId(exchange);
            if (userId <= 0) {
                ResponseUtil.sendErrorResponse(exchange, 401, "UserId inválido");
                return;
            }
            String queryUserId = RequestUtil.getQueryParam(exchange, "userId");
            if (queryUserId != null && !queryUserId.equals(String.valueOf(userId))) {
                // System.err.println("[DEBUG] InvestimentosHandler: Query string tem userId=" + queryUserId + " mas token tem userId=" + userId);
            }
            // System.out.println("[DEBUG] InvestimentosHandler.get: Usando userId=" + userId + " do token JWT");
            
            String categoryParam = RequestUtil.getQueryParam(exchange, "category");
            String assetNameParam = RequestUtil.getQueryParam(exchange, "assetName");
            
            // Busca TODOS os investimentos (sem paginação)
            List<Investimento> allInvestments = investmentRepository.buscarInvestimentosPorUsuario(userId);
            if (categoryParam != null && !categoryParam.isEmpty()) {
                allInvestments = allInvestments.stream()
                    .filter(inv -> inv.getCategoria().equals(categoryParam))
                    .collect(Collectors.toList());
            }
            if (assetNameParam != null && !assetNameParam.isEmpty()) {
                allInvestments = allInvestments.stream()
                    .filter(inv -> inv.getNome().equals(assetNameParam))
                    .collect(Collectors.toList());
            }
            
            // Usa todos os investimentos para processar
            List<Investimento> investments = allInvestments;
            
            QuoteService quoteService = QuoteService.getInstance();
            
            // Calcula summary com TODOS os investimentos
            double totalInvested = 0;
            double totalCurrent = 0;
            for (Investimento inv : allInvestments) {
                try {
                    // Converte valor do aporte para BRL se o investimento foi registrado em outra moeda
                    double valorAporteBRL = inv.getValorAporte();
                
                    if (!"BRL".equals(inv.getMoeda())) {
                        double exchangeRate = quoteService.getExchangeRate(inv.getMoeda(), "BRL");
                        valorAporteBRL *= exchangeRate;
                    }
                
                    double currentValue = 0.0;
                    // Para renda fixa, calcula valor atual baseado nos índices
                    if ("RENDA_FIXA".equals(inv.getCategoria())) {
                        currentValue = quoteService.calculateFixedIncomeValue(
                            valorAporteBRL,
                            inv.getTipoInvestimento(),
                            inv.getTipoRentabilidade(),
                            inv.getIndice(),
                            inv.getPercentualIndice(),
                            inv.getTaxaFixa(),
                            inv.getDataAporte(),
                            inv.getDataVencimento(),
                            LocalDate.now()
                        );
                    } else {
                        // Para outros tipos, busca cotação atual
                        QuoteService.QuoteResult quote = quoteService.getQuote(inv.getNome(), inv.getCategoria(), null);
                        double currentPrice = quote != null && quote.success ? quote.price : inv.getPrecoAporte();
                        
                        // Converte preço atual para BRL se a cotação vier em outra moeda
                        if (quote != null && quote.success) {
                            String currency = quote.currency != null ? quote.currency : 
                                ("CRYPTO".equals(inv.getCategoria()) ? "USD" : "BRL");
                            if (!"BRL".equals(currency)) {
                                double exchangeRate = quoteService.getExchangeRate(currency, "BRL");
                                currentPrice *= exchangeRate;
                            }
                        }
                        
                        currentValue = inv.getQuantidade() * currentPrice;
                    }
                    
                    totalInvested += valorAporteBRL;
                    totalCurrent += currentValue;
                } catch (Exception e) {
                    e.printStackTrace();
                    // Continua processando os outros investimentos mesmo se um falhar
                }
            }
            
            // Processa apenas os investimentos paginados para a lista de resposta
            List<Map<String, Object>> investmentList = new ArrayList<>();
            for (Investimento inv : investments) {
                try {
                    double currentPrice = 0.0;
                    double currentValue = 0.0;
                    QuoteService.QuoteResult quote = null;
                    
                    // Converte valor do aporte para BRL se o investimento foi registrado em outra moeda
                    double valorAporteBRL = inv.getValorAporte();
                    double precoAporteBRL = inv.getPrecoAporte();
                
                if (!"BRL".equals(inv.getMoeda())) {
                    double exchangeRate = quoteService.getExchangeRate(inv.getMoeda(), "BRL");
                    valorAporteBRL *= exchangeRate;
                    precoAporteBRL *= exchangeRate;
                }
                
                // Para renda fixa, calcula valor atual baseado nos índices
                if ("RENDA_FIXA".equals(inv.getCategoria())) {
                    currentValue = quoteService.calculateFixedIncomeValue(
                        valorAporteBRL,
                        inv.getTipoInvestimento(),
                        inv.getTipoRentabilidade(),
                        inv.getIndice(),
                        inv.getPercentualIndice(),
                        inv.getTaxaFixa(),
                        inv.getDataAporte(),
                        inv.getDataVencimento(),
                        LocalDate.now()
                    );
                    currentPrice = currentValue; // Para renda fixa, preço = valor atual
                } else {
                    // Para outros tipos, busca cotação atual
                    quote = quoteService.getQuote(inv.getNome(), inv.getCategoria(), null);
                    currentPrice = quote != null && quote.success ? quote.price : inv.getPrecoAporte();
                    
                    // Converte preço atual para BRL se a cotação vier em outra moeda
                    // Para criptomoedas, assume USD se currency não estiver definida
                    if (quote != null && quote.success) {
                        String currency = quote.currency != null ? quote.currency : 
                            ("CRYPTO".equals(inv.getCategoria()) ? "USD" : "BRL");
                        if (!"BRL".equals(currency)) {
                            double exchangeRate = quoteService.getExchangeRate(currency, "BRL");
                            currentPrice *= exchangeRate;
                        }
                    }
                    
                    currentValue = inv.getQuantidade() * currentPrice;
                }
                
                double returnValue = currentValue - valorAporteBRL;
                double returnPercent = valorAporteBRL > 0 ? (returnValue / valorAporteBRL) * 100 : 0;
                
                // Se o nomeAtivo não estiver preenchido ou for igual ao ticker, tenta buscar da cotação
                String nomeAtivoFinal = inv.getNomeAtivo();
                if ((nomeAtivoFinal == null || nomeAtivoFinal.trim().isEmpty() || nomeAtivoFinal.equals(inv.getNome())) 
                    && !"RENDA_FIXA".equals(inv.getCategoria()) && quote != null && quote.success 
                    && quote.assetName != null && !quote.assetName.trim().isEmpty() 
                    && !quote.assetName.equals(inv.getNome())) {
                    nomeAtivoFinal = quote.assetName;
                    // Atualiza no banco de dados para persistir o nome
                    try {
                        investmentRepository.atualizarNomeAtivo(inv.getIdInvestimento(), nomeAtivoFinal);
                    } catch (Exception e) {
                        // Ignora erros ao atualizar, mas usa o nome da cotação na resposta
                        System.err.println("Erro ao atualizar nomeAtivo para investimento " + inv.getIdInvestimento() + ": " + e.getMessage());
                    }
                }
                
                Map<String, Object> invData = new HashMap<>();
                invData.put("idInvestimento", inv.getIdInvestimento());
                invData.put("nome", inv.getNome());
                // Sempre retorna o nomeAtivo na resposta da API (pode ser null se não estiver disponível)
                invData.put("nomeAtivo", nomeAtivoFinal);
                invData.put("categoria", inv.getCategoria());
                invData.put("quantidade", inv.getQuantidade());
                invData.put("precoAporte", precoAporteBRL);
                invData.put("valorAporte", valorAporteBRL);
                invData.put("corretagem", inv.getCorretagem());
                invData.put("corretora", inv.getCorretora());
                invData.put("dataAporte", inv.getDataAporte().toString());
                invData.put("moeda", inv.getMoeda());
                invData.put("precoAtual", currentPrice);
                invData.put("valorAtual", currentValue);
                invData.put("retorno", returnValue);
                invData.put("retornoPercent", returnPercent);
                invData.put("accountId", inv.getIdConta());
                
                // Campos específicos de renda fixa
                if ("RENDA_FIXA".equals(inv.getCategoria())) {
                    invData.put("tipoInvestimento", inv.getTipoInvestimento());
                    invData.put("tipoRentabilidade", inv.getTipoRentabilidade());
                    invData.put("indice", inv.getIndice());
                    invData.put("percentualIndice", inv.getPercentualIndice());
                    invData.put("taxaFixa", inv.getTaxaFixa());
                    if (inv.getDataVencimento() != null) {
                        invData.put("dataVencimento", inv.getDataVencimento().toString());
                    }
                }
                
                investmentList.add(invData);
                } catch (Exception e) {
                    e.printStackTrace();
                    // Continua processando os outros investimentos mesmo se um falhar
                }
            }
            
            Map<String, Object> summary = new HashMap<>();
            summary.put("totalInvested", totalInvested);
            summary.put("totalCurrent", totalCurrent);
            summary.put("totalReturn", totalCurrent - totalInvested);
            summary.put("totalReturnPercent", totalInvested > 0 ? ((totalCurrent - totalInvested) / totalInvested) * 100 : 0);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("data", investmentList);
            response.put("summary", summary);
            
            ResponseUtil.sendJsonResponse(exchange, 200, response);
        } catch (Exception e) {
            e.printStackTrace();
            ResponseUtil.sendErrorResponse(exchange, 500, "Erro interno do servidor: " + e.getMessage());
        }
    }
    
    private void handleCreateInvestment(HttpExchange exchange) throws IOException {
        try {
            int userId = AuthUtil.requireUserId(exchange);
            if (userId <= 0) {
                Map<String, Object> response = new HashMap<>();
                response.put("success", false);
                response.put("message", "UserId inválido");
                ResponseUtil.sendJsonResponse(exchange, 401, response);
                return;
            }
            String requestBody = RequestUtil.readRequestBody(exchange);
            Map<String, Object> data = JsonUtil.parseJsonWithNested(requestBody);
            // Remove qualquer userId que possa ter vindo do frontend e usa apenas o do token
            Object frontendUserId = data.remove("userId");
            if (frontendUserId != null && !frontendUserId.equals(userId)) {
                // System.err.println("[DEBUG] InvestimentosHandler: Frontend enviou userId=" + frontendUserId + " mas token tem userId=" + userId);
            }
            data.put("userId", userId);
            //  System.out.println("[DEBUG] InvestimentosHandler.create: Usando userId=" + userId + " do token JWT");
            
            String nome = (String) data.get("nome");
            String categoria = (String) data.get("categoria");
            double quantidade = ((Number) data.get("quantidade")).doubleValue();
            double corretagem = data.containsKey("corretagem") ? 
                ((Number) data.get("corretagem")).doubleValue() : 0.0;
            
            // Valida quantidade (deve ser diferente de zero, permite negativos para vendas)
            if (quantidade == 0 || Double.isNaN(quantidade) || Double.isInfinite(quantidade)) {
                Map<String, Object> response = new HashMap<>();
                response.put("success", false);
                response.put("message", "A quantidade deve ser diferente de zero");
                ResponseUtil.sendJsonResponse(exchange, 400, response);
                return;
            }
            String dataAporteStr = (String) data.get("dataAporte");
            LocalDate dataAporte = dataAporteStr != null ? LocalDate.parse(dataAporteStr) : LocalDate.now();
            
            int accountId = ((Number) data.get("accountId")).intValue();
            String moeda = (String) data.getOrDefault("moeda", "BRL");
            
            // Valida se a conta é do tipo "Investimento" e pertence ao usuário
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
            
            // Aceita tanto "Investimento" quanto "Investimento (Corretora)"
            String tipoConta = conta.getTipo().toLowerCase().trim();
            if (!tipoConta.equals("investimento") && !tipoConta.equals("investimento (corretora)") && !tipoConta.startsWith("investimento")) {
                Map<String, Object> response = new HashMap<>();
                response.put("success", false);
                response.put("message", "Apenas contas do tipo 'Investimento' podem ser utilizadas para criar investimentos");
                ResponseUtil.sendJsonResponse(exchange, 400, response);
                return;
            }
            
            // A corretora é o nome da conta de investimento
            String corretoraFinal = conta.getNome();
            
            // Verifica se o preço foi fornecido manualmente
            double precoAporte = 0.0;
            String nomeAtivo = null;
            
            if (data.containsKey("precoAporte")) {
                // Preço fornecido manualmente
                precoAporte = ((Number) data.get("precoAporte")).doubleValue();
                if (data.containsKey("nomeAtivo")) {
                    nomeAtivo = (String) data.get("nomeAtivo");
                }
            } else {
                // Busca cotação do dia do aporte ou atual
                QuoteService quoteService = QuoteService.getInstance();
                QuoteService.QuoteResult quote = quoteService.getQuote(nome, categoria, dataAporte);
                
                if (!quote.success) {
                    Map<String, Object> response = new HashMap<>();
                    response.put("success", false);
                    response.put("message", "Erro ao buscar cotação: " + quote.message);
                    ResponseUtil.sendJsonResponse(exchange, 400, response);
                    return;
                }
                
                precoAporte = quote.price;
                nomeAtivo = quote.assetName;
                
                // Converte para moeda do investimento se necessário
                if (!moeda.equals(quote.currency)) {
                    double exchangeRate = quoteService.getExchangeRate(quote.currency, moeda);
                    precoAporte *= exchangeRate;
                }
            }
            
            // Processa campos específicos de renda fixa
            String tipoInvestimento = null;
            String tipoRentabilidade = null;
            String indice = null;
            Double percentualIndice = null;
            Double taxaFixa = null;
            LocalDate dataVencimento = null;
            
            if ("RENDA_FIXA".equals(categoria)) {
                tipoInvestimento = (String) data.get("tipoInvestimento");
                // Para renda fixa, o valorAporte vem diretamente
                if (data.containsKey("valorAporte")) {
                    double valorAporte = ((Number) data.get("valorAporte")).doubleValue();
                    precoAporte = valorAporte; // Para renda fixa, precoAporte = valorAporte
                    quantidade = 1.0; // Quantidade sempre 1 para renda fixa
                }
                
                tipoRentabilidade = (String) data.get("tipoRentabilidade");
                indice = data.containsKey("indice") ? (String) data.get("indice") : null;
                
                if (data.containsKey("percentualIndice")) {
                    Object percentObj = data.get("percentualIndice");
                    if (percentObj != null) {
                        if (percentObj instanceof Number) {
                            percentualIndice = ((Number) percentObj).doubleValue();
                        } else if (percentObj instanceof String) {
                            String percentStr = ((String) percentObj).trim();
                            if (!percentStr.isEmpty() && !percentStr.equalsIgnoreCase("null")) {
                                try {
                                    percentualIndice = NumberUtil.parseDoubleBrazilian(percentStr);
                                } catch (NumberFormatException e) {
                                    // Ignora valores inválidos
                                }
                            }
                        }
                    }
                }
                
                if (data.containsKey("taxaFixa")) {
                    Object taxaObj = data.get("taxaFixa");
                    if (taxaObj != null) {
                        if (taxaObj instanceof Number) {
                            taxaFixa = ((Number) taxaObj).doubleValue();
                        } else if (taxaObj instanceof String) {
                            String taxaStr = ((String) taxaObj).trim();
                            if (!taxaStr.isEmpty() && !taxaStr.equalsIgnoreCase("null")) {
                                try {
                                    taxaFixa = NumberUtil.parseDoubleBrazilian(taxaStr);
                                } catch (NumberFormatException e) {
                                    // Ignora valores inválidos
                                }
                            }
                        }
                    }
                }
                
                if (data.containsKey("dataVencimento")) {
                    String dataVencimentoStr = (String) data.get("dataVencimento");
                    if (dataVencimentoStr != null) {
                        dataVencimento = LocalDate.parse(dataVencimentoStr);
                    }
                }
            } else {
            // Valida preço para outros tipos
            // Preço de aporte deve ser positivo mesmo na venda (é o preço unitário)
            if (precoAporte <= 0) {
                    Map<String, Object> response = new HashMap<>();
                    response.put("success", false);
                    response.put("message", "O preço de aporte deve ser maior que zero");
                    ResponseUtil.sendJsonResponse(exchange, 400, response);
                    return;
                }
            }
            
            // Garante que o userId usado é o do token autenticado, não o que veio no body
            int investmentId = investmentRepository.cadastrarInvestimento(nome, nomeAtivo, categoria, quantidade, 
                                                                  precoAporte, corretagem, corretoraFinal,
                                                                  dataAporte, userId, accountId, moeda,
                                                                  tipoInvestimento, tipoRentabilidade, indice, percentualIndice, taxaFixa, dataVencimento);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Investimento cadastrado com sucesso");
            response.put("investmentId", investmentId);
            response.put("precoAporte", precoAporte);
            
            ResponseUtil.sendJsonResponse(exchange, 201, response);
        } catch (Exception e) {
            e.printStackTrace();
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "Erro: " + e.getMessage());
            ResponseUtil.sendJsonResponse(exchange, 400, response);
        }
    }
    
    private void handleUpdateInvestment(HttpExchange exchange) throws IOException {
        try {
            // Valida que o usuário está autenticado
            int authenticatedUserId = AuthUtil.requireUserId(exchange);
            
            String requestBody = RequestUtil.readRequestBody(exchange);
            Map<String, Object> data = JsonUtil.parseJsonWithNested(requestBody);
            
            int idInvestimento = ((Number) data.get("id")).intValue();
            
            // Verifica se o investimento existe e pertence ao usuário autenticado
            Investimento investimento = investmentRepository.buscarInvestimento(idInvestimento);
            if (investimento == null) {
                Map<String, Object> response = new HashMap<>();
                response.put("success", false);
                response.put("message", "Investimento não encontrado");
                ResponseUtil.sendJsonResponse(exchange, 404, response);
                return;
            }
            
            if (investimento.getIdUsuario() != authenticatedUserId) {
                Map<String, Object> response = new HashMap<>();
                response.put("success", false);
                response.put("message", "Você não tem permissão para atualizar este investimento");
                ResponseUtil.sendJsonResponse(exchange, 403, response);
                return;
            }
            String nome = (String) data.get("nome");
            String nomeAtivo = data.containsKey("nomeAtivo") ? (String) data.get("nomeAtivo") : null;
            String categoria = (String) data.get("categoria");
            double quantidade = ((Number) data.get("quantidade")).doubleValue();
            double corretagem = ((Number) data.get("corretagem")).doubleValue();
            
            // Valida quantidade (deve ser diferente de zero, permite negativos para vendas)
            if (quantidade == 0 || Double.isNaN(quantidade) || Double.isInfinite(quantidade)) {
                Map<String, Object> response = new HashMap<>();
                response.put("success", false);
                response.put("message", "A quantidade deve ser diferente de zero");
                ResponseUtil.sendJsonResponse(exchange, 400, response);
                return;
            }
            String corretora = (String) data.get("corretora");
            String dataAporteStr = (String) data.get("dataAporte");
            LocalDate dataAporte = LocalDate.parse(dataAporteStr);
            String moeda = (String) data.getOrDefault("moeda", "BRL");
            
            // Busca cotação se necessário
            double precoAporte = data.containsKey("precoAporte") ? 
                ((Number) data.get("precoAporte")).doubleValue() : 0.0;
            
            if (precoAporte == 0.0) {
                QuoteService quoteService = QuoteService.getInstance();
                QuoteService.QuoteResult quote = quoteService.getQuote(nome, categoria, dataAporte);
                if (quote.success) {
                    precoAporte = quote.price;
                    // Se não havia nomeAtivo e a cotação retornou um, usa ele
                    if (nomeAtivo == null && quote.assetName != null) {
                        nomeAtivo = quote.assetName;
                    }
                }
            }
            
            // Obtém o accountId se fornecido (para atualizar a conta do investimento)
            Integer accountId = null;
            if (data.containsKey("accountId")) {
                accountId = ((Number) data.get("accountId")).intValue();
                // Se accountId foi fornecido, busca a conta para obter o nome da corretora
                if (accountId != null && accountId > 0) {
                    Conta conta = accountRepository.buscarConta(accountId);
                    if (conta == null) {
                        Map<String, Object> response = new HashMap<>();
                        response.put("success", false);
                        response.put("message", "Conta não encontrada");
                        ResponseUtil.sendJsonResponse(exchange, 404, response);
                        return;
                    }
                    
                    // Valida que a conta pertence ao usuário autenticado
                    if (conta.getIdUsuario() != authenticatedUserId) {
                        Map<String, Object> response = new HashMap<>();
                        response.put("success", false);
                        response.put("message", "Você não tem permissão para usar esta conta");
                        ResponseUtil.sendJsonResponse(exchange, 403, response);
                        return;
                    }
                    
                    if (conta != null) {
                        corretora = conta.getNome();
                    }
                }
            }
            
            investmentRepository.atualizarInvestimento(idInvestimento, nome, nomeAtivo, categoria, quantidade,
                                                precoAporte, corretagem, corretora, dataAporte, moeda, accountId);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Investimento atualizado com sucesso");
            
            ResponseUtil.sendJsonResponse(exchange, 200, response);
        } catch (Exception e) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", e.getMessage());
            ResponseUtil.sendJsonResponse(exchange, 400, response);
        }
    }
    
    private void handleDeleteInvestment(HttpExchange exchange) throws IOException {
        try {
            // Valida que o usuário está autenticado
            int authenticatedUserId = AuthUtil.requireUserId(exchange);
            
            String idParam = RequestUtil.getQueryParam(exchange, "id");
            if (idParam == null) {
                ResponseUtil.sendErrorResponse(exchange, 400, "ID do investimento não fornecido");
                return;
            }
            
            int idInvestimento = Integer.parseInt(idParam);
            
            // Verifica se o investimento existe e pertence ao usuário autenticado
            Investimento investimento = investmentRepository.buscarInvestimento(idInvestimento);
            if (investimento == null) {
                Map<String, Object> response = new HashMap<>();
                response.put("success", false);
                response.put("message", "Investimento não encontrado");
                ResponseUtil.sendJsonResponse(exchange, 404, response);
                return;
            }
            
            if (investimento.getIdUsuario() != authenticatedUserId) {
                Map<String, Object> response = new HashMap<>();
                response.put("success", false);
                response.put("message", "Você não tem permissão para excluir este investimento");
                ResponseUtil.sendJsonResponse(exchange, 403, response);
                return;
            }
            
            investmentRepository.excluirInvestimento(idInvestimento);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Investimento excluído com sucesso");
            
            ResponseUtil.sendJsonResponse(exchange, 200, response);
        } catch (Exception e) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", e.getMessage());
            ResponseUtil.sendJsonResponse(exchange, 400, response);
        }
    }
}

