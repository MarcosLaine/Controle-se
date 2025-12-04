package server.repository;

import server.database.DatabaseConnection;
import server.model.Investimento;
import server.validation.InputValidator;
import server.validation.ValidationResult;
import java.sql.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class InvestmentRepository {

    private Connection getConnection() throws SQLException {
        return DatabaseConnection.getInstance().getConnection();
    }

    private void validateInput(String field, String value, int maxLength) {
        ValidationResult res = InputValidator.validateString(field, value, maxLength, true);
        if (!res.isValid()) throw new IllegalArgumentException(res.getErrors().get(0));
    }

    private void validateId(String field, int id) {
        ValidationResult res = InputValidator.validateId(field, id, true);
        if (!res.isValid()) throw new IllegalArgumentException(res.getErrors().get(0));
    }

    public int cadastrarInvestimento(String nome, String nomeAtivo, String categoria, double quantidade, 
                                     double precoAporte, double corretagem, String corretora, 
                                     LocalDate dataAporte, int idUsuario, int idConta, String moeda) {
        
        validateInput("Nome do investimento", nome, 50);
        validateInput("Categoria", categoria, 50);
        validateId("ID do usuário", idUsuario);
        validateId("ID da conta", idConta);
        
        // Permite quantidades negativas para vendas, mas não permite zero
        if (quantidade == 0 || Double.isNaN(quantidade) || Double.isInfinite(quantidade)) {
            throw new IllegalArgumentException("A quantidade deve ser diferente de zero");
        }
        if (precoAporte < 0) throw new IllegalArgumentException("Preço de aporte não pode ser negativo");
        if (corretagem < 0) throw new IllegalArgumentException("Corretagem não pode ser negativa");
        if (dataAporte == null) throw new IllegalArgumentException("Data de aporte é obrigatória");
        
        Connection conn = null;
        try {
            conn = getConnection();
            conn.setAutoCommit(false);
            
            String sql = "INSERT INTO investimentos (nome, nome_ativo, categoria, quantidade, preco_aporte, corretagem, corretora, data_aporte, id_usuario, id_conta, moeda) " +
                        "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) RETURNING id_investimento";
            
            int idInvestimento;
            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setString(1, nome);
                pstmt.setString(2, nomeAtivo);
                pstmt.setString(3, categoria.toUpperCase());
                pstmt.setDouble(4, quantidade);
                pstmt.setDouble(5, precoAporte);
                pstmt.setDouble(6, corretagem);
                pstmt.setString(7, corretora);
                pstmt.setDate(8, java.sql.Date.valueOf(dataAporte));
                pstmt.setInt(9, idUsuario);
                pstmt.setInt(10, idConta);
                pstmt.setString(11, moeda != null ? moeda : "BRL");
                
                ResultSet rs = pstmt.executeQuery();
                if (!rs.next()) throw new RuntimeException("Erro ao cadastrar investimento");
                idInvestimento = rs.getInt(1);
            }
            
            // Não debita mais da conta - o saldo será calculado dinamicamente baseado no valor atual dos investimentos
            conn.commit();
            return idInvestimento;
            
        } catch (SQLException e) {
            if (conn != null) try { conn.rollback(); } catch (SQLException ex) {}
            throw new RuntimeException("Erro ao cadastrar investimento: " + e.getMessage(), e);
        } finally {
            if (conn != null) try { conn.close(); } catch (SQLException e) {}
        }
    }

    public int cadastrarInvestimento(String nome, String nomeAtivo, String categoria, double quantidade, 
                                    double precoAporte, double corretagem, String corretora,
                                    LocalDate dataAporte, int idUsuario, int idConta, String moeda,
                                    String tipoInvestimento, String tipoRentabilidade, String indice, 
                                    Double percentualIndice, Double taxaFixa, LocalDate dataVencimento) {
        validateInput("Nome do investimento", nome, 50);
        validateId("ID do usuário", idUsuario);
        validateId("ID da conta", idConta);
        
        // Permite quantidades negativas para vendas, mas não permite zero
        if (quantidade == 0 || Double.isNaN(quantidade) || Double.isInfinite(quantidade)) {
            throw new IllegalArgumentException("A quantidade deve ser diferente de zero");
        }
        if (precoAporte < 0) throw new IllegalArgumentException("Preço de aporte não pode ser negativo");
        if (corretagem < 0) throw new IllegalArgumentException("Corretagem não pode ser negativa");
        if (dataAporte == null) throw new IllegalArgumentException("Data de aporte é obrigatória");
        
        Connection conn = null;
        try {
            conn = getConnection();
            conn.setAutoCommit(false);
            
            String sql = "INSERT INTO investimentos (nome, nome_ativo, categoria, quantidade, preco_aporte, " +
                        "corretagem, corretora, data_aporte, id_usuario, id_conta, moeda, tipo_investimento, " +
                        "tipo_rentabilidade, indice, percentual_indice, taxa_fixa, data_vencimento, ativo) " +
                        "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, TRUE) RETURNING id_investimento";
            
            int idInvestimento;
            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                System.out.println("[DEBUG] InvestmentRepository.cadastrarInvestimento: Criando investimento para userId=" + idUsuario);
                pstmt.setString(1, nome);
                pstmt.setString(2, nomeAtivo);
                pstmt.setString(3, categoria);
                pstmt.setDouble(4, quantidade);
                pstmt.setDouble(5, precoAporte);
                pstmt.setDouble(6, corretagem);
                pstmt.setString(7, corretora);
                pstmt.setDate(8, java.sql.Date.valueOf(dataAporte));
                pstmt.setInt(9, idUsuario);
                pstmt.setInt(10, idConta);
                pstmt.setString(11, moeda);
                pstmt.setString(12, tipoInvestimento);
                pstmt.setString(13, tipoRentabilidade);
                pstmt.setString(14, indice);
                pstmt.setObject(15, percentualIndice);
                pstmt.setObject(16, taxaFixa);
                pstmt.setObject(17, dataVencimento != null ? java.sql.Date.valueOf(dataVencimento) : null);
                
                ResultSet rs = pstmt.executeQuery();
                if (!rs.next()) throw new RuntimeException("Erro ao cadastrar investimento");
                idInvestimento = rs.getInt(1);
                System.out.println("[DEBUG] InvestmentRepository.cadastrarInvestimento: Investimento criado com id=" + idInvestimento + " para userId=" + idUsuario);
            }
            
            // Não debita mais da conta - o saldo será calculado dinamicamente baseado no valor atual dos investimentos
            conn.commit();
            return idInvestimento;
            
        } catch (SQLException e) {
            if (conn != null) try { conn.rollback(); } catch (SQLException ex) {}
            throw new RuntimeException("Erro ao cadastrar investimento: " + e.getMessage(), e);
        } finally {
            if (conn != null) try { conn.close(); } catch (SQLException e) {}
        }
    }

    // Overloads...
    public int cadastrarInvestimento(String nome, String categoria, double quantidade, 
                                     double precoAporte, double corretagem, String corretora, 
                                     LocalDate dataAporte, int idUsuario, int idConta, String moeda) {
        return cadastrarInvestimento(nome, null, categoria, quantidade, precoAporte, corretagem, corretora, dataAporte, idUsuario, idConta, moeda, null, null, null, null, null, null);
    }

    public Investimento buscarInvestimento(int idInvestimento) {
        String sql = "SELECT * FROM investimentos WHERE id_investimento = ? AND ativo = TRUE";
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, idInvestimento);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) return mapInvestimento(rs);
            return null;
        } catch (SQLException e) {
            throw new RuntimeException("Erro ao buscar investimento: " + e.getMessage(), e);
        }
    }

    public List<Investimento> buscarInvestimentosPorUsuario(int idUsuario) {
        return buscarInvestimentosPorUsuario(idUsuario, Integer.MAX_VALUE, 0, null, null);
    }
    
    public List<Investimento> buscarInvestimentosPorUsuario(int idUsuario, int limit, int offset, String category, String assetName) {
        if (idUsuario <= 0) {
            throw new IllegalArgumentException("ID do usuário deve ser maior que zero");
        }
        StringBuilder sql = new StringBuilder("SELECT * FROM investimentos WHERE id_usuario = ? AND ativo = TRUE");
        
        if (category != null && !category.isEmpty()) {
            sql.append(" AND categoria = ?");
        }
        if (assetName != null && !assetName.isEmpty()) {
            sql.append(" AND nome = ?");
        }
        
        sql.append(" ORDER BY data_aporte DESC LIMIT ? OFFSET ?");
        
        List<Investimento> investimentos = new ArrayList<>();
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql.toString())) {
            int paramIndex = 1;
            pstmt.setInt(paramIndex++, idUsuario);
            System.out.println("[DEBUG] InvestmentRepository.buscarInvestimentosPorUsuario: SQL=" + sql.toString());
            System.out.println("[DEBUG] InvestmentRepository.buscarInvestimentosPorUsuario: Parâmetro userId=" + idUsuario + " no índice " + (paramIndex - 1));
            if (category != null && !category.isEmpty()) {
                pstmt.setString(paramIndex++, category);
            }
            if (assetName != null && !assetName.isEmpty()) {
                pstmt.setString(paramIndex++, assetName);
            }
            pstmt.setInt(paramIndex++, limit);
            pstmt.setInt(paramIndex++, offset);
            
            ResultSet rs = pstmt.executeQuery();
            int count = 0;
            int skipped = 0;
            while (rs.next()) {
                // Validação ANTES de mapear: verifica o id_usuario diretamente do ResultSet
                int rsUserId = rs.getInt("id_usuario");
                if (rsUserId != idUsuario) {
                    System.err.println("[ERRO CRÍTICO] InvestmentRepository: Query retornou investimento " + 
                                     rs.getInt("id_investimento") + " com id_usuario=" + rsUserId + 
                                     " mas a query foi feita para userId=" + idUsuario);
                    System.err.println("[ERRO CRÍTICO] SQL executado: " + sql.toString());
                    System.err.println("[ERRO CRÍTICO] Parâmetros: userId=" + idUsuario);
                    skipped++;
                    continue; // Ignora investimentos que não pertencem ao usuário
                }
                
                Investimento inv = mapInvestimento(rs);
                // Validação adicional de segurança após mapeamento
                if (inv.getIdUsuario() != idUsuario) {
                    System.err.println("[ERRO] InvestmentRepository: Investimento " + inv.getIdInvestimento() + 
                                     " mapeado incorretamente - pertence ao usuário " + inv.getIdUsuario() + 
                                     " mas foi retornado para usuário " + idUsuario);
                    skipped++;
                    continue;
                }
                investimentos.add(inv);
                count++;
            }
            if (skipped > 0) {
                System.err.println("[ERRO] InvestmentRepository.buscarInvestimentosPorUsuario: " + skipped + 
                                 " investimentos foram ignorados por não pertencerem ao userId=" + idUsuario);
            }
            System.out.println("[DEBUG] InvestmentRepository.buscarInvestimentosPorUsuario: Encontrados " + count + 
                             " investimentos válidos para userId=" + idUsuario);
        } catch (SQLException e) {
            throw new RuntimeException("Erro ao buscar investimentos: " + e.getMessage(), e);
        }
        return investimentos;
    }

    public List<Investimento> buscarInvestimentosPorConta(int idConta) {
        String sql = "SELECT * FROM investimentos WHERE id_conta = ? AND ativo = TRUE ORDER BY data_aporte DESC";
        List<Investimento> investimentos = new ArrayList<>();
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, idConta);
            ResultSet rs = pstmt.executeQuery();
            while (rs.next()) investimentos.add(mapInvestimento(rs));
        } catch (SQLException e) {
            throw new RuntimeException("Erro ao buscar investimentos por conta: " + e.getMessage(), e);
        }
        return investimentos;
    }

    public void atualizarInvestimento(int idInvestimento, String nome, String nomeAtivo, String categoria, 
                                      double quantidade, double precoAporte, double corretagem, 
                                      String corretora, LocalDate dataAporte, String moeda, Integer accountId) {
        String sql = "UPDATE investimentos SET nome = ?, nome_ativo = ?, categoria = ?, quantidade = ?, preco_aporte = ?, corretagem = ?, corretora = ?, data_aporte = ?, moeda = ?";
        if (accountId != null && accountId > 0) {
            sql += ", id_conta = ?";
        }
        sql += " WHERE id_investimento = ?";
        
        try (Connection conn = getConnection()) {
            conn.setAutoCommit(false);
            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                int paramIndex = 1;
                pstmt.setString(paramIndex++, nome);
                pstmt.setString(paramIndex++, nomeAtivo);
                pstmt.setString(paramIndex++, categoria);
                pstmt.setDouble(paramIndex++, quantidade);
                pstmt.setDouble(paramIndex++, precoAporte);
                pstmt.setDouble(paramIndex++, corretagem);
                pstmt.setString(paramIndex++, corretora);
                pstmt.setDate(paramIndex++, java.sql.Date.valueOf(dataAporte));
                pstmt.setString(paramIndex++, moeda);
                if (accountId != null && accountId > 0) {
                    pstmt.setInt(paramIndex++, accountId);
                }
                pstmt.setInt(paramIndex++, idInvestimento);
                pstmt.executeUpdate();
                conn.commit();
            } catch (Exception e) {
                conn.rollback();
                throw e;
            }
        } catch (SQLException e) {
            throw new RuntimeException("Erro ao atualizar investimento: " + e.getMessage(), e);
        }
    }

    /**
     * Classe auxiliar para representar uma camada de posição (FIFO)
     */
    private static class PositionLayer {
        double qty;
        double unitCost;
        int idInvestimento; // ID da transação que criou esta camada
    }
    
    /**
     * Calcula quanto de um investimento específico ainda está na carteira
     * processando todas as transações do mesmo ativo em ordem cronológica (FIFO)
     */
    private double calcularValorRemanescenteNaCarteira(Investimento invParaExcluir, List<Investimento> todasTransacoes) {
        // Filtra transações do mesmo ativo (mesmo nome e categoria)
        List<Investimento> transacoesAtivo = new ArrayList<>();
        for (Investimento inv : todasTransacoes) {
            if (inv.getNome().equals(invParaExcluir.getNome()) && 
                inv.getCategoria().equals(invParaExcluir.getCategoria())) {
                transacoesAtivo.add(inv);
            }
        }
        
        // Ordena por data (mais antiga primeiro) para processar em ordem cronológica
        transacoesAtivo.sort((a, b) -> {
            int dateCompare = a.getDataAporte().compareTo(b.getDataAporte());
            if (dateCompare != 0) return dateCompare;
            // Se mesma data, compras antes de vendas
            if (a.getQuantidade() > 0 && b.getQuantidade() < 0) return -1;
            if (a.getQuantidade() < 0 && b.getQuantidade() > 0) return 1;
            return Integer.compare(a.getIdInvestimento(), b.getIdInvestimento());
        });
        
        // Simula FIFO para calcular quanto da transação excluída ainda está na carteira
        List<PositionLayer> buyLayers = new ArrayList<>();
        double qtyExcluir = invParaExcluir.getQuantidade();
        double valorAEstornar = 0.0;
        
        if (qtyExcluir > 0) {
            // É uma COMPRA: processa todas as transações simulando FIFO
            for (Investimento trans : transacoesAtivo) {
                double qty = trans.getQuantidade();
                
                if (trans.getIdInvestimento() == invParaExcluir.getIdInvestimento()) {
                    // Esta é a transação que será excluída - adiciona à lista de camadas
                    double unitCost = qty != 0 ? Math.abs(trans.getValorAporte()) / qty : 0.0;
                    PositionLayer layer = new PositionLayer();
                    layer.qty = qty;
                    layer.unitCost = unitCost;
                    layer.idInvestimento = trans.getIdInvestimento();
                    buyLayers.add(layer);
                } else if (qty > 0) {
                    // Outra compra: adiciona camada
                    double unitCost = qty != 0 ? Math.abs(trans.getValorAporte()) / qty : 0.0;
                    PositionLayer layer = new PositionLayer();
                    layer.qty = qty;
                    layer.unitCost = unitCost;
                    layer.idInvestimento = trans.getIdInvestimento();
                    buyLayers.add(layer);
                } else if (qty < 0) {
                    // Venda: remove das camadas usando FIFO
                    double remaining = Math.abs(qty);
                    java.util.Iterator<PositionLayer> iterator = buyLayers.iterator();
                    while (iterator.hasNext() && remaining > 0) {
                        PositionLayer layer = iterator.next();
                        double qtyToUse = Math.min(remaining, layer.qty);
                        layer.qty -= qtyToUse;
                        remaining -= qtyToUse;
                        if (layer.qty <= 0.000001) {
                            iterator.remove();
                        }
                    }
                }
            }
            
            // Calcula quanto da transação excluída ainda está na carteira
            for (PositionLayer layer : buyLayers) {
                if (layer.idInvestimento == invParaExcluir.getIdInvestimento()) {
                    valorAEstornar = layer.qty * layer.unitCost;
                    break;
                }
            }
        } else {
            // É uma VENDA: precisa reverter o crédito que foi feito na conta
            // O valor a estornar é negativo (subtrai da conta)
            valorAEstornar = -Math.abs(invParaExcluir.getValorAporte());
        }
        
        return valorAEstornar;
    }

    public void excluirInvestimento(int idInvestimento) {
        Investimento inv = buscarInvestimento(idInvestimento);
        if (inv == null) throw new IllegalArgumentException("Investimento não encontrado");
        
        Connection conn = null;
        try {
            conn = getConnection();
            conn.setAutoCommit(false);
            
            // Remove o investimento
            String sqlDelete = "DELETE FROM investimentos WHERE id_investimento = ?";
            try (PreparedStatement pstmt = conn.prepareStatement(sqlDelete)) {
                pstmt.setInt(1, idInvestimento);
                pstmt.executeUpdate();
            }
            
            // Não estorna mais valor na conta - o saldo será calculado dinamicamente baseado no valor atual dos investimentos
            conn.commit();
        } catch (SQLException e) {
            if (conn != null) try { conn.rollback(); } catch (SQLException ex) {}
            throw new RuntimeException("Erro ao excluir investimento: " + e.getMessage(), e);
        } finally {
            if (conn != null) try { conn.close(); } catch (SQLException e) {}
        }
    }

    private Investimento mapInvestimento(ResultSet rs) throws SQLException {
        Investimento inv = new Investimento(
            rs.getInt("id_investimento"),
            rs.getString("nome"),
            rs.getString("nome_ativo"),
            rs.getString("categoria"),
            rs.getDouble("quantidade"),
            rs.getDouble("preco_aporte"),
            rs.getDouble("corretagem"),
            rs.getString("corretora"),
            rs.getDate("data_aporte").toLocalDate(),
            rs.getInt("id_usuario"),
            rs.getInt("id_conta"),
            rs.getString("moeda")
        );
        
        try {
            inv.setTipoInvestimento(rs.getString("tipo_investimento"));
            inv.setTipoRentabilidade(rs.getString("tipo_rentabilidade"));
            inv.setIndice(rs.getString("indice"));
            
            Object pct = rs.getObject("percentual_indice");
            if (pct != null) inv.setPercentualIndice(((Number) pct).doubleValue());
            
            Object taxa = rs.getObject("taxa_fixa");
            if (taxa != null) inv.setTaxaFixa(((Number) taxa).doubleValue());
            
            java.sql.Date venc = rs.getDate("data_vencimento");
            if (venc != null) inv.setDataVencimento(venc.toLocalDate());
        } catch (SQLException e) {
            // Campos opcionais - ignora se não existirem
        }
        
        inv.setAtivo(rs.getBoolean("ativo"));
        return inv;
    }
}

