package server.handlers;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import server.model.CompoundInterestCalculation;
import server.repository.CompoundInterestRepository;
import server.utils.*;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.*;

public class CompoundInterestHandler implements HttpHandler {
    private final CompoundInterestRepository repository;

    public CompoundInterestHandler() {
        this.repository = new CompoundInterestRepository();
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        try {
            String method = exchange.getRequestMethod();
            
            if ("OPTIONS".equals(method)) {
                handleOptions(exchange);
            } else if ("POST".equals(method)) {
                handlePost(exchange);
            } else if ("GET".equals(method)) {
                handleGet(exchange);
            } else if ("DELETE".equals(method)) {
                handleDelete(exchange);
            } else {
                ResponseUtil.sendErrorResponse(exchange, 405, "Método não permitido");
            }
        } catch (AuthUtil.UnauthorizedException e) {
            ResponseUtil.sendErrorResponse(exchange, 401, e.getMessage());
        } catch (Exception e) {
            e.printStackTrace();
            ResponseUtil.sendErrorResponse(exchange, 500, "Erro interno do servidor: " + e.getMessage());
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
            double aporteInicial = Double.parseDouble(data.get("aporteInicial").toString());
            double aporteMensal = Double.parseDouble(data.get("aporteMensal").toString());
            String frequenciaAporte = data.get("frequenciaAporte").toString();
            double taxaJuros = Double.parseDouble(data.get("taxaJuros").toString());
            String tipoTaxa = data.get("tipoTaxa").toString();
            int prazo = Integer.parseInt(data.get("prazo").toString());
            String tipoPrazo = data.get("tipoPrazo").toString();
            double totalInvestido = Double.parseDouble(data.get("totalInvestido").toString());
            double saldoFinal = Double.parseDouble(data.get("saldoFinal").toString());
            double totalJuros = Double.parseDouble(data.get("totalJuros").toString());
            
            // Pega monthlyData
            List<Map<String, Object>> monthlyData = null;
            if (data.get("monthlyData") instanceof List) {
                @SuppressWarnings("unchecked")
                List<Object> monthlyDataList = (List<Object>) data.get("monthlyData");
                monthlyData = new ArrayList<>();
                for (Object item : monthlyDataList) {
                    if (item instanceof Map) {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> map = (Map<String, Object>) item;
                        monthlyData.add(map);
                    }
                }
            }
            
            // Busca próximo ID (simplificado - em produção usar sequence)
            int idCalculo = getNextId();
            
            CompoundInterestCalculation calculo = new CompoundInterestCalculation(
                idCalculo, userId, aporteInicial, aporteMensal, frequenciaAporte,
                taxaJuros, tipoTaxa, prazo, tipoPrazo,
                totalInvestido, saldoFinal, totalJuros
            );
            calculo.setMonthlyData(monthlyData);
            
            int savedId = repository.salvarCalculo(calculo);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("id", savedId);
            response.put("message", "Cálculo salvo com sucesso");
            
            ResponseUtil.sendJsonResponse(exchange, 200, response);
        } catch (Exception e) {
            e.printStackTrace();
            ResponseUtil.sendErrorResponse(exchange, 400, "Erro ao salvar cálculo: " + e.getMessage());
        }
    }

    private void handleGet(HttpExchange exchange) throws IOException {
        try {
            // GET requer autenticação para buscar histórico
            // Se não tiver userId no query param, retorna erro
            String userIdParam = RequestUtil.getQueryParam(exchange, "userId");
            if (userIdParam == null) {
                ResponseUtil.sendErrorResponse(exchange, 400, "userId é obrigatório");
                return;
            }
            
            // Requer autenticação para buscar histórico
            int userId = AuthUtil.requireUserId(exchange);
            // Valida se o userId do token corresponde ao do query param
            int queryUserId = Integer.parseInt(userIdParam);
            if (userId != queryUserId) {
                ResponseUtil.sendErrorResponse(exchange, 403, "Acesso negado");
                return;
            }
            
            List<CompoundInterestCalculation> calculos = repository.buscarCalculosPorUsuario(userId);
            
            List<Map<String, Object>> calculosData = new ArrayList<>();
            for (CompoundInterestCalculation calculo : calculos) {
                Map<String, Object> calcMap = new HashMap<>();
                calcMap.put("idCalculo", calculo.getIdCalculo());
                calcMap.put("idUsuario", calculo.getIdUsuario());
                calcMap.put("aporteInicial", calculo.getAporteInicial());
                calcMap.put("aporteMensal", calculo.getAporteMensal());
                calcMap.put("frequenciaAporte", calculo.getFrequenciaAporte());
                calcMap.put("taxaJuros", calculo.getTaxaJuros());
                calcMap.put("tipoTaxa", calculo.getTipoTaxa());
                calcMap.put("prazo", calculo.getPrazo());
                calcMap.put("tipoPrazo", calculo.getTipoPrazo());
                calcMap.put("totalInvestido", calculo.getTotalInvestido());
                calcMap.put("saldoFinal", calculo.getSaldoFinal());
                calcMap.put("totalJuros", calculo.getTotalJuros());
                calcMap.put("dataCalculo", calculo.getDataCalculo() != null ? calculo.getDataCalculo().toString() : null);
                calcMap.put("monthlyData", calculo.getMonthlyData());
                calculosData.add(calcMap);
            }
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("data", calculosData);
            
            ResponseUtil.sendJsonResponse(exchange, 200, response);
        } catch (AuthUtil.UnauthorizedException e) {
            ResponseUtil.sendErrorResponse(exchange, 401, "Token de autenticação necessário para ver histórico");
        } catch (Exception e) {
            e.printStackTrace();
            ResponseUtil.sendErrorResponse(exchange, 500, "Erro ao buscar cálculos: " + e.getMessage());
        }
    }

    private void handleDelete(HttpExchange exchange) throws IOException {
        try {
            int userId = AuthUtil.requireUserId(exchange);
            
            String idParam = RequestUtil.getQueryParam(exchange, "id");
            if (idParam == null) {
                ResponseUtil.sendErrorResponse(exchange, 400, "id é obrigatório");
                return;
            }
            
            int idCalculo = Integer.parseInt(idParam);
            
            boolean deleted = repository.excluirCalculo(idCalculo, userId);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", deleted);
            response.put("message", deleted ? "Cálculo excluído com sucesso" : "Cálculo não encontrado");
            
            ResponseUtil.sendJsonResponse(exchange, 200, response);
        } catch (Exception e) {
            e.printStackTrace();
            ResponseUtil.sendErrorResponse(exchange, 500, "Erro ao excluir cálculo: " + e.getMessage());
        }
    }

    private int getNextId() {
        // Busca o maior ID e adiciona 1
        try {
            java.sql.Connection dbConn = server.database.DatabaseConnection.getInstance().getConnection();
            try (java.sql.PreparedStatement pstmt = dbConn.prepareStatement(
                    "SELECT COALESCE(MAX(id_calculo), 0) + 1 FROM compound_interest_calculations")) {
                java.sql.ResultSet rs = pstmt.executeQuery();
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        } catch (Exception e) {
            // Fallback
        }
        return 1;
    }
}

