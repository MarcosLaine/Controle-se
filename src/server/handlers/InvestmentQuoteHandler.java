package server.handlers;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import server.utils.RequestUtil;
import server.utils.ResponseUtil;
import server.services.QuoteService;

import java.io.IOException;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

public class InvestmentQuoteHandler implements HttpHandler {
    @Override
    public void handle(HttpExchange exchange) throws IOException {
        if (!"GET".equals(exchange.getRequestMethod())) {
            ResponseUtil.sendErrorResponse(exchange, 405, "Método não permitido");
            return;
        }
        
        try {
            String symbol = RequestUtil.getQueryParam(exchange, "symbol");
            String category = RequestUtil.getQueryParam(exchange, "category");
            String dateStr = RequestUtil.getQueryParam(exchange, "date");
            
            if (symbol == null || category == null) {
                ResponseUtil.sendErrorResponse(exchange, 400, "Parâmetros 'symbol' e 'category' são obrigatórios");
                return;
            }
            
            LocalDate date = dateStr != null ? LocalDate.parse(dateStr) : null;
            
            QuoteService quoteService = QuoteService.getInstance();
            QuoteService.QuoteResult quote = quoteService.getQuote(symbol, category, date);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", quote.success);
            response.put("message", quote.message);
            response.put("price", quote.price);
            response.put("currency", quote.currency);
            
            ResponseUtil.sendJsonResponse(exchange, quote.success ? 200 : 400, response);
        } catch (Exception e) {
            e.printStackTrace();
            ResponseUtil.sendErrorResponse(exchange, 500, "Erro interno do servidor: " + e.getMessage());
        }
    }
}

