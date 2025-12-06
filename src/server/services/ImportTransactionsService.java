package server.services;

import server.model.*;
import server.repository.*;
import server.utils.NumberUtil;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Serviço para importação de transações via CSV
 */
public class ImportTransactionsService {
    private final CategoryRepository categoryRepository;
    private final AccountRepository accountRepository;
    private final TagRepository tagRepository;
    private final InstallmentService installmentService;
    
    // Cores padrão para tags criadas automaticamente
    private static final String[] DEFAULT_TAG_COLORS = {
        "#3B82F6", "#10B981", "#F59E0B", "#EF4444", "#8B5CF6",
        "#EC4899", "#06B6D4", "#84CC16", "#F97316", "#6366F1"
    };
    
    public ImportTransactionsService() {
        this.categoryRepository = new CategoryRepository();
        this.accountRepository = new AccountRepository();
        this.tagRepository = new TagRepository();
        this.installmentService = new InstallmentService();
    }
    
    /**
     * Processa CSV e retorna lista de transações validadas
     */
    public ImportResult processCSV(String csvContent, int userId) {
        ImportResult result = new ImportResult();
        List<Map<String, Object>> transactions = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        
        try {
            // Normaliza o conteúdo antes de processar
            if (csvContent == null) {
                errors.add("CSV vazio: nenhum conteúdo fornecido");
                result.setErrors(errors);
                return result;
            }
            
            // Remove BOM se presente
            if (csvContent.startsWith("\uFEFF")) {
                csvContent = csvContent.substring(1);
            }
            
            // Normaliza quebras de linha
            csvContent = csvContent.replace("\r\n", "\n").replace("\r", "\n");
            
            // Parse do CSV
            List<Map<String, String>> rows = parseCSV(csvContent);
            
            if (rows.isEmpty()) {
                // Verifica se o conteúdo tem pelo menos o cabeçalho
                if (csvContent.trim().isEmpty()) {
                    errors.add("CSV vazio: nenhum conteúdo fornecido");
                } else {
                    String[] lines = csvContent.split("\n");
                    int nonEmptyLines = 0;
                    for (String line : lines) {
                        if (line != null && !line.trim().isEmpty()) {
                            nonEmptyLines++;
                        }
                    }
                    if (nonEmptyLines < 1) {
                        errors.add("CSV vazio: nenhuma linha encontrada");
                    } else if (nonEmptyLines < 2) {
                        errors.add("CSV inválido: deve ter pelo menos uma linha de cabeçalho e uma linha de dados. Encontradas " + nonEmptyLines + " linha(s)");
                    } else {
                        errors.add("CSV inválido: nenhuma linha de dados válida encontrada após o cabeçalho. Total de linhas: " + lines.length);
                    }
                }
                result.setErrors(errors);
                return result;
            }
            
            // Cache para evitar buscas repetidas
            Map<String, Integer> categoryCache = new HashMap<>();
            Map<String, Integer> accountCache = new HashMap<>();
            Map<String, Integer> tagCache = new HashMap<>();
            
            // Carrega categorias, contas e tags existentes
            loadExistingEntities(userId, categoryCache, accountCache, tagCache);
            
            int lineNumber = 2; // Linha 1 é o cabeçalho
            for (Map<String, String> row : rows) {
                try {
                    Map<String, Object> transaction = processRow(row, userId, lineNumber, 
                        categoryCache, accountCache, tagCache, errors);
                    if (transaction != null) {
                        transactions.add(transaction);
                    }
                } catch (Exception e) {
                    errors.add(String.format("Linha %d: %s", lineNumber, e.getMessage()));
                }
                lineNumber++;
            }
            
            result.setTransactions(transactions);
            result.setErrors(errors);
            result.setSuccess(errors.isEmpty());
            
        } catch (Exception e) {
            errors.add("Erro ao processar CSV: " + e.getMessage());
            result.setErrors(errors);
            result.setSuccess(false);
        }
        
        return result;
    }
    
    /**
     * Parse simples de CSV (suporta valores entre aspas)
     */
    private List<Map<String, String>> parseCSV(String csvContent) {
        List<Map<String, String>> rows = new ArrayList<>();
        if (csvContent == null || csvContent.trim().isEmpty()) {
            return rows;
        }
        
        // Já normalizado no método processCSV, mas garante novamente
        csvContent = csvContent.replace("\r\n", "\n").replace("\r", "\n");
        
        String[] lines = csvContent.split("\n", -1); // -1 para manter linhas vazias no final
        
        // Remove linhas vazias do final
        int lastNonEmpty = lines.length - 1;
        while (lastNonEmpty >= 0 && (lines[lastNonEmpty] == null || lines[lastNonEmpty].trim().isEmpty())) {
            lastNonEmpty--;
        }
        
        if (lastNonEmpty < 0) {
            return rows; // CSV vazio
        }
        
        // Parse do cabeçalho
        String headerLine = lines[0];
        if (headerLine == null || headerLine.trim().isEmpty()) {
            return rows;
        }
        
        String[] headers = parseCSVLine(headerLine);
        if (headers.length == 0) {
            return rows;
        }
        
        // Normaliza nomes das colunas (remove espaços, converte para minúsculas)
        String[] normalizedHeaders = new String[headers.length];
        for (int i = 0; i < headers.length; i++) {
            normalizedHeaders[i] = headers[i].trim().toLowerCase()
                .replace(" ", "_")
                .replace("ç", "c")
                .replace("ã", "a")
                .replace("õ", "o");
        }
        
        // Parse das linhas de dados
        for (int i = 1; i <= lastNonEmpty; i++) {
            String line = lines[i];
            if (line == null || line.trim().isEmpty()) continue;
            
            String[] values = parseCSVLine(line);
            if (values.length == 0) continue;
            
            Map<String, String> row = new HashMap<>();
            for (int j = 0; j < normalizedHeaders.length && j < values.length; j++) {
                row.put(normalizedHeaders[j], values[j] != null ? values[j].trim() : "");
            }
            rows.add(row);
        }
        
        return rows;
    }
    
    /**
     * Parse de uma linha CSV, suportando valores entre aspas
     */
    private String[] parseCSVLine(String line) {
        List<String> fields = new ArrayList<>();
        boolean inQuotes = false;
        StringBuilder currentField = new StringBuilder();
        
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            
            if (c == '"') {
                if (inQuotes && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    // Aspas duplas escapadas
                    currentField.append('"');
                    i++;
                } else {
                    // Toggle quotes
                    inQuotes = !inQuotes;
                }
            } else if (c == ',' && !inQuotes) {
                // Fim do campo
                fields.add(currentField.toString());
                currentField = new StringBuilder();
            } else {
                currentField.append(c);
            }
        }
        
        // Adiciona o último campo
        fields.add(currentField.toString());
        
        return fields.toArray(new String[0]);
    }
    
    /**
     * Processa uma linha do CSV
     */
    private Map<String, Object> processRow(Map<String, String> row, int userId, int lineNumber,
                                          Map<String, Integer> categoryCache,
                                          Map<String, Integer> accountCache,
                                          Map<String, Integer> tagCache,
                                          List<String> errors) {
        Map<String, Object> transaction = new HashMap<>();
        
        // Validação de campos obrigatórios
        String tipo = getValue(row, "tipo");
        if (tipo == null || tipo.isEmpty()) {
            throw new IllegalArgumentException("Campo 'tipo' é obrigatório");
        }
        
        tipo = tipo.trim().toLowerCase();
        if (!tipo.equals("gasto") && !tipo.equals("receita")) {
            throw new IllegalArgumentException("Tipo deve ser 'gasto' ou 'receita'");
        }
        transaction.put("type", tipo);
        
        String descricao = getValue(row, "descricao");
        if (descricao == null || descricao.isEmpty()) {
            throw new IllegalArgumentException("Campo 'descricao' é obrigatório");
        }
        transaction.put("description", descricao);
        
        // Valor
        String valorStr = getValue(row, "valor");
        if (valorStr == null || valorStr.isEmpty()) {
            throw new IllegalArgumentException("Campo 'valor' é obrigatório");
        }
        double valor;
        try {
            valor = NumberUtil.parseDoubleBrazilian(valorStr);
            if (valor <= 0) {
                throw new IllegalArgumentException("Valor deve ser maior que zero");
            }
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Valor inválido: " + valorStr);
        }
        transaction.put("value", valor);
        
        // Data
        String dataStr = getValue(row, "data");
        if (dataStr == null || dataStr.isEmpty()) {
            throw new IllegalArgumentException("Campo 'data' é obrigatório");
        }
        LocalDate data;
        try {
            data = LocalDate.parse(dataStr);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("Data inválida. Use formato YYYY-MM-DD: " + dataStr);
        }
        transaction.put("date", data.toString());
        
        // Conta
        String contaNome = getValue(row, "conta");
        if (contaNome == null || contaNome.isEmpty()) {
            throw new IllegalArgumentException("Campo 'conta' é obrigatório");
        }
        String tipoConta = getValue(row, "tipo_conta");
        int accountId = getOrCreateAccount(contaNome, tipoConta, userId, accountCache);
        transaction.put("accountId", accountId);
        
        // Categorias (apenas para gastos)
        if (tipo.equals("gasto")) {
            String categoriasStr = getValue(row, "categoria");
            List<Integer> categoryIds = new ArrayList<>();
            if (categoriasStr != null && !categoriasStr.isEmpty()) {
                String[] categorias = categoriasStr.split(",");
                Set<String> categoriasUnicas = new HashSet<>();
                for (String cat : categorias) {
                    String catTrim = cat.trim();
                    if (!catTrim.isEmpty() && !categoriasUnicas.contains(catTrim.toLowerCase())) {
                        categoriasUnicas.add(catTrim.toLowerCase());
                        int catId = getOrCreateCategory(catTrim, userId, categoryCache);
                        categoryIds.add(catId);
                    }
                }
            }
            transaction.put("categoryIds", categoryIds);
        }
        
        // Tags
        String tagsStr = getValue(row, "tags");
        List<Integer> tagIds = new ArrayList<>();
        if (tagsStr != null && !tagsStr.isEmpty()) {
            String[] tags = tagsStr.split(",");
            Set<String> tagsUnicas = new HashSet<>();
            for (String tag : tags) {
                String tagTrim = tag.trim();
                if (!tagTrim.isEmpty() && !tagsUnicas.contains(tagTrim.toLowerCase())) {
                    tagsUnicas.add(tagTrim.toLowerCase());
                    int tagId = getOrCreateTag(tagTrim, userId, tagCache);
                    tagIds.add(tagId);
                }
            }
        }
        transaction.put("tagIds", tagIds);
        
        // Observações
        String observacoesStr = getValue(row, "observacoes");
        List<String> obsList = new ArrayList<>();
        if (observacoesStr != null && !observacoesStr.isEmpty()) {
            String[] observacoes = observacoesStr.split(";");
            for (String obs : observacoes) {
                String obsTrim = obs.trim();
                if (!obsTrim.isEmpty()) {
                    obsList.add(obsTrim);
                }
            }
        }
        // Sempre adiciona como List (mesmo que vazia) para serialização JSON correta
        transaction.put("observacoes", obsList);
        
        // Compras parceladas (apenas para gastos)
        if (tipo.equals("gasto")) {
            String numParcelasStr = getValue(row, "numero_parcelas");
            if (numParcelasStr != null && !numParcelasStr.isEmpty()) {
                try {
                    int numParcelas = Integer.parseInt(numParcelasStr.trim());
                    if (numParcelas > 1) {
                        transaction.put("isParcelado", true);
                        transaction.put("numeroParcelas", numParcelas);
                        
                        String intervaloStr = getValue(row, "intervalo_dias");
                        int intervaloDias = 30; // padrão
                        if (intervaloStr != null && !intervaloStr.isEmpty()) {
                            try {
                                intervaloDias = Integer.parseInt(intervaloStr.trim());
                                if (intervaloDias < 1) intervaloDias = 30;
                            } catch (NumberFormatException e) {
                                // usa padrão
                            }
                        }
                        transaction.put("intervaloDias", intervaloDias);
                    }
                } catch (NumberFormatException e) {
                    // Ignora se não for número válido
                }
            }
            
            // Data de entrada na fatura (compras retidas)
            String dataEntradaFaturaStr = getValue(row, "data_entrada_fatura");
            if (dataEntradaFaturaStr != null && !dataEntradaFaturaStr.isEmpty()) {
                try {
                    LocalDate dataEntradaFatura = LocalDate.parse(dataEntradaFaturaStr);
                    if (dataEntradaFatura.isBefore(data)) {
                        throw new IllegalArgumentException("Data de entrada na fatura deve ser posterior à data da compra");
                    }
                    transaction.put("dataEntradaFatura", dataEntradaFatura.toString());
                    transaction.put("compraRetida", true);
                } catch (DateTimeParseException e) {
                    throw new IllegalArgumentException("Data de entrada na fatura inválida: " + dataEntradaFaturaStr);
                }
            }
        }
        
        // Frequência (recorrência)
        String frequenciaStr = getValue(row, "frequencia");
        if (frequenciaStr != null && !frequenciaStr.isEmpty()) {
            frequenciaStr = frequenciaStr.trim().toUpperCase();
            if (frequenciaStr.equals("SEMANAL") || frequenciaStr.equals("MENSAL") || 
                frequenciaStr.equals("ANUAL") || frequenciaStr.equals("UNICA")) {
                transaction.put("frequency", frequenciaStr);
            }
        } else {
            transaction.put("frequency", "UNICA");
        }
        
        // Pagamento de fatura (apenas para receitas em cartão)
        if (tipo.equals("receita")) {
            String pagamentoFaturaStr = getValue(row, "pagamento_fatura");
            boolean pagamentoFatura = parseBoolean(pagamentoFaturaStr);
            
            // Verifica se a conta é cartão de crédito
            Conta conta = accountRepository.buscarConta(accountId);
            if (conta != null && conta.isCartaoCredito()) {
                transaction.put("pagamentoFatura", pagamentoFatura);
                
                // Conta origem
                String contaOrigemNome = getValue(row, "conta_origem");
                if (contaOrigemNome != null && !contaOrigemNome.isEmpty()) {
                    int contaOrigemId = getOrCreateAccount(contaOrigemNome, null, userId, accountCache);
                    transaction.put("contaOrigemId", contaOrigemId);
                }
            }
        }
        
        transaction.put("userId", userId);
        transaction.put("lineNumber", lineNumber);
        
        return transaction;
    }
    
    /**
     * Obtém ou cria uma categoria
     */
    private int getOrCreateCategory(String nome, int userId, Map<String, Integer> cache) {
        String key = nome.toLowerCase().trim();
        if (cache.containsKey(key)) {
            return cache.get(key);
        }
        
        // Busca no banco
        List<Categoria> categorias = categoryRepository.buscarCategoriasPorUsuario(userId);
        for (Categoria cat : categorias) {
            if (cat.getNome().equalsIgnoreCase(nome)) {
                cache.put(key, cat.getIdCategoria());
                return cat.getIdCategoria();
            }
        }
        
        // Cria nova categoria
        int id = categoryRepository.cadastrarCategoria(nome, userId);
        cache.put(key, id);
        return id;
    }
    
    /**
     * Obtém ou cria uma conta
     */
    private int getOrCreateAccount(String nome, String tipoConta, int userId, Map<String, Integer> cache) {
        // Chave do cache: nome + tipo (se fornecido)
        String cacheKey = tipoConta != null && !tipoConta.isEmpty() 
            ? (nome.toLowerCase().trim() + "::" + tipoConta.toLowerCase().trim())
            : nome.toLowerCase().trim();
        
        if (cache.containsKey(cacheKey)) {
            return cache.get(cacheKey);
        }
        
        // Busca no banco
        List<Conta> contas = accountRepository.buscarContasPorUsuario(userId);
        for (Conta conta : contas) {
            boolean nomeMatch = conta.getNome().equalsIgnoreCase(nome);
            boolean tipoMatch = tipoConta == null || tipoConta.isEmpty() || 
                              conta.getTipo().equalsIgnoreCase(tipoConta);
            
            if (nomeMatch && tipoMatch) {
                cache.put(cacheKey, conta.getIdConta());
                return conta.getIdConta();
            }
        }
        
        // Se tipo não fornecido, tenta inferir ou usar padrão
        String tipoFinal = tipoConta != null && !tipoConta.isEmpty() 
            ? tipoConta 
            : "CORRENTE";
        
        // Cria nova conta
        int id = accountRepository.cadastrarConta(nome, tipoFinal, 0.0, userId);
        cache.put(cacheKey, id);
        return id;
    }
    
    /**
     * Obtém ou cria uma tag
     */
    private int getOrCreateTag(String nome, int userId, Map<String, Integer> cache) {
        String key = nome.toLowerCase().trim();
        if (cache.containsKey(key)) {
            return cache.get(key);
        }
        
        // Busca no banco
        List<Tag> tags = tagRepository.buscarTagsPorUsuario(userId);
        for (Tag tag : tags) {
            if (tag.getNome().equalsIgnoreCase(nome)) {
                cache.put(key, tag.getIdTag());
                return tag.getIdTag();
            }
        }
        
        // Cria nova tag com cor aleatória
        String cor = DEFAULT_TAG_COLORS[cache.size() % DEFAULT_TAG_COLORS.length];
        int id = tagRepository.cadastrarTag(nome, cor, userId);
        cache.put(key, id);
        return id;
    }
    
    /**
     * Carrega entidades existentes no cache
     */
    private void loadExistingEntities(int userId, 
                                      Map<String, Integer> categoryCache,
                                      Map<String, Integer> accountCache,
                                      Map<String, Integer> tagCache) {
        // Categorias
        List<Categoria> categorias = categoryRepository.buscarCategoriasPorUsuario(userId);
        for (Categoria cat : categorias) {
            categoryCache.put(cat.getNome().toLowerCase().trim(), cat.getIdCategoria());
        }
        
        // Contas
        List<Conta> contas = accountRepository.buscarContasPorUsuario(userId);
        for (Conta conta : contas) {
            String key = conta.getNome().toLowerCase().trim() + "::" + conta.getTipo().toLowerCase().trim();
            accountCache.put(key, conta.getIdConta());
            // Também adiciona sem tipo para compatibilidade
            accountCache.put(conta.getNome().toLowerCase().trim(), conta.getIdConta());
        }
        
        // Tags
        List<Tag> tags = tagRepository.buscarTagsPorUsuario(userId);
        for (Tag tag : tags) {
            tagCache.put(tag.getNome().toLowerCase().trim(), tag.getIdTag());
        }
    }
    
    private String getValue(Map<String, String> row, String key) {
        return row.get(key);
    }
    
    /**
     * Converte string para boolean, aceitando valores amigáveis
     * true: "true", "1", "sim", "verdadeiro", "yes", "s", "y"
     * false: "false", "0", "não", "nao", "negativo", "no", "n"
     */
    private boolean parseBoolean(String value) {
        if (value == null || value.trim().isEmpty()) {
            return false;
        }
        String normalized = value.trim().toLowerCase();
        return normalized.equals("true") || 
               normalized.equals("1") || 
               normalized.equals("sim") || 
               normalized.equals("verdadeiro") ||
               normalized.equals("yes") ||
               normalized.equals("s") ||
               normalized.equals("y");
    }
    
    /**
     * Classe para resultado da importação
     */
    public static class ImportResult {
        private List<Map<String, Object>> transactions;
        private List<String> errors;
        private boolean success;
        
        public ImportResult() {
            this.transactions = new ArrayList<>();
            this.errors = new ArrayList<>();
            this.success = false;
        }
        
        public List<Map<String, Object>> getTransactions() { return transactions; }
        public void setTransactions(List<Map<String, Object>> transactions) { this.transactions = transactions; }
        
        public List<String> getErrors() { return errors; }
        public void setErrors(List<String> errors) { this.errors = errors; }
        
        public boolean isSuccess() { return success; }
        public void setSuccess(boolean success) { this.success = success; }
    }
}

