package server.model;

import java.io.Serializable;
import java.time.LocalDate;

public class InstallmentGroup implements Serializable {
    private static final long serialVersionUID = 1L;
    
    private int idGrupo;
    private String descricao;
    private double valorTotal;
    private int numeroParcelas;
    private double valorParcela;
    private LocalDate dataPrimeiraParcela;
    private int intervaloDias;
    private int idUsuario;
    private int idConta;
    private String tipoTransacao; // "GASTO" ou "RECEITA"
    private boolean ativo;
    
    public InstallmentGroup() {
        this.ativo = true;
        this.intervaloDias = 30; // Default mensal
    }
    
    public InstallmentGroup(String descricao, double valorTotal, int numeroParcelas, 
                           LocalDate dataPrimeiraParcela, int intervaloDias, 
                           int idUsuario, int idConta, String tipoTransacao) {
        this();
        this.descricao = descricao;
        this.valorTotal = valorTotal;
        this.numeroParcelas = numeroParcelas;
        // Arredonda para 2 casas decimais
        this.valorParcela = Math.round((valorTotal / numeroParcelas) * 100.0) / 100.0;
        this.dataPrimeiraParcela = dataPrimeiraParcela;
        this.intervaloDias = intervaloDias;
        this.idUsuario = idUsuario;
        this.idConta = idConta;
        this.tipoTransacao = tipoTransacao;
    }
    
    // Getters e Setters
    public int getIdGrupo() { return idGrupo; }
    public void setIdGrupo(int idGrupo) { this.idGrupo = idGrupo; }
    
    public String getDescricao() { return descricao; }
    public void setDescricao(String descricao) { this.descricao = descricao; }
    
    public double getValorTotal() { return valorTotal; }
    public void setValorTotal(double valorTotal) { 
        this.valorTotal = valorTotal;
        if (numeroParcelas > 0) {
            // Arredonda para 2 casas decimais
            this.valorParcela = Math.round((valorTotal / numeroParcelas) * 100.0) / 100.0;
        }
    }
    
    public int getNumeroParcelas() { return numeroParcelas; }
    public void setNumeroParcelas(int numeroParcelas) { 
        this.numeroParcelas = numeroParcelas;
        if (numeroParcelas > 0 && valorTotal > 0) {
            // Arredonda para 2 casas decimais
            this.valorParcela = Math.round((valorTotal / numeroParcelas) * 100.0) / 100.0;
        }
    }
    
    public double getValorParcela() { return valorParcela; }
    public void setValorParcela(double valorParcela) { this.valorParcela = valorParcela; }
    
    public LocalDate getDataPrimeiraParcela() { return dataPrimeiraParcela; }
    public void setDataPrimeiraParcela(LocalDate dataPrimeiraParcela) { 
        this.dataPrimeiraParcela = dataPrimeiraParcela; 
    }
    
    public int getIntervaloDias() { return intervaloDias; }
    public void setIntervaloDias(int intervaloDias) { this.intervaloDias = intervaloDias; }
    
    public int getIdUsuario() { return idUsuario; }
    public void setIdUsuario(int idUsuario) { this.idUsuario = idUsuario; }
    
    public int getIdConta() { return idConta; }
    public void setIdConta(int idConta) { this.idConta = idConta; }
    
    public String getTipoTransacao() { return tipoTransacao; }
    public void setTipoTransacao(String tipoTransacao) { this.tipoTransacao = tipoTransacao; }
    
    public boolean isAtivo() { return ativo; }
    public void setAtivo(boolean ativo) { this.ativo = ativo; }
    
    /**
     * Calcula a data de uma parcela específica
     * Se o intervalo for aproximadamente mensal (28-31 dias), usa lógica mensal inteligente
     * que considera meses com diferentes números de dias.
     * Caso contrário, usa intervalo fixo de dias.
     * 
     * @param numeroParcela Número da parcela (1, 2, 3, ...)
     * @return Data da parcela
     */
    public LocalDate calcularDataParcela(int numeroParcela) {
        if (numeroParcela < 1 || numeroParcela > numeroParcelas) {
            throw new IllegalArgumentException("Número de parcela inválido: " + numeroParcela);
        }
        
        // Parcela 1 sempre é a dataPrimeiraParcela
        if (numeroParcela == 1) {
            return dataPrimeiraParcela;
        }
        
        // Se o intervalo for aproximadamente mensal (28-31 dias), usa lógica mensal inteligente
        if (intervaloDias >= 28 && intervaloDias <= 31) {
            // Calcula quantos meses adicionar
            int mesesAdicionar = numeroParcela - 1;
            LocalDate dataCalculada = dataPrimeiraParcela.plusMonths(mesesAdicionar);
            
            // Tenta manter o mesmo dia do mês
            int diaOriginal = dataPrimeiraParcela.getDayOfMonth();
            try {
                // Tenta usar o mesmo dia do mês
                return dataCalculada.withDayOfMonth(diaOriginal);
            } catch (java.time.DateTimeException e) {
                // Se o dia não existe no mês (ex: 31 de fevereiro), usa o último dia do mês
                return dataCalculada.withDayOfMonth(dataCalculada.lengthOfMonth());
            }
        } else {
            // Para intervalos não mensais, usa a lógica de dias fixos
            // Parcela 2 = dataPrimeiraParcela + intervaloDias
            // Parcela 3 = dataPrimeiraParcela + (2 * intervaloDias)
            return dataPrimeiraParcela.plusDays((numeroParcela - 1) * intervaloDias);
        }
    }
    
    @Override
    public String toString() {
        return "InstallmentGroup{id=" + idGrupo + ", descricao='" + descricao + 
               "', valorTotal=" + valorTotal + ", parcelas=" + numeroParcelas + 
               ", valorParcela=" + valorParcela + ", tipo=" + tipoTransacao + "}";
    }
}

