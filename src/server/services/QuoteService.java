package server.services;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import javax.net.ssl.SSLException;

/**
 * Serviço para buscar cotações de investimentos de APIs públicas
 * Implementa cache com atualização automática a cada 30 minutos
 */
public class QuoteService {
    private static QuoteService instance;
    private Map<String, CachedQuote> cache;
    private Map<String, CachedExchangeRate> exchangeRateCache; // Cache de taxas de câmbio
    private Set<String> sslFailureCache; // Cache de falhas SSL para evitar múltiplas tentativas
    private Set<String> generalFailureCache; // Cache de falhas gerais para evitar spam de logs
    private Set<String> rateLimitCache; // Cache de rate limit (429) para evitar requisições
    private static final long CACHE_DURATION_MS = 30 * 60 * 1000; // 30 minutos
    private static final long CRYPTO_CACHE_DURATION_MS = 60 * 60 * 1000; // 1 hora
    private static final long HISTORICAL_CACHE_DURATION_MS = 24 * 60 * 60 * 1000; // 24 horas para dados históricos (aumentado drasticamente para otimizar primeira carga)
    private static final long EXCHANGE_RATE_CACHE_DURATION_MS = 60 * 60 * 1000; // 1 hora para taxa de câmbio
    private static final long SSL_FAILURE_CACHE_DURATION_MS = 5 * 60 * 1000; // 5 minutos para falhas SSL
    private static final long GENERAL_FAILURE_CACHE_DURATION_MS = 2 * 60 * 1000; // 2 minutos para falhas gerais
    private static final long RATE_LIMIT_CACHE_DURATION_MS = 10 * 60 * 1000; // 10 minutos para rate limit
    private static final String EXCHANGE_RATE_API = "https://api.exchangerate-api.com/v4/latest/USD";
    
    private QuoteService() {
        this.cache = new ConcurrentHashMap<>();
        this.exchangeRateCache = new ConcurrentHashMap<>();
        this.sslFailureCache = ConcurrentHashMap.newKeySet();
        this.generalFailureCache = ConcurrentHashMap.newKeySet();
        this.rateLimitCache = ConcurrentHashMap.newKeySet();
    }
    
    /**
     * Classe interna para cache de taxa de câmbio
     */
    private static class CachedExchangeRate {
        final double rate;
        final long timestamp;
        final long ttl;
        
        CachedExchangeRate(double rate, long timestamp, long ttl) {
            this.rate = rate;
            this.timestamp = timestamp;
            this.ttl = ttl;
        }
        
        boolean isExpired() {
            return (System.currentTimeMillis() - timestamp) > ttl;
        }
    }
    
    public static synchronized QuoteService getInstance() {
        if (instance == null) {
            instance = new QuoteService();
        }
        return instance;
    }
    
    /**
     * Busca cotação atual ou histórica de um investimento
     * @param date Data da cotação. Se for null ou hoje, retorna cotação atual (sem cache para mostrar variação em tempo real)
     */
    public QuoteResult getQuote(String symbol, String category, LocalDate date) {
        return getQuote(symbol, category, date, null);
    }
    
    /**
     * Busca cotação atual ou histórica de um investimento com horário específico
     * @param date Data da cotação
     * @param dateTime Data e hora específica (usado para buscar preços intraday). Se null, usa apenas a data
     */
    public QuoteResult getQuote(String symbol, String category, LocalDate date, LocalDateTime dateTime) {
        LocalDate today = LocalDate.now();
        boolean isToday = date == null || date.equals(today);
        boolean isIntraday = dateTime != null && dateTime.toLocalDate().equals(today);
        boolean isFuture = date != null && date.isAfter(today);
        
        // Para datas futuras, retorna a cotação atual (mais recente disponível)
        if (isFuture) {
            // Busca cotação atual para datas futuras
            return getQuote(symbol, category, null, null);
        }
        
        // Para dados intraday do dia atual, usa cache mais curto (5 minutos) para mostrar variação
        // Para datas históricas, usa cache de 30 minutos
        if (!isToday || isIntraday) {
            String cacheKey = symbol + "_" + category + "_" + 
                (dateTime != null ? dateTime.toString() : date.toString());
            CachedQuote cached = cache.get(cacheKey);
            if (cached != null && !cached.isExpired()) {
                return cached.quote;
            }
        }
        
        // Busca nova cotação
        QuoteResult quote = fetchQuote(symbol, category, date, dateTime);
        
        // Atualiza cache
        if (quote != null && quote.success) {
            String cacheKey = symbol + "_" + category + "_" + 
                (dateTime != null ? dateTime.toString() : (date != null ? date.toString() : "current"));
            long ttl = CACHE_DURATION_MS;
            if ("CRYPTO".equalsIgnoreCase(category)) {
                if (isIntraday) {
                    ttl = 5 * 60 * 1000; // 5 minutos para dados intraday
                } else if (!isToday) {
                    ttl = HISTORICAL_CACHE_DURATION_MS; // 30 minutos para dados históricos
                } else {
                    ttl = CRYPTO_CACHE_DURATION_MS; // 1 hora para cotações atuais
                }
            }
            cache.put(cacheKey, new CachedQuote(quote, System.currentTimeMillis(), ttl));
        }
        
        return quote;
    }
    
    /**
     * Busca cotação de uma API pública
     */
    private QuoteResult fetchQuote(String symbol, String category, LocalDate date) {
        return fetchQuote(symbol, category, date, null);
    }
    
    /**
     * Busca cotação de uma API pública com horário específico
     */
    private QuoteResult fetchQuote(String symbol, String category, LocalDate date, LocalDateTime dateTime) {
        try {
            // Para ações brasileiras (B3)
            if ("ACAO".equals(category) && symbol.matches("^[A-Z]{4}\\d{1,2}$")) {
                return fetchB3Quote(symbol, date);
            }
            // Para stocks (NYSE, NASDAQ)
            else if ("STOCK".equals(category)) {
                return fetchYahooQuote(symbol, date);
            }
            // Para criptomoedas
            else if ("CRYPTO".equals(category)) {
                return fetchCryptoQuote(symbol, date, dateTime);
            }
            // Para FIIs
            else if ("FII".equals(category)) {
                return fetchB3Quote(symbol, date);
            }
            // Para renda fixa, retorna valor fixo (CDI, Selic, etc)
            else if ("RENDA_FIXA".equals(category)) {
                return fetchFixedIncomeQuote(symbol, date);
            }
            
            return new QuoteResult(false, "Categoria não suportada", 0.0, "BRL");
        } catch (Exception e) {
            System.err.println("Erro ao buscar cotação: " + e.getMessage());
            return new QuoteResult(false, "Erro ao buscar cotação: " + e.getMessage(), 0.0, "BRL");
        }
    }
    
    /**
     * Busca cotação de ações da B3 usando Yahoo Finance (funciona melhor que Alpha Vantage)
     */
    private QuoteResult fetchB3Quote(String symbol, LocalDate date) {
        try {
            // Yahoo Finance funciona com ações brasileiras usando o sufixo .SA
            String yahooSymbol = symbol + ".SA";
            String urlStr;
            
            if (date != null && !date.equals(LocalDate.now())) {
                // Cotação histórica - expande janela para 7 dias para cobrir feriados/fins de semana
                // e pegar o último dia útil anterior
                long timestamp = date.atStartOfDay().toEpochSecond(java.time.ZoneOffset.UTC);
                long timestampStart = timestamp - (7 * 86400); // 7 dias antes
                urlStr = String.format("https://query1.finance.yahoo.com/v8/finance/chart/%s?interval=1d&period1=%d&period2=%d", 
                                     yahooSymbol, timestampStart, timestamp + 86400);
            } else {
                // Cotação atual
                urlStr = String.format("https://query1.finance.yahoo.com/v8/finance/chart/%s?interval=1d&range=1d", yahooSymbol);
            }
            
            String response = httpGet(urlStr);
            
            if (response == null || response.length() < 100) {
                // Se httpGet retornou null, significa que houve erro HTTP (404, etc)
                return new QuoteResult(false, "Investimento não encontrado na base de dados. Por favor, insira o preço manualmente.", 0.0, "BRL");
            }
            
            // Primeiro tenta fazer o parse do preço
            boolean isHistorical = date != null && !date.equals(LocalDate.now());
            double price = parseYahooPrice(response, isHistorical);
            String assetName = parseAssetNameFromYahooResponse(response, symbol);
            
            if (price > 0) {
                // Se conseguiu parsear o preço, retorna sucesso
                return new QuoteResult(true, "Cotação obtida com sucesso", price, "BRL", assetName);
            }
            
            // Se não conseguiu parsear, verifica se há erro explícito na resposta
            // Verifica se há "result":null no nível chart (sem array depois)
            boolean hasError = false;
            int chartIdx = response.indexOf("\"chart\"");
            if (chartIdx >= 0) {
                // Procura "result":null após "chart"
                int resultNullIdx = response.indexOf("\"result\":null", chartIdx);
                if (resultNullIdx > chartIdx) {
                    // Verifica se não há array depois (result:[...])
                    int resultArrayIdx = response.indexOf("\"result\":[", chartIdx);
                    if (resultArrayIdx < 0 || resultArrayIdx > resultNullIdx) {
                        // Verifica se há um objeto error logo depois
                        int errorAfterResult = response.indexOf("\"error\"", resultNullIdx);
                        if (errorAfterResult > resultNullIdx && errorAfterResult < resultNullIdx + 50) {
                            hasError = true;
                        }
                    }
                }
                
                // Verifica se há "error" no nível chart com código "Not Found"
                if (!hasError) {
                    int errorIdx = response.indexOf("\"error\"", chartIdx);
                    if (errorIdx > chartIdx) {
                        // Verifica se há "code":"Not Found" ou "description":"No data found" próximo ao error
                        int codeNotFoundIdx = response.indexOf("\"code\":\"Not Found\"", errorIdx);
                        int descNotFoundIdx = response.indexOf("\"description\":\"No data found", errorIdx);
                        if (codeNotFoundIdx > errorIdx && codeNotFoundIdx < errorIdx + 200) {
                            hasError = true;
                        } else if (descNotFoundIdx > errorIdx && descNotFoundIdx < errorIdx + 200) {
                            hasError = true;
                        }
                    }
                }
            }
            
            if (hasError || response.contains("\"code\":\"Not Found\"") || response.contains("\"description\":\"No data found")) {
                System.err.println("Erro na resposta Yahoo Finance para " + symbol + ": " + response.substring(0, Math.min(200, response.length())));
                return new QuoteResult(false, "Investimento não encontrado na base de dados. Por favor, insira o preço manualmente.", 0.0, "BRL");
            }
            
            // Se não há erro explícito mas não conseguiu parsear, retorna erro
            System.err.println("Não foi possível parsear o preço da resposta Yahoo Finance para " + symbol);
            System.err.println("Resposta (primeiros 500 chars): " + response.substring(0, Math.min(500, response.length())));
            return new QuoteResult(false, "Não foi possível obter a cotação do investimento. Por favor, insira o preço manualmente.", 0.0, "BRL");
        } catch (Exception e) {
            System.err.println("Erro ao buscar cotação B3: " + e.getMessage());
            return new QuoteResult(false, "Erro ao buscar cotação: " + e.getMessage() + ". Por favor, insira o preço manualmente.", 0.0, "BRL");
        }
    }
    
    /**
     * Método alternativo para buscar cotações B3 usando Alpha Vantage ou fallback
     */
    private QuoteResult fetchB3Alternative(String symbol, LocalDate date) {
        try {
            // Tenta usar uma API pública brasileira ou scraping
            // Por enquanto, usa valores baseados em símbolos conhecidos
            String assetName = getDefaultAssetName(symbol);
            
            // Para ações brasileiras conhecidas, usa valores aproximados baseados no hash
            // Em produção, você pode usar uma API paga ou scraping
            int hash = Math.abs(symbol.hashCode());
            double basePrice = 20.0 + (hash % 200); // Preços entre 20 e 220
            
            // Ajusta preço baseado em símbolos conhecidos
            Map<String, Double> knownPrices = new HashMap<>();
            knownPrices.put("ITUB4", 28.50);
            knownPrices.put("PETR4", 32.80);
            knownPrices.put("VALE3", 65.20);
            knownPrices.put("BBDC4", 15.90);
            knownPrices.put("ABEV3", 12.40);
            
            if (knownPrices.containsKey(symbol.toUpperCase())) {
                basePrice = knownPrices.get(symbol.toUpperCase());
            }
            
            return new QuoteResult(true, "Cotação aproximada (use API paga para valores precisos)", basePrice, "BRL", assetName);
        } catch (Exception e) {
            return generateMockQuote(symbol, "BRL", 50.0);
        }
    }
    
    /**
     * Busca cotação de stocks (NYSE, NASDAQ) usando Yahoo Finance API
     */
    private QuoteResult fetchYahooQuote(String symbol, LocalDate date) {
        try {
            // Yahoo Finance API (gratuita)
            String urlStr;
            if (date != null && !date.equals(LocalDate.now())) {
                long timestamp = date.atStartOfDay().toEpochSecond(java.time.ZoneOffset.UTC);
                long timestampStart = timestamp - (7 * 86400); // 7 dias antes
                urlStr = String.format("https://query1.finance.yahoo.com/v8/finance/chart/%s?interval=1d&period1=%d&period2=%d", 
                                     symbol, timestampStart, timestamp + 86400);
            } else {
                urlStr = String.format("https://query1.finance.yahoo.com/v8/finance/chart/%s?interval=1d&range=1d", symbol);
            }
            
            String response = httpGet(urlStr);
            
            if (response == null || response.length() < 100) {
                // Se httpGet retornou null, significa que houve erro HTTP (404, etc)
                System.err.println("Resposta vazia ou inválida do Yahoo Finance para " + symbol);
                return new QuoteResult(false, "Investimento não encontrado na base de dados. Por favor, insira o preço manualmente.", 0.0, "USD");
            }
            
            // Primeiro tenta fazer o parse do preço
            boolean isHistorical = date != null && !date.equals(LocalDate.now());
            double price = parseYahooPrice(response, isHistorical);
            String assetName = parseAssetNameFromYahooResponse(response, symbol);
            
            if (price > 0) {
                // Se conseguiu parsear o preço, retorna sucesso
                return new QuoteResult(true, "Cotação obtida com sucesso", price, "USD", assetName);
            }
            
            // Se não conseguiu parsear, verifica se há erro explícito na resposta
            // Verifica se há "result":null no nível chart (sem array depois)
            boolean hasError = false;
            int chartIdx = response.indexOf("\"chart\"");
            if (chartIdx >= 0) {
                // Procura "result":null após "chart"
                int resultNullIdx = response.indexOf("\"result\":null", chartIdx);
                if (resultNullIdx > chartIdx) {
                    // Verifica se não há array depois (result:[...])
                    int resultArrayIdx = response.indexOf("\"result\":[", chartIdx);
                    if (resultArrayIdx < 0 || resultArrayIdx > resultNullIdx) {
                        // Verifica se há um objeto error logo depois
                        int errorAfterResult = response.indexOf("\"error\"", resultNullIdx);
                        if (errorAfterResult > resultNullIdx && errorAfterResult < resultNullIdx + 50) {
                            hasError = true;
                        }
                    }
                }
                
                // Verifica se há "error" no nível chart com código "Not Found"
                if (!hasError) {
                    int errorIdx = response.indexOf("\"error\"", chartIdx);
                    if (errorIdx > chartIdx) {
                        // Verifica se há "code":"Not Found" ou "description":"No data found" próximo ao error
                        int codeNotFoundIdx = response.indexOf("\"code\":\"Not Found\"", errorIdx);
                        int descNotFoundIdx = response.indexOf("\"description\":\"No data found", errorIdx);
                        if (codeNotFoundIdx > errorIdx && codeNotFoundIdx < errorIdx + 200) {
                            hasError = true;
                        } else if (descNotFoundIdx > errorIdx && descNotFoundIdx < errorIdx + 200) {
                            hasError = true;
                        }
                    }
                }
            }
            
            if (hasError || response.contains("\"code\":\"Not Found\"") || response.contains("\"description\":\"No data found")) {
                System.err.println("Erro na resposta Yahoo Finance para " + symbol + ": " + response.substring(0, Math.min(200, response.length())));
                return new QuoteResult(false, "Investimento não encontrado na base de dados. Por favor, insira o preço manualmente.", 0.0, "USD");
            }
            
            // Se não há erro explícito mas não conseguiu parsear, retorna erro
            System.err.println("Não foi possível parsear o preço da resposta Yahoo Finance para " + symbol);
            System.err.println("Resposta (primeiros 500 chars): " + response.substring(0, Math.min(500, response.length())));
            return new QuoteResult(false, "Não foi possível obter a cotação do investimento. Por favor, insira o preço manualmente.", 0.0, "USD");
        } catch (Exception e) {
            System.err.println("Erro ao buscar cotação Yahoo: " + e.getMessage());
            e.printStackTrace();
            return new QuoteResult(false, "Erro ao buscar cotação: " + e.getMessage() + ". Por favor, insira o preço manualmente.", 0.0, "USD");
        }
    }
    
    /**
     * Busca cotação de criptomoedas usando Binance (principal) e CoinGecko (fallback)
     */
    private QuoteResult fetchCryptoQuote(String symbol, LocalDate date, LocalDateTime dateTime) {
        // Tenta Binance primeiro
        QuoteResult result = fetchCryptoQuoteFromBinance(symbol, date, dateTime);
        
        // Se Binance falhar, tenta CoinGecko como fallback
        if (result == null || !result.success) {
            result = fetchCryptoQuoteFromCoinGecko(symbol, date, dateTime);
        }
        
        return result != null ? result : new QuoteResult(false, "Não foi possível obter a cotação. Por favor, insira o preço manualmente.", 0.0, "USD");
    }
    
    /**
     * Busca cotação de criptomoedas usando Binance API
     */
    private QuoteResult fetchCryptoQuoteFromBinance(String symbol, LocalDate date, LocalDateTime dateTime) {
        try {
            // Mapeamento de símbolos para pares Binance (formato: BTCUSDT, ETHUSDT, etc)
            Map<String, String> binanceSymbols = getBinanceSymbolMap();
            String normalizedSymbol = symbol.trim().replaceAll("\\s+", " ").toUpperCase();
            String binancePair = binanceSymbols.get(normalizedSymbol);
            
            if (binancePair == null) {
                // Tenta criar par padrão: símbolo + USDT
                binancePair = normalizedSymbol + "USDT";
            }
            
            String urlStr;
            LocalDate today = LocalDate.now();
            boolean isIntraday = dateTime != null;
            boolean isToday = date != null && date.equals(today);
            
            if (isIntraday && dateTime != null) {
                // Para horário específico: busca candles horários do dia específico
                LocalDate targetDate = dateTime.toLocalDate();
                long timestampStart = targetDate.atStartOfDay().toEpochSecond(ZoneOffset.UTC) * 1000;
                long timestampEnd = timestampStart + (24 * 60 * 60 * 1000) - 1;
                // Limita o endTime ao tempo atual se for hoje
                if (isToday) {
                    long now = System.currentTimeMillis();
                    if (timestampEnd > now) {
                        timestampEnd = now;
                    }
                }
                // Busca candles de 1 hora do dia específico (até 24 candles)
                urlStr = String.format("https://api.binance.com/api/v3/klines?symbol=%s&interval=1h&startTime=%d&endTime=%d&limit=24", 
                                     binancePair, timestampStart, timestampEnd);
            } else if (date != null && !date.equals(today)) {
                // Dados históricos de dias passados: busca o candle do dia específico (fechamento diário)
                long timestampStart = date.atStartOfDay().toEpochSecond(ZoneOffset.UTC) * 1000;
                long timestampEnd = timestampStart + (24 * 60 * 60 * 1000) - 1;
                // Binance klines: retorna candles OHLCV com intervalo diário
                urlStr = String.format("https://api.binance.com/api/v3/klines?symbol=%s&interval=1d&startTime=%d&endTime=%d&limit=1", 
                                     binancePair, timestampStart, timestampEnd);
            } else if (date != null && date.equals(today)) {
                // Para o dia atual sem horário específico: busca dados horários das últimas 24h
                long timestampEnd = System.currentTimeMillis();
                long timestampStart = timestampEnd - (24 * 60 * 60 * 1000);
                urlStr = String.format("https://api.binance.com/api/v3/klines?symbol=%s&interval=1h&startTime=%d&endTime=%d&limit=24", 
                                     binancePair, timestampStart, timestampEnd);
            } else {
                // Cotação atual: usa endpoint de ticker
                urlStr = String.format("https://api.binance.com/api/v3/ticker/price?symbol=%s", binancePair);
            }
            
            String response = httpGet(urlStr);
            
            if (response == null || response.length() < 10) {
                return null; // Retorna null para tentar fallback
            }
            
            // Verifica se é erro da API
            if (response.contains("\"code\"") || response.contains("\"msg\"")) {
                return null; // Erro da API, tenta fallback
            }
            
            double price = 0.0;
            String assetName = symbol;
            
            if (isIntraday && dateTime != null) {
                // Parse de dados intraday com horário específico - busca o candle mais próximo do horário
                price = parseBinanceKlinesPrice(response, true, dateTime);
            } else if (date != null && !date.equals(today)) {
                // Parse de dados históricos (klines diários) - retorna o preço de fechamento
                price = parseBinanceKlinesPrice(response, false, null);
            } else if (date != null && date.equals(today)) {
                // Parse de dados intraday (klines horários) - retorna o último preço disponível
                price = parseBinanceKlinesPrice(response, true, null);
            } else {
                // Parse de cotação atual (ticker)
                price = parseBinanceTickerPrice(response);
            }
            
            if (price > 0) {
                return new QuoteResult(true, "Cotação obtida com sucesso (Binance)", price, "USD", assetName);
            }
            
            return null;
        } catch (Exception e) {
            System.err.println("Erro ao buscar cotação da Binance: " + e.getMessage());
            return null; // Retorna null para tentar fallback
        }
    }
    
    /**
     * Busca cotação de criptomoedas usando CoinGecko API (fallback)
     */
    private QuoteResult fetchCryptoQuoteFromCoinGecko(String symbol, LocalDate date, LocalDateTime dateTime) {
        try {
            // Mapeamento de símbolos para IDs CoinGecko
            Map<String, String> coinGeckoIds = getCoinGeckoIdMap();
            String normalizedSymbol = symbol.trim().replaceAll("\\s+", " ").toUpperCase();
            String coinId = coinGeckoIds.get(normalizedSymbol);
            
            if (coinId == null) {
                // Tenta usar o símbolo em minúsculas como ID
                coinId = symbol.trim().toLowerCase().replaceAll("\\s+", "-");
            }
            
            String urlStr;
            LocalDate today = LocalDate.now();
            boolean isIntraday = dateTime != null && dateTime.toLocalDate().equals(today);
            
            if (date != null && !date.equals(today)) {
                // Dados históricos: usa market_chart e filtra pela data
                // CoinGecko retorna muitos pontos, então buscamos um período maior e filtramos
                long daysDiff = java.time.temporal.ChronoUnit.DAYS.between(date, today);
                int days = (int) Math.min(Math.max(daysDiff, 1), 365); // Entre 1 e 365 dias
                urlStr = String.format("https://api.coingecko.com/api/v3/coins/%s/market_chart?vs_currency=usd&days=%d", 
                                     coinId, days);
            } else if (isIntraday) {
                // Para horário específico do dia atual: busca dados de 1 dia e filtra pelo horário
                urlStr = String.format("https://api.coingecko.com/api/v3/coins/%s/market_chart?vs_currency=usd&days=1", coinId);
            } else {
                // Cotação atual
                urlStr = String.format("https://api.coingecko.com/api/v3/simple/price?ids=%s&vs_currencies=usd", coinId);
            }
            
            String response = httpGet(urlStr);
            
            if (response == null || response.length() < 10) {
                return new QuoteResult(false, "Investimento não encontrado. Por favor, insira o preço manualmente.", 0.0, "USD");
            }
            
            // Verifica se é erro da API
            if (response.contains("\"error\"") || response.contains("Not Found")) {
                return new QuoteResult(false, "Investimento não encontrado. Por favor, insira o preço manualmente.", 0.0, "USD");
            }
            
            double price = 0.0;
            String assetName = symbol;
            
            if (date != null && !date.equals(today)) {
                // Parse de dados históricos (market_chart)
                price = parseCoinGeckoHistoricalPrice(response, date, null);
            } else if (isIntraday && dateTime != null) {
                // Parse de dados intraday com horário específico
                price = parseCoinGeckoHistoricalPrice(response, date, dateTime);
            } else {
                // Parse de cotação atual (simple/price)
                price = parseCoinGeckoCurrentPrice(response, coinId);
            }
            
            if (price > 0) {
                return new QuoteResult(true, "Cotação obtida com sucesso (CoinGecko)", price, "USD", assetName);
            }
            
            return new QuoteResult(false, "Não foi possível obter a cotação. Por favor, insira o preço manualmente.", 0.0, "USD");
        } catch (Exception e) {
            System.err.println("Erro ao buscar cotação da CoinGecko: " + e.getMessage());
            return new QuoteResult(false, "Erro ao buscar cotação. Por favor, insira o preço manualmente.", 0.0, "USD");
        }
    }
    
    /**
     * Retorna mapeamento de símbolos para pares Binance
     */
    private Map<String, String> getBinanceSymbolMap() {
        Map<String, String> map = new HashMap<>();
        // Símbolos principais
        map.put("BTC", "BTCUSDT");
        map.put("ETH", "ETHUSDT");
        map.put("BNB", "BNBUSDT");
        map.put("ADA", "ADAUSDT");
        map.put("SOL", "SOLUSDT");
        map.put("XRP", "XRPUSDT");
        map.put("DOGE", "DOGEUSDT");
        map.put("DOT", "DOTUSDT");
        map.put("MATIC", "MATICUSDT");
        map.put("LTC", "LTCUSDT");
        // Nomes completos
        map.put("BITCOIN", "BTCUSDT");
        map.put("ETHEREUM", "ETHUSDT");
        map.put("BINANCECOIN", "BNBUSDT");
        map.put("BINANCE COIN", "BNBUSDT");
        map.put("CARDANO", "ADAUSDT");
        map.put("SOLANA", "SOLUSDT");
        map.put("RIPPLE", "XRPUSDT");
        map.put("DOGECOIN", "DOGEUSDT");
        map.put("POLKADOT", "DOTUSDT");
        map.put("POLYGON", "MATICUSDT");
        map.put("LITECOIN", "LTCUSDT");
        return map;
    }
    
    /**
     * Retorna mapeamento de símbolos para IDs CoinGecko
     */
    private Map<String, String> getCoinGeckoIdMap() {
        Map<String, String> map = new HashMap<>();
        // Símbolos principais
        map.put("BTC", "bitcoin");
        map.put("ETH", "ethereum");
        map.put("BNB", "binancecoin");
        map.put("ADA", "cardano");
        map.put("SOL", "solana");
        map.put("XRP", "ripple");
        map.put("DOGE", "dogecoin");
        map.put("DOT", "polkadot");
        map.put("MATIC", "matic-network");
        map.put("LTC", "litecoin");
        // Nomes completos
        map.put("BITCOIN", "bitcoin");
        map.put("ETHEREUM", "ethereum");
        map.put("BINANCECOIN", "binancecoin");
        map.put("BINANCE COIN", "binancecoin");
        map.put("CARDANO", "cardano");
        map.put("SOLANA", "solana");
        map.put("RIPPLE", "ripple");
        map.put("DOGECOIN", "dogecoin");
        map.put("POLKADOT", "polkadot");
        map.put("POLYGON", "matic-network");
        map.put("LITECOIN", "litecoin");
        return map;
    }
    
    /**
     * Parse preço do ticker da Binance: {"symbol":"BTCUSDT","price":"91371.45000000"}
     */
    private double parseBinanceTickerPrice(String response) {
        try {
            int priceIdx = response.indexOf("\"price\"");
            if (priceIdx > 0) {
                int valueStart = response.indexOf(":", priceIdx) + 1;
                // Pula espaços e aspas
                while (valueStart < response.length() && 
                       (Character.isWhitespace(response.charAt(valueStart)) || 
                        response.charAt(valueStart) == '"')) {
                    valueStart++;
                }
                int valueEnd = valueStart;
                while (valueEnd < response.length() && 
                       (Character.isDigit(response.charAt(valueEnd)) || 
                        response.charAt(valueEnd) == '.')) {
                    valueEnd++;
                }
                if (valueEnd > valueStart) {
                    String priceStr = response.substring(valueStart, valueEnd).trim();
                    return Double.parseDouble(priceStr);
                }
            }
        } catch (Exception e) {
            System.err.println("Erro ao parsear preço Binance ticker: " + e.getMessage());
        }
        return 0.0;
    }
    
    /**
     * Parse preço de klines da Binance: [[timestamp, open, high, low, close, volume, ...], ...]
     * @param isIntraday Se true, busca em candles horários. Se false, retorna o primeiro (fechamento do dia).
     * @param targetDateTime Se fornecido, busca o candle mais próximo deste horário. Se null, retorna o último.
     */
    private double parseBinanceKlinesPrice(String response, boolean isIntraday, LocalDateTime targetDateTime) {
        try {
            // Formato: [[timestamp, open, high, low, close, volume, ...], ...]
            if (isIntraday && targetDateTime != null) {
                // Para horário específico, encontra o candle mais próximo
                // Arredonda o horário para o início da hora (candles são de 1h, começam no início de cada hora)
                LocalDateTime roundedDateTime = targetDateTime.withMinute(0).withSecond(0).withNano(0);
                long targetTimestamp = roundedDateTime.toEpochSecond(ZoneOffset.UTC) * 1000;
                
                double closestPrice = 0.0;
                long minDiff = Long.MAX_VALUE;
                
                // Parse todos os candles e encontra o mais próximo
                int pos = 0;
                while (pos < response.length()) {
                    int arrayStart = response.indexOf("[", pos);
                    if (arrayStart < 0) break;
                    int arrayEnd = response.indexOf("]", arrayStart);
                    if (arrayEnd < 0) break;
                    
                    String candle = response.substring(arrayStart + 1, arrayEnd);
                    String[] parts = candle.split(",");
                    if (parts.length > 4) {
                        try {
                            long candleTimestamp = Long.parseLong(parts[0].trim());
                            double closePrice = Double.parseDouble(parts[4].trim().replace("\"", ""));
                            long diff = Math.abs(candleTimestamp - targetTimestamp);
                            if (diff < minDiff) {
                                minDiff = diff;
                                closestPrice = closePrice;
                            }
                            // Se encontrou exatamente o candle do horário, retorna imediatamente
                            if (diff == 0) {
                                return closePrice;
                            }
                        } catch (NumberFormatException e) {
                            // Ignora candles inválidos
                        }
                    }
                    pos = arrayEnd + 1;
                }
                
                if (closestPrice > 0) {
                    return closestPrice;
                }
            } else if (isIntraday) {
                // Para dados intraday sem horário específico, pega o último candle (mais recente)
                int lastArrayStart = response.lastIndexOf("[");
                if (lastArrayStart >= 0) {
                    int lastArrayEnd = response.indexOf("]", lastArrayStart);
                    if (lastArrayEnd > lastArrayStart) {
                        String innerArray = response.substring(lastArrayStart + 1, lastArrayEnd);
                        String[] parts = innerArray.split(",");
                        if (parts.length > 4) {
                            String closeStr = parts[4].trim().replace("\"", "");
                            return Double.parseDouble(closeStr);
                        }
                    }
                }
            } else {
                // Para dados históricos diários, pega o primeiro candle (fechamento do dia)
                int arrayStart = response.indexOf("[[");
                if (arrayStart >= 0) {
                    int innerArrayStart = arrayStart + 1;
                    int innerArrayEnd = response.indexOf("]", innerArrayStart);
                    if (innerArrayEnd > innerArrayStart) {
                        String innerArray = response.substring(innerArrayStart + 1, innerArrayEnd);
                        String[] parts = innerArray.split(",");
                        if (parts.length > 4) {
                            String closeStr = parts[4].trim().replace("\"", "");
                            return Double.parseDouble(closeStr);
                        }
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("Erro ao parsear preço Binance klines: " + e.getMessage());
        }
        return 0.0;
    }
    
    /**
     * Parse preço atual da CoinGecko: {"bitcoin":{"usd":91387}}
     */
    private double parseCoinGeckoCurrentPrice(String response, String coinId) {
        try {
            int usdIdx = response.indexOf("\"usd\"");
            if (usdIdx > 0) {
                int valueStart = response.indexOf(":", usdIdx) + 1;
                // Pula espaços
                while (valueStart < response.length() && Character.isWhitespace(response.charAt(valueStart))) {
                    valueStart++;
                }
                int valueEnd = valueStart;
                while (valueEnd < response.length() && 
                       (Character.isDigit(response.charAt(valueEnd)) || 
                        response.charAt(valueEnd) == '.')) {
                    valueEnd++;
                }
                if (valueEnd > valueStart) {
                    String priceStr = response.substring(valueStart, valueEnd).trim();
                    return Double.parseDouble(priceStr);
                }
            }
        } catch (Exception e) {
            System.err.println("Erro ao parsear preço CoinGecko atual: " + e.getMessage());
        }
        return 0.0;
    }
    
    /**
     * Parse preço histórico da CoinGecko: {"prices":[[timestamp, price], ...]}
     * Encontra o preço mais próximo da data/hora solicitada
     */
    private double parseCoinGeckoHistoricalPrice(String response, LocalDate targetDate, LocalDateTime targetDateTime) {
        try {
            // Converte data/hora alvo para timestamp (milissegundos)
            long targetTimestamp;
            if (targetDateTime != null) {
                targetTimestamp = targetDateTime.toEpochSecond(ZoneOffset.UTC) * 1000;
            } else {
                targetTimestamp = targetDate.atStartOfDay().toEpochSecond(ZoneOffset.UTC) * 1000;
            }
            
            int pricesIdx = response.indexOf("\"prices\"");
            if (pricesIdx > 0) {
                int arrayStart = response.indexOf("[[", pricesIdx);
                if (arrayStart > 0) {
                    // Encontra o array completo de preços
                    int bracketCount = 0;
                    int arrayEnd = arrayStart;
                    for (int i = arrayStart; i < response.length(); i++) {
                        if (response.charAt(i) == '[') bracketCount++;
                        if (response.charAt(i) == ']') bracketCount--;
                        if (bracketCount == 0) {
                            arrayEnd = i + 1;
                            break;
                        }
                    }
                    
                    String pricesArray = response.substring(arrayStart + 1, arrayEnd - 1);
                    // Procura o ponto mais próximo da data alvo
                    double closestPrice = 0.0;
                    long minDiff = Long.MAX_VALUE;
                    
                    // Parse manual do array: [[ts1, price1], [ts2, price2], ...]
                    int pos = 0;
                    while (pos < pricesArray.length()) {
                        int entryStart = pricesArray.indexOf("[", pos);
                        if (entryStart < 0) break;
                        int entryEnd = pricesArray.indexOf("]", entryStart);
                        if (entryEnd < 0) break;
                        
                        String entry = pricesArray.substring(entryStart + 1, entryEnd);
                        String[] parts = entry.split(",");
                        if (parts.length >= 2) {
                            try {
                                long ts = Long.parseLong(parts[0].trim());
                                double price = Double.parseDouble(parts[1].trim());
                                long diff = Math.abs(ts - targetTimestamp);
                                if (diff < minDiff) {
                                    minDiff = diff;
                                    closestPrice = price;
                                }
                            } catch (NumberFormatException e) {
                                // Ignora entradas inválidas
                            }
                        }
                        pos = entryEnd + 1;
                    }
                    
                    if (closestPrice > 0) {
                        return closestPrice;
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("Erro ao parsear preço histórico CoinGecko: " + e.getMessage());
        }
        return 0.0;
    }
    
    /**
     * Busca cotação de renda fixa (CDI, Selic, etc)
     */
    private QuoteResult fetchFixedIncomeQuote(String symbol, LocalDate date) {
        // Renda fixa geralmente tem valor fixo ou taxa de juros
        // Para simplificar, retornamos valor baseado no tipo
        double baseValue = 1000.0; // Valor base para CDI, Selic, etc
        
        if (symbol.toUpperCase().contains("CDI")) {
            baseValue = 1000.0; // CDI geralmente é 100% do valor investido
        } else if (symbol.toUpperCase().contains("SELIC")) {
            baseValue = 1000.0;
        }
        
        String assetName = getDefaultAssetName(symbol);
        return new QuoteResult(true, "Renda fixa - valor base", baseValue, "BRL", assetName);
    }
    
    /**
     * Gera cotação simulada quando API não está disponível
     */
    private QuoteResult generateMockQuote(String symbol, String currency, double basePrice) {
        // Gera um preço baseado no hash do símbolo para consistência
        int hash = symbol.hashCode();
        double price = basePrice + (hash % 1000) / 10.0;
        String assetName = getDefaultAssetName(symbol);
        return new QuoteResult(true, "Cotação simulada (API não disponível)", price, currency, assetName);
    }
    
    /**
     * Retorna nome padrão do ativo baseado no símbolo
     */
    private String getDefaultAssetName(String symbol) {
        // Mapeamento básico de símbolos conhecidos
        Map<String, String> knownAssets = new HashMap<>();
        knownAssets.put("ITUB4", "Itaú Unibanco");
        knownAssets.put("PETR4", "Petrobras");
        knownAssets.put("VALE3", "Vale");
        knownAssets.put("BBDC4", "Banco Bradesco");
        knownAssets.put("ABEV3", "Ambev");
        knownAssets.put("AAPL", "Apple Inc.");
        knownAssets.put("MSFT", "Microsoft Corporation");
        knownAssets.put("GOOGL", "Alphabet Inc.");
        knownAssets.put("AMZN", "Amazon.com Inc.");
        knownAssets.put("TSLA", "Tesla Inc.");
        knownAssets.put("BTC", "Bitcoin");
        knownAssets.put("ETH", "Ethereum");
        
        return knownAssets.getOrDefault(symbol.toUpperCase(), symbol);
    }
    
    /**
     * Extrai nome do ativo de resposta B3/Alpha Vantage
     */
    private String parseAssetNameFromB3Response(String response, String symbol) {
        try {
            // Tenta extrair do campo "Name" ou "2. name"
            int nameIdx = response.indexOf("\"2. name\"");
            if (nameIdx > 0) {
                int valueStart = response.indexOf("\"", nameIdx + 9) + 1;
                int valueEnd = response.indexOf("\"", valueStart);
                if (valueEnd > valueStart) {
                    return response.substring(valueStart, valueEnd);
                }
            }
        } catch (Exception e) {
            // Ignora erros
        }
        return getDefaultAssetName(symbol);
    }
    
    /**
     * Extrai nome do ativo de resposta Yahoo Finance
     */
    private String parseAssetNameFromYahooResponse(String response, String symbol) {
        try {
            // Yahoo Finance retorna o nome em "longName" ou "shortName" dentro de chart.result[0].meta
            String[] nameFields = {"longName", "shortName", "symbol", "exchangeName"};
            
            for (String field : nameFields) {
                int nameIdx = response.indexOf("\"" + field + "\"");
                if (nameIdx > 0) {
                    int valueStart = response.indexOf("\"", nameIdx + field.length() + 3) + 1;
                    if (valueStart > nameIdx + field.length() + 3) {
                        int valueEnd = response.indexOf("\"", valueStart);
                        if (valueEnd > valueStart) {
                            String name = response.substring(valueStart, valueEnd);
                            // Se for um nome válido (não vazio e não é apenas o símbolo), retorna
                            if (!name.isEmpty() && !name.equals(symbol) && name.length() > 2) {
                                return name;
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("Erro ao extrair nome do ativo: " + e.getMessage());
        }
        return getDefaultAssetName(symbol);
    }
    
    /**
     * Extrai nome do ativo de resposta CoinCap
     */
    private String parseAssetNameFromCryptoResponse(String response, String symbol) {
        try {
            // CoinCap retorna o nome em "name" dentro de "data"
            // Para API atual: {"data": {"id": "...", "name": "Bitcoin", ...}}
            // Para API histórico: {"data": [...]} - não tem nome, usa o símbolo
            int dataIdx = response.indexOf("\"data\"");
            if (dataIdx > 0) {
                // Verifica se é objeto (cotação atual) ou array (histórico)
                int objStart = response.indexOf("{", dataIdx);
                int arrStart = response.indexOf("[", dataIdx);
                
                // Se é objeto (cotação atual), procura "name"
                if (objStart > 0 && (arrStart < 0 || objStart < arrStart)) {
                    int nameIdx = response.indexOf("\"name\"", objStart);
                    if (nameIdx > 0) {
                        int valueStart = response.indexOf("\"", nameIdx + 7) + 1;
                        int valueEnd = response.indexOf("\"", valueStart);
                        if (valueEnd > valueStart) {
                            String name = response.substring(valueStart, valueEnd);
                            if (!name.isEmpty() && name.length() > 1) {
                                return name;
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("Erro ao extrair nome crypto: " + e.getMessage());
        }
        return getDefaultAssetName(symbol);
    }
    
    /**
     * Faz requisição HTTP GET
     */
    private String httpGet(String urlStr) {
        // Verifica se há falha SSL recente para esta URL
        String domain = extractDomain(urlStr);
        if (sslFailureCache.contains(domain)) {
            // Log apenas uma vez por período para evitar spam
            return null;
        }
        
        // Verifica se está em rate limit
        if (rateLimitCache.contains(domain)) {
            return null;
        }
        
        try {
            URL url = new URL(urlStr);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(10000); // 10 segundos
            conn.setReadTimeout(10000);
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36");
            conn.setRequestProperty("Accept", "application/json");
            conn.setInstanceFollowRedirects(true);
            
            int responseCode;
            try {
                responseCode = conn.getResponseCode();
            } catch (SSLException sslEx) {
                // Re-lança como exceção SSL para ser tratada no catch geral
                // SSLException é a classe pai de SSLHandshakeException e SSLPeerUnverifiedException
                throw new SSLException("Erro SSL ao conectar: " + sslEx.getMessage(), sslEx);
            }
            // Lê a resposta mesmo em caso de erro HTTP (para poder verificar erros no JSON)
            InputStream inputStream = null;
            if (responseCode == 200) {
                inputStream = conn.getInputStream();
            } else {
                // Para códigos de erro, tenta ler do errorStream
                inputStream = conn.getErrorStream();
                if (inputStream == null) {
                    // Se não há errorStream, tenta ler do inputStream mesmo assim
                    try {
                        inputStream = conn.getInputStream();
                    } catch (Exception e) {
                        // Ignora
                    }
                }
            }
            
            if (inputStream != null) {
                BufferedReader in = new BufferedReader(new InputStreamReader(inputStream, "UTF-8"));
                StringBuilder response = new StringBuilder();
                String line;
                while ((line = in.readLine()) != null) {
                    response.append(line);
                }
                in.close();
                String result = response.toString();
                
                // Trata erro 429 (Too Many Requests) - Rate Limit
                if (responseCode == 429) {
                    // Adiciona ao cache de rate limit
                    rateLimitCache.add(domain);
                    new Thread(() -> {
                        try {
                            Thread.sleep(RATE_LIMIT_CACHE_DURATION_MS);
                            rateLimitCache.remove(domain);
                        } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                        }
                    }).start();
                    
                    String failureKey = domain + "_429";
                    if (!generalFailureCache.contains(failureKey)) {
                        generalFailureCache.add(failureKey);
                        new Thread(() -> {
                            try {
                                Thread.sleep(RATE_LIMIT_CACHE_DURATION_MS);
                                generalFailureCache.remove(failureKey);
                            } catch (InterruptedException ie) {
                                Thread.currentThread().interrupt();
                            }
                        }).start();
                        System.err.println("Rate limit (429) atingido para " + domain + ". Requisições serão ignoradas por 10 minutos.");
                    }
                    return null;
                }
                
                // Log reduzido para debug
                if (responseCode != 200 && responseCode != 404) {
                    System.err.println("Erro HTTP " + responseCode + " para: " + domain);
                }
                return result;
            } else {
                // Trata erro 429 também quando não há inputStream
                if (responseCode == 429) {
                    rateLimitCache.add(domain);
                    new Thread(() -> {
                        try {
                            Thread.sleep(RATE_LIMIT_CACHE_DURATION_MS);
                            rateLimitCache.remove(domain);
                        } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                        }
                    }).start();
                    return null;
                }
                
                if (responseCode != 404) {
                    System.err.println("Erro HTTP " + responseCode + " para: " + domain + " - Não foi possível ler a resposta");
                }
            }
        } catch (java.net.SocketTimeoutException e) {
            // Usa cache geral para timeouts
            String failureKey = domain + "_timeout";
            if (!generalFailureCache.contains(failureKey)) {
                generalFailureCache.add(failureKey);
                new Thread(() -> {
                    try {
                        Thread.sleep(GENERAL_FAILURE_CACHE_DURATION_MS);
                        generalFailureCache.remove(failureKey);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                    }
                }).start();
                System.err.println("Timeout na requisição HTTP para: " + domain);
            }
        } catch (java.net.SocketException e) {
            // Trata erros SSL/TLS
            if (e.getMessage() != null && 
                (e.getMessage().contains("SSL") || 
                 e.getMessage().contains("TLS") || 
                 e.getMessage().contains("trust store") ||
                 e.getMessage().contains("NoSuchAlgorithmException"))) {
                // Adiciona ao cache de falhas SSL
                sslFailureCache.add(domain);
                // Remove após o período de cache
                new Thread(() -> {
                    try {
                        Thread.sleep(SSL_FAILURE_CACHE_DURATION_MS);
                        sslFailureCache.remove(domain);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                    }
                }).start();
                // Log apenas uma vez (usa um Set separado para logs)
                String logKey = domain + "_ssl_logged";
                if (!sslFailureCache.contains(logKey)) {
                    System.err.println("Erro SSL/TLS ao conectar com " + domain + ". Requisições serão ignoradas por 5 minutos.");
                    sslFailureCache.add(logKey);
                    // Remove a flag de log após um tempo menor
                    new Thread(() -> {
                        try {
                            Thread.sleep(SSL_FAILURE_CACHE_DURATION_MS);
                            sslFailureCache.remove(logKey);
                        } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                        }
                    }).start();
                }
            } else {
                // Para erros de conexão não-SSL, usa cache geral
                String failureKey = domain + "_connection";
                if (!generalFailureCache.contains(failureKey)) {
                    generalFailureCache.add(failureKey);
                    new Thread(() -> {
                        try {
                            Thread.sleep(GENERAL_FAILURE_CACHE_DURATION_MS);
                            generalFailureCache.remove(failureKey);
                        } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                        }
                    }).start();
                    System.err.println("Erro de conexão para " + domain + ": " + e.getMessage());
                }
            }
        } catch (java.net.UnknownHostException e) {
            // Erro de DNS - usa cache geral
            String failureKey = domain + "_dns";
            if (!generalFailureCache.contains(failureKey)) {
                generalFailureCache.add(failureKey);
                new Thread(() -> {
                    try {
                        Thread.sleep(GENERAL_FAILURE_CACHE_DURATION_MS);
                        generalFailureCache.remove(failureKey);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                    }
                }).start();
                System.err.println("Erro DNS (host não encontrado) para: " + domain);
            }
        } catch (Exception e) {
            // Verifica se é erro SSL/TLS - verifica a exceção diretamente e suas causas
            boolean isSSLError = false;
            String errorMsg = e.getMessage();
            String className = e.getClass().getName();
            
            // Verifica se é exceção SSL diretamente
            if (className.contains("SSL") || className.contains("TLS") ||
                e instanceof javax.net.ssl.SSLException ||
                e instanceof javax.net.ssl.SSLHandshakeException ||
                e instanceof javax.net.ssl.SSLPeerUnverifiedException) {
                isSSLError = true;
            }
            
            // Verifica nas causas
            Throwable cause = e.getCause();
            while (cause != null && !isSSLError) {
                String causeClassName = cause.getClass().getName();
                if (causeClassName.contains("SSL") || causeClassName.contains("TLS") ||
                    cause instanceof java.security.KeyManagementException ||
                    cause instanceof java.security.NoSuchAlgorithmException ||
                    cause instanceof javax.net.ssl.SSLException ||
                    (cause.getMessage() != null && 
                     (cause.getMessage().contains("SSL") || 
                      cause.getMessage().contains("TLS") || 
                      cause.getMessage().contains("trust store") ||
                      cause.getMessage().contains("certificate")))) {
                    isSSLError = true;
                    break;
                }
                cause = cause.getCause();
            }
            
            // Verifica na mensagem de erro também
            if (!isSSLError && errorMsg != null) {
                String lowerMsg = errorMsg.toLowerCase();
                if (lowerMsg.contains("ssl") || lowerMsg.contains("tls") || 
                    lowerMsg.contains("certificate") || lowerMsg.contains("trust store")) {
                    isSSLError = true;
                }
            }
            
            if (isSSLError) {
                // Adiciona ao cache de falhas SSL
                sslFailureCache.add(domain);
                new Thread(() -> {
                    try {
                        Thread.sleep(SSL_FAILURE_CACHE_DURATION_MS);
                        sslFailureCache.remove(domain);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                    }
                }).start();
                String logKey = domain + "_ssl_logged";
                if (!sslFailureCache.contains(logKey)) {
                    System.err.println("Erro SSL/TLS ao conectar com " + domain + ". Requisições serão ignoradas por 5 minutos.");
                    sslFailureCache.add(logKey);
                    // Remove a flag de log após o período
                    new Thread(() -> {
                        try {
                            Thread.sleep(SSL_FAILURE_CACHE_DURATION_MS);
                            sslFailureCache.remove(logKey);
                        } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                        }
                    }).start();
                }
            } else {
                // Para erros não-SSL, usa cache geral para evitar spam de logs
                String failureKey = domain + "_general";
                if (!generalFailureCache.contains(failureKey)) {
                    generalFailureCache.add(failureKey);
                    // Remove após o período de cache
                    new Thread(() -> {
                        try {
                            Thread.sleep(GENERAL_FAILURE_CACHE_DURATION_MS);
                            generalFailureCache.remove(failureKey);
                        } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                        }
                    }).start();
                    
                    // Log apenas uma vez por período
                    if (errorMsg != null && !errorMsg.contains("404")) {
                        System.err.println("Erro na requisição HTTP para " + domain + ": " + errorMsg);
                    }
                }
            }
        }
        return null;
    }
    
    /**
     * Extrai o domínio de uma URL
     */
    private String extractDomain(String urlStr) {
        try {
            URL url = new URL(urlStr);
            return url.getHost();
        } catch (Exception e) {
            return urlStr;
        }
    }
    
    /**
     * Parse simples de preço de resposta JSON (Alpha Vantage) - mantido para compatibilidade
     */
    private double parsePriceFromResponse(String response, LocalDate date) {
        try {
            if (date != null && !date.equals(LocalDate.now())) {
                // Parse histórico
                String dateStr = date.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
                int idx = response.indexOf("\"" + dateStr + "\"");
                if (idx > 0) {
                    int closeIdx = response.indexOf("\"4. close\"", idx);
                    if (closeIdx > 0) {
                        int valueStart = response.indexOf("\"", closeIdx + 10) + 1;
                        int valueEnd = response.indexOf("\"", valueStart);
                        String priceStr = response.substring(valueStart, valueEnd);
                        return Double.parseDouble(priceStr);
                    }
                }
            } else {
                // Parse cotação atual
                int priceIdx = response.indexOf("\"05. price\"");
                if (priceIdx > 0) {
                    int valueStart = response.indexOf("\"", priceIdx + 11) + 1;
                    int valueEnd = response.indexOf("\"", valueStart);
                    String priceStr = response.substring(valueStart, valueEnd);
                    return Double.parseDouble(priceStr);
                }
            }
        } catch (Exception e) {
            // Ignora erros de parse
        }
        return 0.0;
    }
    
    /**
     * Parse preço de resposta Yahoo Finance
     */
    private double parseYahooPrice(String response, boolean isHistorical) {
        try {
            // Se for busca histórica, tenta PRIMEIRO e ÚNICO buscar nos arrays (chart indicators)
            // pois os campos de metadata (regularMarketPrice) trazem o valor ATUAL, o que estaria errado para histórico.
            if (isHistorical) {
                double priceFromArray = parsePriceFromYahooArray(response);
                if (priceFromArray > 0) {
                    return priceFromArray;
                } else {
                    // Se não encontrou no array histórico, NÃO tenta pegar do metadata atual.
                    // Isso previne o bug de retornar preço de hoje para datas passadas sem negociação (ex: feriados).
                    // Se chegou aqui, é porque não achou dados no período solicitado (mesmo com janela de 7 dias).
                    System.err.println("Não foi possível encontrar cotação histórica no array (periodo sem negociação?)");
                    return 0.0; 
                }
            }

            // Para cotação ATUAL, tenta vários campos possíveis (em ordem de prioridade)
            String[] priceFields = {"regularMarketPrice", "previousClose", "close", "price"};
            
            for (String field : priceFields) {
                int priceIdx = response.indexOf("\"" + field + "\"");
                if (priceIdx > 0) {
                    // Verifica se está dentro de um objeto válido (não dentro de uma string)
                    // Procura o próximo ":" após o campo
                    int colonIdx = response.indexOf(":", priceIdx);
                    if (colonIdx > priceIdx) {
                        int valueStart = colonIdx + 1;
                        // Pula espaços
                        while (valueStart < response.length() && Character.isWhitespace(response.charAt(valueStart))) {
                            valueStart++;
                        }
                        
                        // Encontra o fim do número
                        int valueEnd = valueStart;
                        while (valueEnd < response.length()) {
                            char ch = response.charAt(valueEnd);
                            // Aceita dígitos, ponto decimal, e sinais de notação científica
                            if (Character.isDigit(ch) || ch == '.' || ch == '-' || ch == '+' || ch == 'e' || ch == 'E') {
                                // Para sinais + ou -, verifica se é notação científica (deve ter 'e' ou 'E' antes)
                                if ((ch == '-' || ch == '+') && valueEnd > valueStart) {
                                    char prevChar = response.charAt(valueEnd - 1);
                                    if (prevChar != 'e' && prevChar != 'E') {
                                        break; // Não é notação científica, para aqui
                                    }
                                }
                                valueEnd++;
                            } else {
                                break; // Caractere inválido, para aqui
                            }
                        }
                        
                        // Se encontrou vírgula ou }, para antes
                        int commaIdx = response.indexOf(",", valueStart);
                        int braceIdx = response.indexOf("}", valueStart);
                        if (commaIdx > 0 && commaIdx < valueEnd) valueEnd = commaIdx;
                        if (braceIdx > 0 && braceIdx < valueEnd) valueEnd = braceIdx;
                        
                        if (valueEnd > valueStart) {
                            String priceStr = response.substring(valueStart, valueEnd).trim();
                            // Remove caracteres não numéricos no final
                            priceStr = priceStr.replaceAll("[^0-9.Ee+-]$", "");
                            if (!priceStr.isEmpty()) {
                                try {
                                    double price = Double.parseDouble(priceStr);
                                    if (price > 0) {
                                        System.out.println("Preço encontrado no campo '" + field + "': " + price);
                                        return price;
                                    }
                                } catch (NumberFormatException e) {
                                    // Continua tentando próximo campo
                                }
                            }
                        }
                    }
                }
            }
            
            // Se não for histórico (ou se falhou acima), tenta array como fallback
            if (!isHistorical) {
                 double priceFromArray = parsePriceFromYahooArray(response);
                 if (priceFromArray > 0) return priceFromArray;
            }
            
        } catch (Exception e) {
            System.err.println("Erro ao fazer parse do preço Yahoo: " + e.getMessage());
        }
        return 0.0;
    }

    private double parsePriceFromYahooArray(String response) {
        try {
            // Tenta buscar em arrays (result.chart.result[0].indicators.quote[0].close)
            int resultIdx = response.indexOf("\"result\"");
            if (resultIdx > 0) {
                int indicatorsIdx = response.indexOf("\"indicators\"", resultIdx);
                if (indicatorsIdx > 0) {
                    int quoteIdx = response.indexOf("\"quote\"", indicatorsIdx);
                    if (quoteIdx > 0) {
                        int closeIdx = response.indexOf("\"close\"", quoteIdx);
                        if (closeIdx > 0) {
                            // Procura array de valores
                            int arrayStart = response.indexOf("[", closeIdx);
                            if (arrayStart > 0) {
                                int arrayEnd = response.indexOf("]", arrayStart);
                                if (arrayEnd > arrayStart) {
                                    String arrayContent = response.substring(arrayStart + 1, arrayEnd);
                                    // Pega o último valor não-nulo
                                    String[] values = arrayContent.split(",");
                                    for (int i = values.length - 1; i >= 0; i--) {
                                        String val = values[i].trim();
                                        if (!val.equals("null") && !val.isEmpty()) {
                                            try {
                                                double price = Double.parseDouble(val);
                                                if (price > 0) {
                                                    return price;
                                                }
                                            } catch (NumberFormatException e) {
                                                // Continua
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("Erro ao extrair preço do array Yahoo: " + e.getMessage());
        }
        return 0.0;
    }
    
    /**
     * Parse preço de resposta CoinCap
     */
    private double parseCryptoPrice(String response, String coinId, boolean isHistorical) {
        try {
            if (isHistorical) {
                // Para API histórico: {"data": [{"priceUsd": "50000.00", "time": 1234567890000, ...}]}
                // Pega o último item do array (mais próximo da data solicitada)
                int dataIdx = response.indexOf("\"data\"");
                if (dataIdx > 0) {
                    int arrayStart = response.indexOf("[", dataIdx);
                    if (arrayStart > 0) {
                        int arrayEnd = response.indexOf("]", arrayStart);
                        if (arrayEnd > arrayStart) {
                            String arrayContent = response.substring(arrayStart + 1, arrayEnd);
                            // Procura o último "priceUsd" no array
                            int lastPriceUsdIdx = arrayContent.lastIndexOf("\"priceUsd\"");
                            if (lastPriceUsdIdx > 0) {
                                int valueStart = arrayContent.indexOf(":", lastPriceUsdIdx) + 1;
                                // Pula espaços e aspas
                                while (valueStart < arrayContent.length() && 
                                       (Character.isWhitespace(arrayContent.charAt(valueStart)) || 
                                        arrayContent.charAt(valueStart) == '"')) {
                                    valueStart++;
                                }
                                int valueEnd = valueStart;
                                // Encontra o fim do número (pode estar entre aspas)
                                while (valueEnd < arrayContent.length()) {
                                    char ch = arrayContent.charAt(valueEnd);
                                    if (ch == '"' || ch == ',' || ch == '}') {
                                        break;
                                    }
                                    if (Character.isDigit(ch) || ch == '.' || ch == 'e' || ch == 'E' || ch == '-' || ch == '+') {
                                        valueEnd++;
                                    } else {
                                        break;
                                    }
                                }
                                
                                if (valueEnd > valueStart) {
                                    String priceStr = arrayContent.substring(valueStart, valueEnd).trim();
                                    if (!priceStr.isEmpty()) {
                                        return Double.parseDouble(priceStr);
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                // Para API atual: {"data": {"id": "bitcoin", "priceUsd": "50000.00", ...}}
                int dataIdx = response.indexOf("\"data\"");
                if (dataIdx > 0) {
                    int priceUsdIdx = response.indexOf("\"priceUsd\"", dataIdx);
                    if (priceUsdIdx > 0) {
                        int valueStart = response.indexOf(":", priceUsdIdx) + 1;
                        // Pula espaços e aspas
                        while (valueStart < response.length() && 
                               (Character.isWhitespace(response.charAt(valueStart)) || 
                                response.charAt(valueStart) == '"')) {
                            valueStart++;
                        }
                        int valueEnd = valueStart;
                        // Encontra o fim do número (pode estar entre aspas)
                        while (valueEnd < response.length()) {
                            char ch = response.charAt(valueEnd);
                            if (ch == '"' || ch == ',' || ch == '}') {
                                break;
                            }
                            if (Character.isDigit(ch) || ch == '.' || ch == 'e' || ch == 'E' || ch == '-' || ch == '+') {
                                valueEnd++;
                            } else {
                                break;
                            }
                        }
                        
                        // Se encontrou vírgula ou }, para antes
                        int commaIdx = response.indexOf(",", valueStart);
                        int braceIdx = response.indexOf("}", valueStart);
                        if (commaIdx > 0 && commaIdx < valueEnd) valueEnd = commaIdx;
                        if (braceIdx > 0 && braceIdx < valueEnd) valueEnd = braceIdx;
                        
                        if (valueEnd > valueStart) {
                            String priceStr = response.substring(valueStart, valueEnd).trim();
                            if (!priceStr.isEmpty()) {
                                return Double.parseDouble(priceStr);
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("Erro ao fazer parse do preço CoinCap: " + e.getMessage());
        }
        return 0.0;
    }
    
    /**
     * Busca taxa de câmbio USD para BRL (com cache)
     */
    public double getExchangeRate(String from, String to) {
        if (from.equals(to)) return 1.0;
        
        String cacheKey = from + "_" + to;
        
        // Verifica cache
        CachedExchangeRate cached = exchangeRateCache.get(cacheKey);
        if (cached != null && !cached.isExpired()) {
            return cached.rate;
        }
        
        // Verifica se está em rate limit
        if (rateLimitCache.contains("exchangerate-api.com")) {
            // Usa taxa em cache mesmo se expirada, ou taxa fixa
            if (cached != null) {
                return cached.rate;
            }
            // Fallback: taxa aproximada USD/BRL
            return 6.0;
        }
        
        try {
            if (from.equals("USD") && to.equals("BRL")) {
                String response = httpGet(EXCHANGE_RATE_API);
                if (response != null && !response.isEmpty()) {
                    int brlIdx = response.indexOf("\"BRL\"");
                    if (brlIdx > 0) {
                        int valueStart = response.indexOf(":", brlIdx) + 1;
                        int valueEnd = response.indexOf(",", valueStart);
                        if (valueEnd == -1) valueEnd = response.indexOf("}", valueStart);
                        String rateStr = response.substring(valueStart, valueEnd).trim();
                        double rate = Double.parseDouble(rateStr);
                        
                        // Atualiza cache
                        exchangeRateCache.put(cacheKey, new CachedExchangeRate(
                            rate, System.currentTimeMillis(), EXCHANGE_RATE_CACHE_DURATION_MS));
                        return rate;
                    }
                }
            }
        } catch (Exception e) {
            // Se houver erro, usa cache expirado se disponível
            if (cached != null) {
                return cached.rate;
            }
        }
        
        // Fallback: taxa aproximada USD/BRL
        double fallbackRate = 6.0;
        exchangeRateCache.put(cacheKey, new CachedExchangeRate(
            fallbackRate, System.currentTimeMillis(), EXCHANGE_RATE_CACHE_DURATION_MS));
        return fallbackRate;
    }
    
    /**
     * Limpa cache expirado
     */
    public void cleanExpiredCache() {
        cache.entrySet().removeIf(entry -> entry.getValue().isExpired());
    }
    
    /**
     * Calcula o valor atual de um investimento de renda fixa
     * baseado no tipo de rentabilidade e índice escolhido, considerando impostos
     */
    public double calculateFixedIncomeValue(double valorAporte, String tipoInvestimento,
                                           String tipoRentabilidade, String indice, Double percentualIndice, Double taxaFixa, 
                                           LocalDate dataAporte, LocalDate dataVencimento, LocalDate referenceDate) {
        if (dataVencimento == null || dataAporte == null) {
            return valorAporte; // Se não tem vencimento, retorna o valor original
        }
        
        LocalDate effectiveDate = referenceDate != null ? referenceDate : LocalDate.now();
        LocalDate dataFinal = effectiveDate.isBefore(dataVencimento) ? effectiveDate : dataVencimento;
        
        long diasTotais = java.time.temporal.ChronoUnit.DAYS.between(dataAporte, dataVencimento);
        long diasDecorridos = java.time.temporal.ChronoUnit.DAYS.between(dataAporte, dataFinal);
        
        if (diasTotais <= 0 || diasDecorridos < 0) {
            return valorAporte;
        }
        
        double taxaAnual = 0.0;
        
        if ("PRE_FIXADO".equals(tipoRentabilidade)) {
            // Pré-fixado: usa a taxa fixa fornecida (ex: 15,5% a.a.)
            taxaAnual = taxaFixa != null ? taxaFixa : 0.0;
        } else if ("POS_FIXADO".equals(tipoRentabilidade) || "POS_FIXADO_TAXA".equals(tipoRentabilidade)) {
            // Pós-fixado: busca taxa do índice (real) e aplica percentual (ex: 115% do CDI)
            double taxaIndice = getIndexRate(indice);
            double percentual = percentualIndice != null ? percentualIndice : 100.0;
            taxaAnual = taxaIndice * (percentual / 100.0);
            
            // Se for pós-fixado + taxa fixa, soma a taxa fixa (ex: 95% do CDI + 1,5% a.a.)
            if ("POS_FIXADO_TAXA".equals(tipoRentabilidade) && taxaFixa != null) {
                taxaAnual += taxaFixa;
            }
        }
        
        // Calcula taxa diária (considerando ano comercial de 252 dias úteis)
        double taxaDiaria = taxaAnual / 100.0 / 252.0;
        
        // Calcula valor bruto com juros compostos dia a dia
        double valorBruto = valorAporte;
        for (long i = 0; i < diasDecorridos; i++) {
            valorBruto *= (1 + taxaDiaria);
        }
        
        // Calcula rendimento bruto
        double rendimentoBruto = valorBruto - valorAporte;
        
        // Verifica se há imposto de renda (LCI e LCA são isentos)
        boolean isentoIR = tipoInvestimento != null && 
                          ("LCI".equals(tipoInvestimento) || "LCA".equals(tipoInvestimento));
        
        double rendimentoLiquido = rendimentoBruto;
        if (!isentoIR && rendimentoBruto > 0) {
            // Calcula alíquota de IR baseada na tabela regressiva
            double aliquotaIR = getIRTax(diasDecorridos);
            double imposto = rendimentoBruto * (aliquotaIR / 100.0);
            rendimentoLiquido = rendimentoBruto - imposto;
        }
        
        // Retorna valor líquido (aporte + rendimento líquido)
        return valorAporte + rendimentoLiquido;
    }
    
    /**
     * Retorna a alíquota de Imposto de Renda baseada na tabela regressiva
     * Tabela regressiva:
     * - Até 180 dias: 22,5%
     * - De 181 a 360 dias: 20%
     * - De 361 a 720 dias: 17,5%
     * - Acima de 720 dias: 15%
     */
    private double getIRTax(long dias) {
        if (dias <= 180) {
            return 22.5;
        } else if (dias <= 360) {
            return 20.0;
        } else if (dias <= 720) {
            return 17.5;
        } else {
            return 15.0;
        }
    }
    
    /**
     * Retorna a taxa anual atual de um índice buscando de APIs reais
     * Tenta buscar de APIs públicas, com fallback para valores aproximados
     */
    private double getIndexRate(String indice) {
        if (indice == null) {
            return 0.0;
        }
        
        try {
            switch (indice.toUpperCase()) {
                case "SELIC":
                    return getSELICRate();
                case "CDI":
                    return getCDIRate();
                case "IPCA":
                    return getIPCARate();
                case "PRE":
                    return 12.0; // Taxa pré-fixada padrão (deveria vir do investimento)
                default:
                    return 10.0; // Taxa padrão
            }
        } catch (Exception e) {
            System.err.println("Erro ao buscar taxa do índice " + indice + ": " + e.getMessage());
            // Fallback para valores aproximados
            return getFallbackRate(indice);
        }
    }
    
    /**
     * Busca taxa SELIC atual da API Brasil API
     */
    private double getSELICRate() {
        try {
            // API Brasil API - taxa SELIC
            String url = "https://brasilapi.com.br/api/taxas/v1/selic";
            String response = httpGet(url);
            
            if (response != null && response.contains("valor")) {
                // Procura pelo campo "valor" na resposta
                int valorIdx = response.indexOf("\"valor\"");
                if (valorIdx > 0) {
                    int valueStart = response.indexOf(":", valorIdx) + 1;
                    int valueEnd = response.indexOf(",", valueStart);
                    if (valueEnd == -1) valueEnd = response.indexOf("}", valueStart);
                    String rateStr = response.substring(valueStart, valueEnd).trim();
                    double rate = Double.parseDouble(rateStr);
                    return rate; // Retorna taxa anual
                }
            }
        } catch (Exception e) {
            System.err.println("Erro ao buscar SELIC: " + e.getMessage());
        }
        // Fallback
        return 10.5;
    }
    
    /**
     * Busca taxa CDI atual (aproximação: CDI geralmente é um pouco menor que SELIC)
     */
    private double getCDIRate() {
        try {
            // CDI geralmente é 0,1% a 0,2% menor que SELIC
            double selic = getSELICRate();
            return selic - 0.15; // Aproximação
        } catch (Exception e) {
            System.err.println("Erro ao buscar CDI: " + e.getMessage());
        }
        // Fallback
        return 10.35;
    }
    
    /**
     * Busca taxa IPCA acumulado em 12 meses
     */
    private double getIPCARate() {
        try {
            // API Brasil API - IPCA
            String url = "https://brasilapi.com.br/api/ibge/inflacao/v1/ipca";
            String response = httpGet(url);
            
            if (response != null && response.contains("valor")) {
                // Procura pelo último valor do IPCA acumulado em 12 meses
                // A API retorna um array, precisamos pegar o último valor acumulado
                int lastAccumulatedIdx = response.lastIndexOf("\"acumulado12Meses\"");
                if (lastAccumulatedIdx > 0) {
                    int valueStart = response.indexOf(":", lastAccumulatedIdx) + 1;
                    int valueEnd = response.indexOf(",", valueStart);
                    if (valueEnd == -1) valueEnd = response.indexOf("}", valueStart);
                    String rateStr = response.substring(valueStart, valueEnd).trim();
                    double rate = Double.parseDouble(rateStr);
                    return rate;
                }
            }
        } catch (Exception e) {
            System.err.println("Erro ao buscar IPCA: " + e.getMessage());
        }
        // Fallback
        return 4.62;
    }
    
    /**
     * Retorna taxa de fallback quando não consegue buscar da API
     */
    private double getFallbackRate(String indice) {
        switch (indice.toUpperCase()) {
            case "SELIC":
                return 10.5;
            case "CDI":
                return 10.35;
            case "IPCA":
                return 4.62;
            default:
                return 10.0;
        }
    }
    
    /**
     * Classe para armazenar cotação em cache
     */
    private static class CachedQuote {
        QuoteResult quote;
        long timestamp;
        long ttl;
        
        CachedQuote(QuoteResult quote, long timestamp, long ttl) {
            this.quote = quote;
            this.timestamp = timestamp;
            this.ttl = ttl;
        }
        
        boolean isExpired() {
            return (System.currentTimeMillis() - timestamp) > ttl;
        }
    }
    
    /**
     * Resultado de uma busca de cotação
     */
    public static class QuoteResult {
        public boolean success;
        public String message;
        public double price;
        public String currency;
        public String assetName; // Nome completo do ativo
        
        public QuoteResult(boolean success, String message, double price, String currency) {
            this(success, message, price, currency, null);
        }
        
        public QuoteResult(boolean success, String message, double price, String currency, String assetName) {
            this.success = success;
            this.message = message;
            this.price = price;
            this.currency = currency;
            this.assetName = assetName;
        }
    }
}
