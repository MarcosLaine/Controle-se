package server.handlers;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import server.model.Investimento;
import server.repository.InvestmentRepository;
import server.utils.RequestUtil;
import server.utils.ResponseUtil;
import server.services.QuoteService;

import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;

public class InvestmentEvolutionHandler implements HttpHandler {
    private static final DateTimeFormatter LABEL_FORMATTER = DateTimeFormatter.ofPattern("dd/MM");
    private static final DateTimeFormatter LABEL_FORMATTER_WITH_YEAR = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter HOURLY_LABEL_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");
    private static final int MAX_DAYS = 3650; // ~10 anos

    private final InvestmentRepository investmentRepository;

    public InvestmentEvolutionHandler(InvestmentRepository investmentRepository) {
        this.investmentRepository = investmentRepository;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            ResponseUtil.sendErrorResponse(exchange, 405, "Método não permitido");
            return;
        }

        try {
            String userIdParam = RequestUtil.getQueryParam(exchange, "userId");
            int userId = userIdParam != null ? Integer.parseInt(userIdParam) : 1;

            LocalDate today = LocalDate.now();
            LocalDate endDate = parseDateOrDefault(RequestUtil.getQueryParam(exchange, "endDate"), today);

            // Busca investimentos antes de determinar o período final
            List<Investimento> transactions = investmentRepository.buscarInvestimentosPorUsuario(userId);

            String periodParam = RequestUtil.getQueryParam(exchange, "period");
            boolean twoHourResolution = "1D".equalsIgnoreCase(periodParam);

            LocalDate startDate = resolveStartDate(
                RequestUtil.getQueryParam(exchange, "startDate"),
                periodParam,
                endDate,
                transactions
            );

            if (startDate.isAfter(endDate)) {
                LocalDate tmp = startDate;
                startDate = endDate;
                endDate = tmp;
            }

            long totalDays = ChronoUnit.DAYS.between(startDate, endDate);
            if (totalDays > MAX_DAYS) {
                ResponseUtil.sendErrorResponse(exchange, 400, "Período máximo de 10 anos excedido. Reduza o intervalo.");
                return;
            }

            // Otimização: reduz granularidade para períodos longos
            // Limita número máximo de pontos para evitar sobrecarga
            int maxPoints = 200; // Reduzido para 200 pontos para carregar mais rápido na primeira vez
            int dayStep = 1;
            if (totalDays > maxPoints) {
                dayStep = (int) Math.ceil((double) totalDays / maxPoints);
                // Ajusta para valores "redondos"
                if (dayStep <= 2) dayStep = 1;
                else if (dayStep <= 5) dayStep = 3;
                else if (dayStep <= 10) dayStep = 7;
                else if (dayStep <= 20) dayStep = 14; // 2 semanas
                else if (dayStep <= 30) dayStep = 30; // 1 mês
                else dayStep = 60; // 2 meses para períodos extremamente longos
            } else if (totalDays > 730) { // > 2 anos
                dayStep = 7; // Semanal
            } else if (totalDays > 180) { // > 6 meses
                dayStep = 3; // A cada 3 dias
            }

            // Determina se deve mostrar o ano (período > 1 ano)
            boolean showYear = totalDays > 365;
            Map<String, Object> data = buildEvolutionSeries(transactions, startDate, endDate, twoHourResolution, dayStep, showYear);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("data", data);

            ResponseUtil.sendJsonResponse(exchange, 200, response);
        } catch (Exception e) {
            e.printStackTrace();
            ResponseUtil.sendErrorResponse(exchange, 500, "Erro ao calcular evolução: " + e.getMessage());
        }
    }

    private LocalDate parseDateOrDefault(String value, LocalDate fallback) {
        if (value == null || value.isEmpty()) {
            return fallback;
        }
        return LocalDate.parse(value);
    }

    private LocalDate resolveStartDate(String startParam, String periodParam, LocalDate endDate, List<Investimento> transactions) {
        if (startParam != null && !startParam.isEmpty()) {
            return LocalDate.parse(startParam);
        }

        if (periodParam != null) {
            switch (periodParam.toUpperCase()) {
                case "1D":
                    return endDate.minusDays(1);
                case "1W":
                    return endDate.minusWeeks(1);
                case "1M":
                    return endDate.minusMonths(1);
                case "6M":
                    return endDate.minusMonths(6);
                case "YTD":
                    return LocalDate.of(endDate.getYear(), 1, 1);
                case "1Y":
                    return endDate.minusYears(1);
                case "5Y":
                    return endDate.minusYears(5);
                case "ALL":
                    return transactions.stream()
                        .map(Investimento::getDataAporte)
                        .min(LocalDate::compareTo)
                        .orElse(endDate.minusMonths(1));
                default:
                    break;
            }
        }

        return endDate.minusMonths(1);
    }

    private Map<String, Object> buildEvolutionSeries(List<Investimento> transactions, LocalDate startDate, LocalDate endDate, boolean useTwoHourSteps, int dayStep, boolean showYear) {
        Map<String, Object> data = new HashMap<>();
        List<String> labels = new ArrayList<>();
        List<Double> investedPoints = new ArrayList<>();
        List<Double> currentPoints = new ArrayList<>();
        
        // Mapas para rastrear valores por categoria
        Map<String, List<Double>> categoryInvested = new HashMap<>();
        Map<String, List<Double>> categoryCurrent = new HashMap<>();
        // Set para rastrear todas as categorias que já apareceram (para garantir continuidade)
        Set<String> allCategoriesSeen = new HashSet<>();

        data.put("labels", labels);
        data.put("invested", investedPoints);
        data.put("current", currentPoints);
        data.put("startDate", startDate.toString());
        data.put("endDate", endDate.toString());
        data.put("resolution", useTwoHourSteps ? "2h" : "1d");

        if (transactions == null || transactions.isEmpty()) {
            data.put("points", 0);
            return data;
        }

        transactions.sort(Comparator.comparing(Investimento::getDataAporte));

        QuoteService quoteService = QuoteService.getInstance();
        Map<String, AssetState> assetState = new LinkedHashMap<>();
        Map<String, Double> priceCache = new HashMap<>();
        
        // Otimização: para períodos muito longos, reduz busca de preços
        long totalDays = ChronoUnit.DAYS.between(startDate, endDate);
        int priceLookupInterval = 1; // Busca preço a cada N dias (padrão: diário)
        if (totalDays > 730) { // > 2 anos
            priceLookupInterval = 14; // Busca preço a cada 2 semanas para períodos muito longos
        } else if (totalDays > 365) { // > 1 ano
            priceLookupInterval = 7; // Busca preço semanalmente
        } else if (totalDays > 180) { // > 6 meses
            priceLookupInterval = 3; // Busca preço a cada 3 dias
        }

        int txIndex = 0;
        
        // OTIMIZAÇÃO CRÍTICA: Pre-busca cotações em batch para períodos grandes
        // Processa transações primeiro para identificar ativos, depois busca cotações
        if (totalDays > 365) {
            // Processa todas as transações até a data final para identificar todos os ativos
            Map<String, AssetState> tempAssetState = new LinkedHashMap<>();
            int tempTxIndex = 0;
            for (LocalDate date = startDate; !date.isAfter(endDate); date = date.plusDays(1)) {
                tempTxIndex = consumeTransactions(transactions, tempAssetState, tempTxIndex, date);
            }
            // Agora pre-busca cotações baseado nos ativos identificados
            preFetchQuotesInBatch(tempAssetState, startDate, endDate, priceLookupInterval, quoteService, priceCache);
        }

        if (useTwoHourSteps) {
            LocalDateTime cursor = startDate.atStartOfDay();
            LocalDateTime endCursor = endDate.atStartOfDay();
            if (endCursor.isBefore(cursor)) {
                endCursor = cursor;
            }

            while (!cursor.isAfter(endCursor)) {
                LocalDate currentDate = cursor.toLocalDate();
                txIndex = consumeTransactions(transactions, assetState, txIndex, currentDate);
                accumulatePoint(assetState, quoteService, priceCache, labels, investedPoints, currentPoints,
                    categoryInvested, categoryCurrent, allCategoriesSeen, cursor.format(HOURLY_LABEL_FORMATTER), currentDate, cursor, 1);
                cursor = cursor.plusHours(2);
            }
        } else {
            // Usa step passado como parâmetro (já calculado no handler)
            // Se não foi passado, calcula baseado no período
            if (dayStep <= 0) {
                dayStep = 1; // Padrão: diário
                if (totalDays > 730) { // > 2 anos
                    dayStep = 7; // Semanal
                } else if (totalDays > 180) { // > 6 meses
                    dayStep = 3; // A cada 3 dias
                }
            }
            
            DateTimeFormatter labelFormatter = showYear ? LABEL_FORMATTER_WITH_YEAR : LABEL_FORMATTER;
            for (LocalDate date = startDate; !date.isAfter(endDate); date = date.plusDays(dayStep)) {
                txIndex = consumeTransactions(transactions, assetState, txIndex, date);
                accumulatePoint(assetState, quoteService, priceCache, labels, investedPoints, currentPoints,
                    categoryInvested, categoryCurrent, allCategoriesSeen, date.format(labelFormatter), date, date.atStartOfDay(), priceLookupInterval);
            }
        }

        // Adiciona dados por categoria ao response
        // Garante que todas as categorias tenham o mesmo número de pontos que os labels
        int expectedSize = labels.size();
        Map<String, Map<String, List<Double>>> categoriesData = new HashMap<>();
        for (String category : allCategoriesSeen) {
            List<Double> investedList = categoryInvested.getOrDefault(category, new ArrayList<>());
            List<Double> currentList = categoryCurrent.getOrDefault(category, new ArrayList<>());
            
            // Preenche com zeros se a lista estiver menor que o esperado
            while (investedList.size() < expectedSize) {
                investedList.add(0.0);
            }
            while (currentList.size() < expectedSize) {
                currentList.add(0.0);
            }
            
            Map<String, List<Double>> catData = new HashMap<>();
            catData.put("invested", investedList);
            catData.put("current", currentList);
            categoriesData.put(category, catData);
        }
        data.put("categories", categoriesData);

        data.put("points", labels.size());
        return data;
    }

    private int consumeTransactions(List<Investimento> transactions, Map<String, AssetState> assetState, int txIndex, LocalDate cutoffDate) {
        while (txIndex < transactions.size() && !transactions.get(txIndex).getDataAporte().isAfter(cutoffDate)) {
            Investimento inv = transactions.get(txIndex);
            
            // IMPORTANTE: Ignora transações inválidas (quantidade zero)
            double qty = inv.getQuantidade();
            if (qty == 0.0 || Double.isNaN(qty) || Double.isInfinite(qty)) {
                txIndex++;
                continue;
            }
            
            String key = buildAssetKey(inv);
            AssetState state = assetState.computeIfAbsent(key, k -> AssetState.fromInvestment(inv));
            state.updateMetadata(inv);
            state.applyTransaction(inv);

            txIndex++;
        }
        return txIndex;
    }

    private void accumulatePoint(
        Map<String, AssetState> assetState,
        QuoteService quoteService,
        Map<String, Double> priceCache,
        List<String> labels,
        List<Double> investedPoints,
        List<Double> currentPoints,
        Map<String, List<Double>> categoryInvested,
        Map<String, List<Double>> categoryCurrent,
        Set<String> allCategoriesSeen,
        String label,
        LocalDate dateForPricing,
        LocalDateTime dateTimeForPricing,
        int priceLookupInterval
    ) {
        double totalInvested = 0;
        double totalCurrent = 0;
        
        // Mapas para rastrear valores por categoria
        Map<String, Double> categoryInvestedMap = new HashMap<>();
        Map<String, Double> categoryCurrentMap = new HashMap<>();

        Iterator<Map.Entry<String, AssetState>> iterator = assetState.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, AssetState> entry = iterator.next();
            AssetState state = entry.getValue();
            
            // Verifica se o estado está vazio ANTES de processar
            boolean isEmpty = state.isEmpty();
            
            String category = state.category != null ? state.category : "OUTROS";
            // IMPORTANTE: Marca a categoria como vista ANTES de calcular valores
            allCategoriesSeen.add(category);
            
            // Se o estado está vazio, pula o cálculo mas mantém a categoria rastreada
            if (isEmpty) {
                continue;
            }
            
            double invested = state.getTotalCostBasis();
            totalInvested += invested;
            
            // Otimização: para períodos longos, reutiliza preço de dias próximos
            LocalDate priceLookupDate = dateForPricing;
            if (priceLookupInterval > 1) {
                // Arredonda para o dia mais próximo que é múltiplo do intervalo
                long daysSinceEpoch = dateForPricing.toEpochDay();
                long roundedDays = (daysSinceEpoch / priceLookupInterval) * priceLookupInterval;
                priceLookupDate = LocalDate.ofEpochDay(roundedDays);
                // Se arredondou para frente, volta um intervalo
                if (priceLookupDate.isAfter(dateForPricing)) {
                    priceLookupDate = priceLookupDate.minusDays(priceLookupInterval);
                }
            }
            
            double price = resolvePriceForDate(state, priceLookupDate, 
                priceLookupDate.equals(dateForPricing) ? dateTimeForPricing : null, 
                quoteService, priceCache);
            
            // Garante que o preço nunca seja 0 se há investimentos válidos
            // Se o preço for 0 mas há quantidade, usa o último preço conhecido ou preço médio
            if (price <= 0 && state.getTotalQuantity() > 0) {
                if (state.lastKnownPrice > 0) {
                    price = state.lastKnownPrice;
                } else {
                    price = state.getAverageCost();
                }
                // Atualiza lastKnownPrice para que próximas datas usem esse valor
                if (price > 0) {
                    state.lastKnownPrice = price;
                }
            }
            
            // Para períodos muito longos, usa interpolação linear entre pontos conhecidos
            if (priceLookupInterval > 1 && !priceLookupDate.equals(dateForPricing)) {
                // Tenta encontrar preços próximos para interpolar
                LocalDate prevDate = priceLookupDate;
                LocalDate nextDate = priceLookupDate.plusDays(priceLookupInterval);
                
                String prevKey = state.symbol + "|" + state.category + "|" + prevDate.toString();
                String nextKey = state.symbol + "|" + state.category + "|" + nextDate.toString();
                
                Double prevPrice = priceCache.get(prevKey);
                Double nextPrice = priceCache.get(nextKey);
                
                if (prevPrice != null && nextPrice != null) {
                    // Interpolação linear
                    long daysBetween = ChronoUnit.DAYS.between(prevDate, nextDate);
                    long daysFromPrev = ChronoUnit.DAYS.between(prevDate, dateForPricing);
                    if (daysBetween > 0) {
                        double ratio = (double) daysFromPrev / daysBetween;
                        price = prevPrice + (nextPrice - prevPrice) * ratio;
                    }
                } else if (prevPrice != null) {
                    price = prevPrice; // Usa o preço anterior se não tiver próximo
                }
            }
            
            double current = state.getTotalQuantity() * price;
            totalCurrent += current;
            
            // Acumula valores por categoria
            categoryInvestedMap.put(category, categoryInvestedMap.getOrDefault(category, 0.0) + invested);
            categoryCurrentMap.put(category, categoryCurrentMap.getOrDefault(category, 0.0) + current);
        }

        labels.add(label);
        investedPoints.add(roundMoney(totalInvested));
        currentPoints.add(roundMoney(totalCurrent));
        
        // Adiciona valores por categoria
        for (String category : allCategoriesSeen) {
            if (categoryInvestedMap.containsKey(category)) {
                // Categoria tem ativos neste ponto
                categoryInvested.computeIfAbsent(category, k -> new ArrayList<>()).add(roundMoney(categoryInvestedMap.get(category)));
                categoryCurrent.computeIfAbsent(category, k -> new ArrayList<>()).add(roundMoney(categoryCurrentMap.get(category)));
            } else {
                // Categoria não tem mais ativos neste ponto, adiciona zero para manter continuidade
                categoryInvested.computeIfAbsent(category, k -> new ArrayList<>()).add(0.0);
                categoryCurrent.computeIfAbsent(category, k -> new ArrayList<>()).add(0.0);
            }
        }
        
        // IMPORTANTE: Processa categorias que apareceram pela primeira vez neste ponto
        for (String category : categoryInvestedMap.keySet()) {
            if (!allCategoriesSeen.contains(category)) {
                // Nova categoria que apareceu pela primeira vez
                allCategoriesSeen.add(category);
                // Preenche com zeros para pontos anteriores
                List<Double> investedList = categoryInvested.computeIfAbsent(category, k -> new ArrayList<>());
                List<Double> currentList = categoryCurrent.computeIfAbsent(category, k -> new ArrayList<>());
                while (investedList.size() < labels.size() - 1) {
                    investedList.add(0.0);
                }
                while (currentList.size() < labels.size() - 1) {
                    currentList.add(0.0);
                }
                // Adiciona o valor atual
                investedList.add(roundMoney(categoryInvestedMap.get(category)));
                currentList.add(roundMoney(categoryCurrentMap.get(category)));
            }
        }
    }

    private String buildAssetKey(Investimento inv) {
        String category = inv.getCategoria() != null ? inv.getCategoria() : "OUTROS";
        String symbol = inv.getNome() != null ? inv.getNome() : "DESCONHECIDO";

        if ("RENDA_FIXA".equalsIgnoreCase(category)) {
            return category + "_" + inv.getIdInvestimento();
        }

        return category + "_" + symbol;
    }

    private double resolvePriceForDate(AssetState state, LocalDate date, LocalDateTime dateTime, QuoteService quoteService, Map<String, Double> priceCache) {
        if ("RENDA_FIXA".equalsIgnoreCase(state.category)) {
            double totalValue = 0.0;
            for (PositionLayer layer : state.layers) {
                totalValue += quoteService.calculateFixedIncomeValue(
                    layer.originalAmount,
                    state.tipoInvestimento,
                    state.tipoRentabilidade,
                    state.indice,
                    state.percentualIndice,
                    state.taxaFixa,
                    layer.aporteDate,
                    state.dataVencimento,
                    date
                );
            }
            double qty = state.getTotalQuantity();
            double price = qty > 0 ? totalValue / qty : 0.0;
            if (price <= 0) {
                price = state.lastKnownPrice > 0 ? state.lastKnownPrice : state.getAverageCost();
            }
            state.lastKnownPrice = price;
            return price;
        }

        // IMPORTANTE: Para datas futuras, usa sempre a última cotação conhecida
        LocalDate today = LocalDate.now();
        boolean isFuture = date != null && date.isAfter(today);
        
        String cacheKey = state.symbol + "|" + state.category + "|" + (dateTime != null ? dateTime.toString() : date.toString());
        if (priceCache.containsKey(cacheKey)) {
            return priceCache.get(cacheKey);
        }

        double price = 0.0;
        
        // Para datas futuras, tenta buscar a cotação atual primeiro (mais recente disponível)
        if (isFuture) {
            // Busca cotação atual para usar como referência para datas futuras
            QuoteService.QuoteResult currentQuote = quoteService.getQuote(state.symbol, state.category, null, null);
            if (currentQuote != null && currentQuote.success && currentQuote.price > 0) {
                price = currentQuote.price;
                String currency = currentQuote.currency != null ? currentQuote.currency : state.currency;
                if (currency != null && !"BRL".equalsIgnoreCase(currency)) {
                    double exchangeRate = quoteService.getExchangeRate(currency, "BRL");
                    price *= exchangeRate;
                }
            }
        } else {
            // Para datas passadas ou atuais, busca cotação normalmente
            QuoteService.QuoteResult quote = quoteService.getQuote(state.symbol, state.category, date, dateTime);
            if (quote != null && quote.success && quote.price > 0) {
                price = quote.price;
                String currency = quote.currency != null ? quote.currency : state.currency;
                if (currency != null && !"BRL".equalsIgnoreCase(currency)) {
                    double exchangeRate = quoteService.getExchangeRate(currency, "BRL");
                    price *= exchangeRate;
                }
            }
        }

        // Se não conseguiu cotação, usa fallback
        if (price <= 0) {
            if (isFuture && state.lastKnownPrice > 0) {
                // Para datas futuras, usa a última cotação conhecida
                price = state.lastKnownPrice;
            } else {
                // Para datas passadas sem cotação, usa última conhecida ou preço médio
                double fallbackPrice = state.lastKnownPrice > 0 ? state.lastKnownPrice : state.getAverageCost();
                if (fallbackPrice > 0) {
                    price = fallbackPrice;
                } else {
                    // Se nem o fallback funcionou, tenta buscar cotação atual como último recurso
                    // Isso pode acontecer se é a primeira vez que processamos este ativo
                    QuoteService.QuoteResult currentQuote = quoteService.getQuote(state.symbol, state.category, null, null);
                    if (currentQuote != null && currentQuote.success && currentQuote.price > 0) {
                        price = currentQuote.price;
                        String currency = currentQuote.currency != null ? currentQuote.currency : state.currency;
                        if (currency != null && !"BRL".equalsIgnoreCase(currency)) {
                            double exchangeRate = quoteService.getExchangeRate(currency, "BRL");
                            price *= exchangeRate;
                        }
                    } else {
                        // Último recurso: se não conseguiu nada, usa o preço médio mesmo que seja 0
                        // Mas isso não deveria acontecer se há investimentos válidos
                        price = state.getAverageCost();
                    }
                }
            }
        }

        // IMPORTANTE: Atualiza lastKnownPrice sempre que temos um preço válido
        // Isso garante que próximas datas usem esse valor quando a cotação falhar
        if (price > 0) {
            if (!isFuture) {
                // Para datas passadas/atuais, sempre atualiza lastKnownPrice
                // Isso garante que se a cotação foi encontrada OU se usamos um fallback válido,
                // o próximo ponto no gráfico terá um preço válido mesmo se a cotação falhar
                state.lastKnownPrice = price;
            } else if (state.lastKnownPrice <= 0) {
                // Se é futura e não tinha última cotação, atualiza com a cotação atual
                state.lastKnownPrice = price;
            }
        }
        
        // Armazena no cache apenas se o preço for válido
        // Se o preço for 0, não armazena para forçar nova tentativa na próxima vez
        // Mas sempre retorna o preço (mesmo que seja 0) para não quebrar o cálculo
        if (price > 0) {
            priceCache.put(cacheKey, price);
        }
        return price;
    }

    private double roundMoney(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
    
    /**
     * Pre-busca cotações em batch para otimizar carregamento de gráficos grandes
     * Busca apenas as cotações necessárias baseado no intervalo de preços
     */
    private void preFetchQuotesInBatch(
        Map<String, AssetState> assetState,
        LocalDate startDate,
        LocalDate endDate,
        int priceLookupInterval,
        QuoteService quoteService,
        Map<String, Double> priceCache
    ) {
        // Para cada ativo único, busca cotações apenas nos intervalos necessários
        Set<String> processedAssets = new HashSet<>();
        for (AssetState state : assetState.values()) {
            if (state.isEmpty() || "RENDA_FIXA".equalsIgnoreCase(state.category)) {
                continue; // Pula renda fixa (não precisa de cotação externa)
            }
            
            String symbol = state.symbol;
            String category = state.category != null ? state.category : "OUTROS";
            
            // Evita processar o mesmo ativo múltiplas vezes
            String assetKey = category + "_" + symbol;
            if (processedAssets.contains(assetKey)) {
                continue;
            }
            processedAssets.add(assetKey);
            
            // Busca cotações apenas nos pontos de intervalo
            int fetched = 0;
            int maxFetches = 100; // Limita requisições por ativo
            for (LocalDate date = startDate; !date.isAfter(endDate) && fetched < maxFetches; date = date.plusDays(priceLookupInterval)) {
                String cacheKey = symbol + "|" + category + "|" + date.toString();
                if (!priceCache.containsKey(cacheKey)) {
                    // Busca a cotação e armazena no cache
                    QuoteService.QuoteResult quote = quoteService.getQuote(symbol, category, date, null);
                    if (quote != null && quote.success && quote.price > 0) {
                        double price = quote.price;
                        String currency = quote.currency != null ? quote.currency : (state.currency != null ? state.currency : "BRL");
                        if (currency != null && !"BRL".equalsIgnoreCase(currency)) {
                            double exchangeRate = quoteService.getExchangeRate(currency, "BRL");
                            price *= exchangeRate;
                        }
                        priceCache.put(cacheKey, price);
                        fetched++;
                    }
                    // Pequena pausa para não sobrecarregar a API
                    if (fetched % 10 == 0 && fetched > 0) {
                        try {
                            Thread.sleep(50); // 50ms de pausa a cada 10 requisições
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            break;
                        }
                    }
                }
            }
        }
    }

    private static class AssetState {
        final String symbol;
        final String category;
        String currency;
        String tipoInvestimento;
        String tipoRentabilidade;
        String indice;
        Double percentualIndice;
        Double taxaFixa;
        LocalDate dataVencimento;
        final List<PositionLayer> layers = new ArrayList<>();
        double lastKnownPrice;

        private AssetState(String symbol, String category) {
            this.symbol = symbol;
            this.category = category;
        }

        static AssetState fromInvestment(Investimento inv) {
            AssetState state = new AssetState(
                inv.getNome() != null ? inv.getNome() : "DESCONHECIDO",
                inv.getCategoria() != null ? inv.getCategoria() : "OUTROS"
            );
            state.updateMetadata(inv);
            return state;
        }

        void updateMetadata(Investimento inv) {
            if (inv.getMoeda() != null) {
                this.currency = inv.getMoeda();
            }
            this.tipoInvestimento = inv.getTipoInvestimento();
            this.tipoRentabilidade = inv.getTipoRentabilidade();
            this.indice = inv.getIndice();
            this.percentualIndice = inv.getPercentualIndice();
            this.taxaFixa = inv.getTaxaFixa();
            if (inv.getDataVencimento() != null) {
                this.dataVencimento = inv.getDataVencimento();
            }
        }

        void applyTransaction(Investimento inv) {
            double qty = inv.getQuantidade();
            
            // Ignora transações com quantidade zero (não devem existir, mas protege contra bugs)
            if (qty == 0.0 || Double.isNaN(qty) || Double.isInfinite(qty)) {
                return;
            }
            
            double unitCost = (qty != 0) ? Math.abs(inv.getValorAporte()) / Math.abs(qty) : 0.0;

            if (qty > 0) {
                // Compra: adiciona nova camada
                PositionLayer layer = new PositionLayer();
                layer.qty = qty;
                layer.unitCost = unitCost;
                layer.aporteDate = inv.getDataAporte();
                layer.originalAmount = Math.abs(inv.getValorAporte());
                layers.add(layer);
            } else if (qty < 0) {
                // Venda: remove quantidade das camadas existentes (FIFO)
                double remaining = Math.abs(qty);
                Iterator<PositionLayer> iterator = layers.iterator();
                while (iterator.hasNext() && remaining > 0) {
                    PositionLayer layer = iterator.next();
                    double qtyToUse = Math.min(remaining, layer.qty);
                    layer.qty -= qtyToUse;
                    remaining -= qtyToUse;
                    // Remove camada se quantidade ficou muito pequena (arredondamento)
                    if (layer.qty <= 0.000001) {
                        iterator.remove();
                    }
                }
            }
        }

        boolean isEmpty() {
            return layers.isEmpty();
        }

        double getTotalQuantity() {
            return layers.stream().mapToDouble(layer -> layer.qty).sum();
        }

        double getTotalCostBasis() {
            return layers.stream().mapToDouble(layer -> layer.qty * layer.unitCost).sum();
        }

        double getAverageCost() {
            double qty = getTotalQuantity();
            if (qty <= 0) {
                return 0.0;
            }
            return getTotalCostBasis() / qty;
        }
    }

    private static class PositionLayer {
        double qty;
        double unitCost;
        LocalDate aporteDate;
        double originalAmount;
    }
}

