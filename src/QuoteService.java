import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Serviço para buscar cotações de investimentos de APIs públicas
 * Implementa cache com atualização automática a cada 30 minutos
 */
public class QuoteService {
    private static QuoteService instance;
    private Map<String, CachedQuote> cache;
    private static final long CACHE_DURATION_MS = 30 * 60 * 1000; // 30 minutos
    private static final String EXCHANGE_RATE_API = "https://api.exchangerate-api.com/v4/latest/USD";
    
    private QuoteService() {
        this.cache = new ConcurrentHashMap<>();
    }
    
    public static synchronized QuoteService getInstance() {
        if (instance == null) {
            instance = new QuoteService();
        }
        return instance;
    }
    
    /**
     * Busca cotação atual ou histórica de um investimento
     */
    public QuoteResult getQuote(String symbol, String category, LocalDate date) {
        String cacheKey = symbol + "_" + category + "_" + (date != null ? date.toString() : "current");
        
        // Verifica cache
        CachedQuote cached = cache.get(cacheKey);
        if (cached != null && !cached.isExpired()) {
            return cached.quote;
        }
        
        // Busca nova cotação
        QuoteResult quote = fetchQuote(symbol, category, date);
        
        // Atualiza cache
        if (quote != null && quote.success) {
            cache.put(cacheKey, new CachedQuote(quote, System.currentTimeMillis()));
        }
        
        return quote;
    }
    
    /**
     * Busca cotação de uma API pública
     */
    private QuoteResult fetchQuote(String symbol, String category, LocalDate date) {
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
                return fetchCryptoQuote(symbol, date);
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
                // Cotação histórica
                long timestamp = date.atStartOfDay().toEpochSecond(java.time.ZoneOffset.UTC);
                urlStr = String.format("https://query1.finance.yahoo.com/v8/finance/chart/%s?interval=1d&period1=%d&period2=%d", 
                                     yahooSymbol, timestamp, timestamp + 86400);
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
                urlStr = String.format("https://query1.finance.yahoo.com/v8/finance/chart/%s?interval=1d&period1=%d&period2=%d", 
                                     symbol, timestamp, timestamp + 86400);
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
     * Busca cotação de criptomoedas usando CoinGecko API
     */
    private QuoteResult fetchCryptoQuote(String symbol, LocalDate date) {
        try {
            // CoinGecko API (gratuita)
            // Mapeamento de símbolos e nomes para IDs do CoinGecko
            Map<String, String> coinGeckoIds = new HashMap<>();
            // Símbolos
            coinGeckoIds.put("BTC", "bitcoin");
            coinGeckoIds.put("ETH", "ethereum");
            coinGeckoIds.put("BNB", "binancecoin");
            coinGeckoIds.put("ADA", "cardano");
            coinGeckoIds.put("SOL", "solana");
            coinGeckoIds.put("XRP", "ripple");
            coinGeckoIds.put("DOGE", "dogecoin");
            coinGeckoIds.put("DOT", "polkadot");
            coinGeckoIds.put("MATIC", "matic-network");
            coinGeckoIds.put("LTC", "litecoin");
            // Nomes completos (normalizados para maiúsculas)
            coinGeckoIds.put("BITCOIN", "bitcoin");
            coinGeckoIds.put("ETHEREUM", "ethereum");
            coinGeckoIds.put("BINANCECOIN", "binancecoin");
            coinGeckoIds.put("BINANCE COIN", "binancecoin");
            coinGeckoIds.put("CARDANO", "cardano");
            coinGeckoIds.put("SOLANA", "solana");
            coinGeckoIds.put("RIPPLE", "ripple");
            coinGeckoIds.put("DOGECOIN", "dogecoin");
            coinGeckoIds.put("POLKADOT", "polkadot");
            coinGeckoIds.put("POLYGON", "matic-network");
            coinGeckoIds.put("LITECOIN", "litecoin");
            
            // Normaliza o símbolo para busca (remove espaços extras, converte para maiúsculas)
            String normalizedSymbol = symbol.trim().replaceAll("\\s+", " ").toUpperCase();
            String coinId = coinGeckoIds.get(normalizedSymbol);
            
            // Se não encontrou no mapeamento, usa o símbolo em minúsculas diretamente
            // CoinGecko aceita tanto IDs quanto nomes em minúsculas (ex: "bitcoin", "ethereum")
            if (coinId == null) {
                coinId = symbol.trim().toLowerCase().replaceAll("\\s+", "-");
            }
            String urlStr;
            
            if (date != null && !date.equals(LocalDate.now())) {
                urlStr = String.format("https://api.coingecko.com/api/v3/coins/%s/history?date=%s", 
                                     coinId, date.format(DateTimeFormatter.ofPattern("dd-MM-yyyy")));
            } else {
                urlStr = String.format("https://api.coingecko.com/api/v3/simple/price?ids=%s&vs_currencies=usd", coinId);
            }
            
            String response = httpGet(urlStr);
            
            if (response == null || response.length() < 10) {
                System.err.println("Resposta vazia ou inválida do CoinGecko para " + symbol);
                return new QuoteResult(false, "Investimento não encontrado na base de dados. Por favor, insira o preço manualmente.", 0.0, "USD");
            }
            
            // Verifica se a resposta contém erro
            if (response.contains("\"error\"") || response.contains("Not Found")) {
                System.err.println("Erro na resposta CoinGecko para " + symbol + ": " + response.substring(0, Math.min(200, response.length())));
                return new QuoteResult(false, "Investimento não encontrado na base de dados. Por favor, insira o preço manualmente.", 0.0, "USD");
            }
            
            double price = parseCryptoPrice(response, coinId);
            String assetName = parseAssetNameFromCryptoResponse(response, symbol);
            
            if (price > 0) {
                return new QuoteResult(true, "Cotação obtida com sucesso", price, "USD", assetName);
            } else {
                // Se não conseguiu parsear o preço, verifica se há erro na resposta
                if (response.contains("\"error\"") || response.contains("Not Found")) {
                    return new QuoteResult(false, "Investimento não encontrado na base de dados. Por favor, insira o preço manualmente.", 0.0, "USD");
                }
                // Se não há erro explícito mas não conseguiu parsear, retorna erro
                System.err.println("Preço não encontrado na resposta CoinGecko: " + response.substring(0, Math.min(200, response.length())));
                return new QuoteResult(false, "Não foi possível obter a cotação do investimento. Por favor, insira o preço manualmente.", 0.0, "USD");
            }
        } catch (Exception e) {
            System.err.println("Erro ao buscar cotação crypto: " + e.getMessage());
            e.printStackTrace();
            return new QuoteResult(false, "Erro ao buscar cotação: " + e.getMessage() + ". Por favor, insira o preço manualmente.", 0.0, "USD");
        }
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
     * Extrai nome do ativo de resposta CoinGecko
     */
    private String parseAssetNameFromCryptoResponse(String response, String symbol) {
        try {
            // CoinGecko retorna o nome em "name" dentro do objeto do coin
            // Para API simples, não retorna nome, então usa o mapeamento
            // Para API de histórico, retorna em "name"
            int nameIdx = response.indexOf("\"name\"");
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
        } catch (Exception e) {
            System.err.println("Erro ao extrair nome crypto: " + e.getMessage());
        }
        return getDefaultAssetName(symbol);
    }
    
    /**
     * Faz requisição HTTP GET
     */
    private String httpGet(String urlStr) {
        try {
            URL url = new URL(urlStr);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(10000); // Aumentado para 10 segundos
            conn.setReadTimeout(10000);
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36");
            conn.setRequestProperty("Accept", "application/json");
            conn.setInstanceFollowRedirects(true);
            
            int responseCode = conn.getResponseCode();
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
                
                // Log para debug
                if (responseCode != 200) {
                    System.err.println("Erro HTTP " + responseCode + " para URL: " + urlStr);
                    System.err.println("Mensagem de erro: " + result);
                } else if (result.length() > 500) {
                    System.out.println("Resposta API (primeiros 500 chars): " + result.substring(0, 500));
                } else {
                    System.out.println("Resposta API: " + result);
                }
                return result;
            } else {
                System.err.println("Erro HTTP " + responseCode + " para URL: " + urlStr + " - Não foi possível ler a resposta");
            }
        } catch (java.net.SocketTimeoutException e) {
            System.err.println("Timeout na requisição HTTP para: " + urlStr);
        } catch (Exception e) {
            System.err.println("Erro na requisição HTTP para " + urlStr + ": " + e.getMessage());
            e.printStackTrace();
        }
        return null;
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
            // Se for busca histórica, tenta primeiro buscar nos arrays (chart indicators)
            // pois os campos de metadata (regularMarketPrice) trazem o valor atual
            if (isHistorical) {
                double priceFromArray = parsePriceFromYahooArray(response);
                if (priceFromArray > 0) return priceFromArray;
            }

            // Tenta vários campos possíveis (em ordem de prioridade)
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
     * Parse preço de resposta CoinGecko
     */
    private double parseCryptoPrice(String response, String coinId) {
        try {
            // Para API simples: {"bitcoin":{"usd":50000}}
            int coinIdx = response.indexOf("\"" + coinId + "\"");
            if (coinIdx > 0) {
                int usdIdx = response.indexOf("\"usd\"", coinIdx);
                if (usdIdx > 0) {
                    int valueStart = response.indexOf(":", usdIdx) + 1;
                    // Pula espaços
                    while (valueStart < response.length() && Character.isWhitespace(response.charAt(valueStart))) {
                        valueStart++;
                    }
                    int valueEnd = valueStart;
                    // Encontra o fim do número
                    while (valueEnd < response.length() && 
                           (Character.isDigit(response.charAt(valueEnd)) || 
                            response.charAt(valueEnd) == '.' || 
                            response.charAt(valueEnd) == 'e' ||
                            response.charAt(valueEnd) == 'E' ||
                            response.charAt(valueEnd) == '-' ||
                            response.charAt(valueEnd) == '+')) {
                        valueEnd++;
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
            
            // Para API de histórico: {"market_data":{"current_price":{"usd":50000}}}
            int marketDataIdx = response.indexOf("\"market_data\"");
            if (marketDataIdx > 0) {
                int currentPriceIdx = response.indexOf("\"current_price\"", marketDataIdx);
                if (currentPriceIdx > 0) {
                    int usdIdx = response.indexOf("\"usd\"", currentPriceIdx);
                    if (usdIdx > 0) {
                        int valueStart = response.indexOf(":", usdIdx) + 1;
                        int valueEnd = response.indexOf(",", valueStart);
                        if (valueEnd == -1) valueEnd = response.indexOf("}", valueStart);
                        String priceStr = response.substring(valueStart, valueEnd).trim();
                        if (!priceStr.isEmpty()) {
                            return Double.parseDouble(priceStr);
                        }
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("Erro ao fazer parse do preço CoinGecko: " + e.getMessage());
        }
        return 0.0;
    }
    
    /**
     * Busca taxa de câmbio USD para BRL
     */
    public double getExchangeRate(String from, String to) {
        if (from.equals(to)) return 1.0;
        
        try {
            if (from.equals("USD") && to.equals("BRL")) {
                String response = httpGet(EXCHANGE_RATE_API);
                if (response != null) {
                    int brlIdx = response.indexOf("\"BRL\"");
                    if (brlIdx > 0) {
                        int valueStart = response.indexOf(":", brlIdx) + 1;
                        int valueEnd = response.indexOf(",", valueStart);
                        if (valueEnd == -1) valueEnd = response.indexOf("}", valueStart);
                        String rateStr = response.substring(valueStart, valueEnd).trim();
                        return Double.parseDouble(rateStr);
                    }
                }
            }
        } catch (Exception e) {
            // Fallback para taxa fixa
        }
        
        // Fallback: taxa aproximada USD/BRL
        return 6.0;
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
                                           String tipoRentabilidade, String indice, Double taxaFixa, 
                                           LocalDate dataAporte, LocalDate dataVencimento) {
        if (dataVencimento == null || dataAporte == null) {
            return valorAporte; // Se não tem vencimento, retorna o valor original
        }
        
        LocalDate hoje = LocalDate.now();
        LocalDate dataFinal = hoje.isBefore(dataVencimento) ? hoje : dataVencimento;
        
        long diasTotais = java.time.temporal.ChronoUnit.DAYS.between(dataAporte, dataVencimento);
        long diasDecorridos = java.time.temporal.ChronoUnit.DAYS.between(dataAporte, dataFinal);
        
        if (diasTotais <= 0 || diasDecorridos < 0) {
            return valorAporte;
        }
        
        double taxaAnual = 0.0;
        
        if ("PRE_FIXADO".equals(tipoRentabilidade)) {
            // Pré-fixado: usa a taxa fixa fornecida
            taxaAnual = taxaFixa != null ? taxaFixa : 0.0;
        } else if ("POS_FIXADO".equals(tipoRentabilidade) || "POS_FIXADO_TAXA".equals(tipoRentabilidade)) {
            // Pós-fixado: busca taxa do índice (real)
            taxaAnual = getIndexRate(indice);
            
            // Se for pós-fixado + taxa fixa, soma a taxa fixa
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
        
        CachedQuote(QuoteResult quote, long timestamp) {
            this.quote = quote;
            this.timestamp = timestamp;
        }
        
        boolean isExpired() {
            return (System.currentTimeMillis() - timestamp) > CACHE_DURATION_MS;
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
