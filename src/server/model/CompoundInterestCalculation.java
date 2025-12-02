package server.model;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public class CompoundInterestCalculation implements Serializable {
    private static final long serialVersionUID = 17L;
    private int idCalculo;
    private int idUsuario;
    private double aporteInicial;
    private double aporteMensal;
    private String frequenciaAporte; // mensal, quinzenal, anual
    private double taxaJuros;
    private String tipoTaxa; // mensal, anual
    private int prazo;
    private String tipoPrazo; // meses, anos
    private double totalInvestido;
    private double saldoFinal;
    private double totalJuros;
    private LocalDateTime dataCalculo;
    private boolean ativo;
    
    // Dados mensais (serializados como List<Map>)
    private List<Map<String, Object>> monthlyData;
    
    public CompoundInterestCalculation(int idCalculo, int idUsuario, double aporteInicial, 
                                     double aporteMensal, String frequenciaAporte,
                                     double taxaJuros, String tipoTaxa, int prazo, String tipoPrazo,
                                     double totalInvestido, double saldoFinal, double totalJuros) {
        this.idCalculo = idCalculo;
        this.idUsuario = idUsuario;
        this.aporteInicial = aporteInicial;
        this.aporteMensal = aporteMensal;
        this.frequenciaAporte = frequenciaAporte;
        this.taxaJuros = taxaJuros;
        this.tipoTaxa = tipoTaxa;
        this.prazo = prazo;
        this.tipoPrazo = tipoPrazo;
        this.totalInvestido = totalInvestido;
        this.saldoFinal = saldoFinal;
        this.totalJuros = totalJuros;
        this.dataCalculo = LocalDateTime.now();
        this.ativo = true;
    }
    
    public int getIdCalculo() { return idCalculo; }
    public void setIdCalculo(int idCalculo) { this.idCalculo = idCalculo; }
    
    public int getIdUsuario() { return idUsuario; }
    public void setIdUsuario(int idUsuario) { this.idUsuario = idUsuario; }
    
    public double getAporteInicial() { return aporteInicial; }
    public void setAporteInicial(double aporteInicial) { this.aporteInicial = aporteInicial; }
    
    public double getAporteMensal() { return aporteMensal; }
    public void setAporteMensal(double aporteMensal) { this.aporteMensal = aporteMensal; }
    
    public String getFrequenciaAporte() { return frequenciaAporte; }
    public void setFrequenciaAporte(String frequenciaAporte) { this.frequenciaAporte = frequenciaAporte; }
    
    public double getTaxaJuros() { return taxaJuros; }
    public void setTaxaJuros(double taxaJuros) { this.taxaJuros = taxaJuros; }
    
    public String getTipoTaxa() { return tipoTaxa; }
    public void setTipoTaxa(String tipoTaxa) { this.tipoTaxa = tipoTaxa; }
    
    public int getPrazo() { return prazo; }
    public void setPrazo(int prazo) { this.prazo = prazo; }
    
    public String getTipoPrazo() { return tipoPrazo; }
    public void setTipoPrazo(String tipoPrazo) { this.tipoPrazo = tipoPrazo; }
    
    public double getTotalInvestido() { return totalInvestido; }
    public void setTotalInvestido(double totalInvestido) { this.totalInvestido = totalInvestido; }
    
    public double getSaldoFinal() { return saldoFinal; }
    public void setSaldoFinal(double saldoFinal) { this.saldoFinal = saldoFinal; }
    
    public double getTotalJuros() { return totalJuros; }
    public void setTotalJuros(double totalJuros) { this.totalJuros = totalJuros; }
    
    public LocalDateTime getDataCalculo() { return dataCalculo; }
    public void setDataCalculo(LocalDateTime dataCalculo) { this.dataCalculo = dataCalculo; }
    
    public boolean isAtivo() { return ativo; }
    public void setAtivo(boolean ativo) { this.ativo = ativo; }
    
    public List<Map<String, Object>> getMonthlyData() { return monthlyData; }
    public void setMonthlyData(List<Map<String, Object>> monthlyData) { this.monthlyData = monthlyData; }
    
    @Override
    public String toString() {
        return "CompoundInterestCalculation{id=" + idCalculo + ", usuario=" + idUsuario + 
               ", aporteInicial=" + aporteInicial + ", aporteMensal=" + aporteMensal +
               ", taxaJuros=" + taxaJuros + "%, prazo=" + prazo + " " + tipoPrazo +
               ", saldoFinal=" + saldoFinal + "}";
    }
}

