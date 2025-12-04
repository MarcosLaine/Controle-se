package server.handlers;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import server.model.*;
import server.repository.*;
import server.utils.*;
import static server.utils.AuthUtil.UnauthorizedException;

import java.io.IOException;
import java.io.OutputStream;
import java.time.LocalDate;
import java.util.*;

public class ReportsHandler implements HttpHandler {
    private final ExpenseRepository expenseRepository;
    private final IncomeRepository incomeRepository;
    private final CategoryRepository categoryRepository;
    private final AccountRepository accountRepository;

    public ReportsHandler(ExpenseRepository expenseRepository, IncomeRepository incomeRepository,
                         CategoryRepository categoryRepository, AccountRepository accountRepository) {
        this.expenseRepository = expenseRepository;
        this.incomeRepository = incomeRepository;
        this.categoryRepository = categoryRepository;
        this.accountRepository = accountRepository;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        String method = exchange.getRequestMethod();
        
        if ("GET".equals(method)) {
            handleGetReports(exchange);
        } else if ("POST".equals(method)) {
            handleExportReport(exchange);
        } else {
            ResponseUtil.sendErrorResponse(exchange, 405, "Método não permitido");
        }
    }
    
    private void handleGetReports(HttpExchange exchange) throws IOException {
        try {
            // Usa o userId do token JWT autenticado, não do parâmetro da query string
            // Isso previne que usuários vejam relatórios de outros usuários
            int userId = AuthUtil.requireUserId(exchange);
            
            String periodParam = RequestUtil.getQueryParam(exchange, "period");
            String startDateParam = RequestUtil.getQueryParam(exchange, "startDate");
            String endDateParam = RequestUtil.getQueryParam(exchange, "endDate");
            String period = periodParam != null ? periodParam : "month";
            
            // Calcula datas baseado no período
            LocalDate startDate, endDate;
            LocalDate now = LocalDate.now();
            
            if (startDateParam != null && endDateParam != null) {
                startDate = LocalDate.parse(startDateParam);
                endDate = LocalDate.parse(endDateParam);
            } else if ("year".equals(period)) {
                startDate = now.withDayOfYear(1);
                endDate = now.withDayOfYear(now.lengthOfYear());
            } else { // month
                startDate = now.withDayOfMonth(1);
                endDate = now.withDayOfMonth(now.lengthOfMonth());
            }
            
            // Busca dados para o relatório
            List<Gasto> expenses = expenseRepository.buscarGastosPorPeriodo(userId, startDate, endDate);
            List<Receita> incomes = incomeRepository.buscarReceitasPorPeriodo(userId, startDate, endDate);
            
            // Calcula totais
            double totalExpenses = expenses.stream().mapToDouble(Gasto::getValor).sum();
            double totalIncomes = incomes.stream().mapToDouble(Receita::getValor).sum();
            double balance = totalIncomes - totalExpenses;
            
            // Análise por categoria
            Map<String, Double> categoryAnalysis = new HashMap<>();
            for (Gasto gasto : expenses) {
                List<Categoria> categorias = categoryRepository.buscarCategoriasDoGasto(gasto.getIdGasto());
                for (Categoria categoria : categorias) {
                    categoryAnalysis.merge(categoria.getNome(), gasto.getValor(), Double::sum);
                }
            }
            
            // Análise por conta
            Map<String, Double> accountAnalysis = new HashMap<>();
            for (Gasto gasto : expenses) {
                Conta conta = accountRepository.buscarConta(gasto.getIdConta());
                if (conta != null && conta.getIdUsuario() == userId) {
                    accountAnalysis.merge(conta.getNome(), gasto.getValor(), Double::sum);
                }
            }
            
            // Análise mensal (últimos 12 meses)
            List<Map<String, Object>> monthlyAnalysis = new ArrayList<>();
            for (int i = 11; i >= 0; i--) {
                LocalDate monthStart = now.minusMonths(i).withDayOfMonth(1);
                LocalDate monthEnd = monthStart.withDayOfMonth(monthStart.lengthOfMonth());
                
                List<Gasto> monthExpenses = expenseRepository.buscarGastosPorPeriodo(userId, monthStart, monthEnd);
                List<Receita> monthIncomes = incomeRepository.buscarReceitasPorPeriodo(userId, monthStart, monthEnd);
                
                double monthExpenseTotal = monthExpenses.stream().mapToDouble(Gasto::getValor).sum();
                double monthIncomeTotal = monthIncomes.stream().mapToDouble(Receita::getValor).sum();
                
                Map<String, Object> monthData = new HashMap<>();
                monthData.put("month", monthStart.getMonthValue());
                monthData.put("year", monthStart.getYear());
                monthData.put("monthName", monthStart.getMonth().name());
                monthData.put("expenses", monthExpenseTotal);
                monthData.put("incomes", monthIncomeTotal);
                monthData.put("balance", monthIncomeTotal - monthExpenseTotal);
                monthlyAnalysis.add(monthData);
            }
            
            // Top 5 gastos
            List<Map<String, Object>> topExpenses = new ArrayList<>();
            expenses.stream()
                .sorted((a, b) -> Double.compare(b.getValor(), a.getValor()))
                .limit(5)
                .forEach(gasto -> {
                    Map<String, Object> expenseData = new HashMap<>();
                    expenseData.put("description", gasto.getDescricao());
                    expenseData.put("value", gasto.getValor());
                    expenseData.put("date", gasto.getData().toString());
                    
                    List<Categoria> categorias = categoryRepository.buscarCategoriasDoGasto(gasto.getIdGasto());
                    String categoryNames = categorias.stream()
                        .map(Categoria::getNome)
                        .reduce((a, b) -> a + ", " + b)
                        .orElse("Sem categoria");
                    expenseData.put("category", categoryNames);
                    
                    topExpenses.add(expenseData);
                });
            
            // Prepara resposta
            Map<String, Object> reportData = new HashMap<>();
            reportData.put("period", period);
            reportData.put("startDate", startDate.toString());
            reportData.put("endDate", endDate.toString());
            reportData.put("totalExpenses", totalExpenses);
            reportData.put("totalIncomes", totalIncomes);
            reportData.put("balance", balance);
            reportData.put("categoryAnalysis", categoryAnalysis);
            reportData.put("accountAnalysis", accountAnalysis);
            reportData.put("monthlyAnalysis", monthlyAnalysis);
            reportData.put("topExpenses", topExpenses);
            reportData.put("expenseCount", expenses.size());
            reportData.put("incomeCount", incomes.size());
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("data", reportData);
            
            ResponseUtil.sendJsonResponse(exchange, 200, response);
        } catch (Exception e) {
            e.printStackTrace();
            ResponseUtil.sendErrorResponse(exchange, 500, "Erro interno do servidor");
        }
    }
    
    private void handleExportReport(HttpExchange exchange) throws IOException {
        try {
            int userId = AuthUtil.requireUserId(exchange);
            String requestBody = RequestUtil.readRequestBody(exchange);
            Map<String, String> data = JsonUtil.parseJson(requestBody);
            
            String format = data.get("format"); // "csv" ou "xlsx"
            String period = data.get("period");
            String startDateParam = data.get("startDate");
            String endDateParam = data.get("endDate");
            
            // Calcula datas
            LocalDate startDate, endDate;
            LocalDate now = LocalDate.now();
            
            if (startDateParam != null && endDateParam != null) {
                startDate = LocalDate.parse(startDateParam);
                endDate = LocalDate.parse(endDateParam);
            } else if ("year".equals(period)) {
                startDate = now.withDayOfYear(1);
                endDate = now.withDayOfYear(now.lengthOfYear());
            } else { // month
                startDate = now.withDayOfMonth(1);
                endDate = now.withDayOfMonth(now.lengthOfMonth());
            }
            
            // Busca dados
            List<Gasto> expenses = expenseRepository.buscarGastosPorPeriodo(userId, startDate, endDate);
            List<Receita> incomes = incomeRepository.buscarReceitasPorPeriodo(userId, startDate, endDate);
            
            if ("csv".equals(format)) {
                exportToCSV(exchange, expenses, incomes, startDate, endDate);
            } else if ("xlsx".equals(format)) {
                exportToXLSX(exchange, expenses, incomes, startDate, endDate);
            } else {
                ResponseUtil.sendErrorResponse(exchange, 400, "Formato não suportado. Use 'csv' ou 'xlsx'");
            }
            
        } catch (UnauthorizedException e) {
            ResponseUtil.sendErrorResponse(exchange, 401, e.getMessage());
        } catch (Exception e) {
            e.printStackTrace();
            ResponseUtil.sendErrorResponse(exchange, 500, "Erro interno do servidor");
        }
    }
    
    private void exportToCSV(HttpExchange exchange, List<Gasto> expenses, List<Receita> incomes, 
                            LocalDate startDate, LocalDate endDate) throws IOException {
        StringBuilder csv = new StringBuilder();
        
        // Header
        csv.append("Tipo,Descrição,Valor,Data,Categoria,Conta,Observações\n");
        
        // Expenses
        for (Gasto gasto : expenses) {
            List<Categoria> categorias = categoryRepository.buscarCategoriasDoGasto(gasto.getIdGasto());
            String categoryNames = categorias.stream()
                .map(Categoria::getNome)
                .reduce((a, b) -> a + ", " + b)
                .orElse("Sem categoria");
            
            Conta conta = accountRepository.buscarConta(gasto.getIdConta());
            String accountName = (conta != null && conta.getIdUsuario() == userId) ? conta.getNome() : "Conta não encontrada";
            
            String observacoes = "";
            if (gasto.getObservacoes() != null && gasto.getObservacoes().length > 0) {
                observacoes = String.join("; ", gasto.getObservacoes());
            }
            
            csv.append("Gasto,")
               .append(escapeCsv(gasto.getDescricao())).append(",")
               .append(gasto.getValor()).append(",")
               .append(gasto.getData()).append(",")
               .append(escapeCsv(categoryNames)).append(",")
               .append(escapeCsv(accountName)).append(",")
               .append(escapeCsv(observacoes)).append("\n");
        }
        
        // Incomes
        for (Receita receita : incomes) {
            Conta conta = accountRepository.buscarConta(receita.getIdConta());
            String accountName = (conta != null && conta.getIdUsuario() == userId) ? conta.getNome() : "Conta não encontrada";
            
            csv.append("Receita,")
               .append(escapeCsv(receita.getDescricao())).append(",")
               .append(receita.getValor()).append(",")
               .append(receita.getData()).append(",")
               .append("Receita,")
               .append(escapeCsv(accountName)).append(",")
               .append("").append("\n");
        }
        
        String filename = "relatorio_" + startDate + "_" + endDate + ".csv";
        
        exchange.getResponseHeaders().set("Content-Type", "text/csv; charset=UTF-8");
        exchange.getResponseHeaders().set("Content-Disposition", "attachment; filename=\"" + filename + "\"");
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        
        byte[] csvBytes = csv.toString().getBytes("UTF-8");
        exchange.sendResponseHeaders(200, csvBytes.length);
        
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(csvBytes);
        }
    }
    
    private void exportToXLSX(HttpExchange exchange, List<Gasto> expenses, List<Receita> incomes, 
                             LocalDate startDate, LocalDate endDate) throws IOException {
        // Para XLSX, vamos retornar um CSV com extensão .xlsx por simplicidade
        // Em uma implementação real, usaria Apache POI
        StringBuilder xlsx = new StringBuilder();
        
        // Header
        xlsx.append("Tipo\tDescrição\tValor\tData\tCategoria\tConta\tObservações\n");
        
        // Expenses
        for (Gasto gasto : expenses) {
            List<Categoria> categorias = categoryRepository.buscarCategoriasDoGasto(gasto.getIdGasto());
            String categoryNames = categorias.stream()
                .map(Categoria::getNome)
                .reduce((a, b) -> a + ", " + b)
                .orElse("Sem categoria");
            
            Conta conta = accountRepository.buscarConta(gasto.getIdConta());
            String accountName = (conta != null && conta.getIdUsuario() == userId) ? conta.getNome() : "Conta não encontrada";
            
            String observacoes = "";
            if (gasto.getObservacoes() != null && gasto.getObservacoes().length > 0) {
                observacoes = String.join("; ", gasto.getObservacoes());
            }
            
            xlsx.append("Gasto\t")
               .append(gasto.getDescricao()).append("\t")
               .append(gasto.getValor()).append("\t")
               .append(gasto.getData()).append("\t")
               .append(categoryNames).append("\t")
               .append(accountName).append("\t")
               .append(observacoes).append("\n");
        }
        
        // Incomes
        for (Receita receita : incomes) {
            Conta conta = accountRepository.buscarConta(receita.getIdConta());
            String accountName = (conta != null && conta.getIdUsuario() == userId) ? conta.getNome() : "Conta não encontrada";
            
            xlsx.append("Receita\t")
               .append(receita.getDescricao()).append("\t")
               .append(receita.getValor()).append("\t")
               .append(receita.getData()).append("\t")
               .append("Receita\t")
               .append(accountName).append("\t")
               .append("").append("\n");
        }
        
        String filename = "relatorio_" + startDate + "_" + endDate + ".xlsx";
        
        exchange.getResponseHeaders().set("Content-Type", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        exchange.getResponseHeaders().set("Content-Disposition", "attachment; filename=\"" + filename + "\"");
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        
        byte[] xlsxBytes = xlsx.toString().getBytes("UTF-8");
        exchange.sendResponseHeaders(200, xlsxBytes.length);
        
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(xlsxBytes);
        }
    }
    
    private String escapeCsv(String value) {
        if (value == null) return "";
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }
}
