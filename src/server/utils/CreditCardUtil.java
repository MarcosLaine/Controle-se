package server.utils;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;

/**
 * Utilitários para cálculos relacionados a cartões de crédito
 * A fatura é identificada pela data de pagamento, não pela data de fechamento.
 * Exemplo: se fecha dia 29/04 e paga dia 01/05, essa é a fatura de maio.
 */
public class CreditCardUtil {
    
    /**
     * Calcula informações da fatura de cartão de crédito baseado nos dias de fechamento e pagamento.
     * A fatura é identificada pela data de pagamento, não pela data de fechamento.
     * Exemplo: se fecha dia 29/04 e paga dia 01/05, essa é a fatura de maio.
     * @param diaFechamento Dia do mês em que a fatura fecha (1-31)
     * @param diaPagamento Dia do mês em que a fatura deve ser paga (1-31)
     * @return Map com informações da fatura (próximo fechamento, próximo pagamento, status, etc.)
     */
    public static Map<String, Object> calcularInfoFatura(int diaFechamento, int diaPagamento) {
        Map<String, Object> info = new HashMap<>();
        LocalDate hoje = LocalDate.now();
        
        // Calcula a próxima data de pagamento (baseado na data de pagamento)
        LocalDate proximoPagamento;
        try {
            LocalDate pagamentoEsteMes = LocalDate.of(hoje.getYear(), hoje.getMonthValue(), diaPagamento);
            if (hoje.isBefore(pagamentoEsteMes) || hoje.isEqual(pagamentoEsteMes)) {
                // Ainda não passou o pagamento deste mês (ou é hoje)
                proximoPagamento = pagamentoEsteMes;
            } else {
                // Já passou o pagamento deste mês, próximo pagamento é no próximo mês
                LocalDate proximoMes = LocalDate.of(hoje.getYear(), hoje.getMonthValue(), 1).plusMonths(1);
                try {
                    proximoPagamento = proximoMes.withDayOfMonth(diaPagamento);
                } catch (java.time.DateTimeException e) {
                    // Se o dia não existe no próximo mês (ex: 31 de fevereiro), usa o último dia do mês
                    proximoPagamento = proximoMes.withDayOfMonth(proximoMes.lengthOfMonth());
                }
            }
        } catch (java.time.DateTimeException e) {
            // Se o dia não existe no mês atual (ex: 31 de fevereiro), usa o último dia do mês
            int ultimoDia = LocalDate.of(hoje.getYear(), hoje.getMonthValue(), 1).lengthOfMonth();
            LocalDate pagamentoEsteMes = LocalDate.of(hoje.getYear(), hoje.getMonthValue(), ultimoDia);
            if (hoje.isBefore(pagamentoEsteMes) || hoje.isEqual(pagamentoEsteMes)) {
                proximoPagamento = pagamentoEsteMes;
            } else {
                // Próximo mês
                LocalDate proximoMes = LocalDate.of(hoje.getYear(), hoje.getMonthValue(), 1).plusMonths(1);
                try {
                    proximoPagamento = proximoMes.withDayOfMonth(diaPagamento);
                } catch (java.time.DateTimeException e2) {
                    proximoPagamento = proximoMes.withDayOfMonth(proximoMes.lengthOfMonth());
                }
            }
        }
        
        // Para a fatura que será paga no próximo pagamento, encontra o fechamento correspondente
        // O fechamento é o último fechamento que acontece ANTES do pagamento
        LocalDate proximoFechamento;
        try {
            // Tenta encontrar o fechamento no mesmo mês do pagamento
            LocalDate fechamentoMesPagamento = LocalDate.of(proximoPagamento.getYear(), proximoPagamento.getMonthValue(), diaFechamento);
            if (fechamentoMesPagamento.isBefore(proximoPagamento) || fechamentoMesPagamento.equals(proximoPagamento)) {
                // O fechamento no mesmo mês do pagamento é antes ou igual ao pagamento
                proximoFechamento = fechamentoMesPagamento;
            } else {
                // O fechamento no mesmo mês do pagamento é depois do pagamento, então o fechamento correto é no mês anterior
                LocalDate mesAnterior = proximoPagamento.minusMonths(1);
                try {
                    proximoFechamento = mesAnterior.withDayOfMonth(diaFechamento);
                } catch (java.time.DateTimeException e) {
                    proximoFechamento = mesAnterior.withDayOfMonth(mesAnterior.lengthOfMonth());
                }
            }
        } catch (java.time.DateTimeException e) {
            // Se o dia não existe no mês do pagamento, tenta o mês anterior
            LocalDate mesAnterior = proximoPagamento.minusMonths(1);
            try {
                proximoFechamento = mesAnterior.withDayOfMonth(diaFechamento);
            } catch (java.time.DateTimeException e2) {
                proximoFechamento = mesAnterior.withDayOfMonth(mesAnterior.lengthOfMonth());
            }
        }
        
        // Calcula a data do último fechamento (fechamento anterior ao próximo fechamento)
        LocalDate ultimoFechamento;
        LocalDate mesAnteriorFechamento = proximoFechamento.minusMonths(1);
        try {
            ultimoFechamento = mesAnteriorFechamento.withDayOfMonth(diaFechamento);
        } catch (java.time.DateTimeException e) {
            // Se o dia não existe no mês (ex: 31 de fevereiro), usa o último dia do mês
            ultimoFechamento = mesAnteriorFechamento.withDayOfMonth(mesAnteriorFechamento.lengthOfMonth());
        }
        
        // Determina se a fatura atual está aberta ou fechada
        // Fatura está aberta se hoje está entre o último fechamento (inclusive) e o próximo fechamento (exclusive)
        boolean faturaAberta = !hoje.isBefore(ultimoFechamento) && hoje.isBefore(proximoFechamento);
        
        // Calcula dias até próximo fechamento e pagamento
        long diasAteFechamento = ChronoUnit.DAYS.between(hoje, proximoFechamento);
        long diasAtePagamento = ChronoUnit.DAYS.between(hoje, proximoPagamento);
        
        info.put("proximoFechamento", proximoFechamento.toString());
        info.put("proximoPagamento", proximoPagamento.toString());
        info.put("ultimoFechamento", ultimoFechamento.toString());
        info.put("faturaAberta", faturaAberta);
        info.put("diasAteFechamento", diasAteFechamento);
        info.put("diasAtePagamento", diasAtePagamento);
        
        return info;
    }
    
    /**
     * Calcula a data correta da primeira parcela para uma compra parcelada em cartão de crédito.
     * A primeira parcela deve cair na fatura que corresponde à data da compra.
     * A fatura é identificada pela data de pagamento, não pela data de fechamento.
     * 
     * Exemplo: 
     * - Compra em 09/10/2025
     * - Fechamento: dia 29, Pagamento: dia 1
     * - A compra pertence à fatura que fecha em 29/10 e paga em 01/11 (fatura de novembro)
     * - A primeira parcela deve ser em 29/10/2025 (data de fechamento dessa fatura)
     * 
     * @param dataCompra Data em que a compra foi feita
     * @param diaFechamento Dia do mês em que a fatura fecha (1-31)
     * @param diaPagamento Dia do mês em que a fatura deve ser paga (1-31)
     * @return Data correta da primeira parcela (data de fechamento da fatura correspondente)
     */
    public static LocalDate calcularDataPrimeiraParcela(LocalDate dataCompra, int diaFechamento, int diaPagamento) {
        // Calcula qual é o próximo pagamento a partir da data da compra
        LocalDate proximoPagamento;
        try {
            LocalDate pagamentoEsteMes = LocalDate.of(dataCompra.getYear(), dataCompra.getMonthValue(), diaPagamento);
            if (dataCompra.isBefore(pagamentoEsteMes) || dataCompra.isEqual(pagamentoEsteMes)) {
                // Ainda não passou o pagamento deste mês (ou é hoje)
                proximoPagamento = pagamentoEsteMes;
            } else {
                // Já passou o pagamento deste mês, próximo pagamento é no próximo mês
                LocalDate proximoMes = LocalDate.of(dataCompra.getYear(), dataCompra.getMonthValue(), 1).plusMonths(1);
                try {
                    proximoPagamento = proximoMes.withDayOfMonth(diaPagamento);
                } catch (java.time.DateTimeException e) {
                    proximoPagamento = proximoMes.withDayOfMonth(proximoMes.lengthOfMonth());
                }
            }
        } catch (java.time.DateTimeException e) {
            // Se o dia não existe no mês atual, usa o último dia do mês
            int ultimoDia = LocalDate.of(dataCompra.getYear(), dataCompra.getMonthValue(), 1).lengthOfMonth();
            LocalDate pagamentoEsteMes = LocalDate.of(dataCompra.getYear(), dataCompra.getMonthValue(), ultimoDia);
            if (dataCompra.isBefore(pagamentoEsteMes) || dataCompra.isEqual(pagamentoEsteMes)) {
                proximoPagamento = pagamentoEsteMes;
            } else {
                LocalDate proximoMes = LocalDate.of(dataCompra.getYear(), dataCompra.getMonthValue(), 1).plusMonths(1);
                try {
                    proximoPagamento = proximoMes.withDayOfMonth(diaPagamento);
                } catch (java.time.DateTimeException e2) {
                    proximoPagamento = proximoMes.withDayOfMonth(proximoMes.lengthOfMonth());
                }
            }
        }
        
        // Para a fatura que será paga no próximo pagamento, encontra o fechamento correspondente
        // O fechamento é o último fechamento que acontece ANTES do pagamento
        LocalDate fechamentoFatura;
        try {
            // Tenta encontrar o fechamento no mesmo mês do pagamento
            LocalDate fechamentoMesPagamento = LocalDate.of(proximoPagamento.getYear(), proximoPagamento.getMonthValue(), diaFechamento);
            if (fechamentoMesPagamento.isBefore(proximoPagamento) || fechamentoMesPagamento.equals(proximoPagamento)) {
                // O fechamento no mesmo mês do pagamento é antes ou igual ao pagamento
                fechamentoFatura = fechamentoMesPagamento;
            } else {
                // O fechamento no mesmo mês do pagamento é depois do pagamento, então o fechamento correto é no mês anterior
                LocalDate mesAnterior = proximoPagamento.minusMonths(1);
                try {
                    fechamentoFatura = mesAnterior.withDayOfMonth(diaFechamento);
                } catch (java.time.DateTimeException e) {
                    fechamentoFatura = mesAnterior.withDayOfMonth(mesAnterior.lengthOfMonth());
                }
            }
        } catch (java.time.DateTimeException e) {
            // Se o dia não existe no mês do pagamento, tenta o mês anterior
            LocalDate mesAnterior = proximoPagamento.minusMonths(1);
            try {
                fechamentoFatura = mesAnterior.withDayOfMonth(diaFechamento);
            } catch (java.time.DateTimeException e2) {
                fechamentoFatura = mesAnterior.withDayOfMonth(mesAnterior.lengthOfMonth());
            }
        }
        
        // A primeira parcela deve ser na data de fechamento da fatura
        return fechamentoFatura;
    }
}

