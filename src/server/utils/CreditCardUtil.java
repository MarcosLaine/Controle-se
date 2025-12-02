package server.utils;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;

/**
 * Utilitários para cálculos relacionados a cartões de crédito
 */
public class CreditCardUtil {
    
    /**
     * Calcula informações da fatura de cartão de crédito baseado nos dias de fechamento e pagamento
     * @param diaFechamento Dia do mês em que a fatura fecha (1-31)
     * @param diaPagamento Dia do mês em que a fatura deve ser paga (1-31)
     * @return Map com informações da fatura (próximo fechamento, próximo pagamento, status, etc.)
     */
    public static Map<String, Object> calcularInfoFatura(int diaFechamento, int diaPagamento) {
        Map<String, Object> info = new HashMap<>();
        LocalDate hoje = LocalDate.now();
        
        // Calcula a próxima data de fechamento
        LocalDate proximoFechamento;
        
        // Tenta criar a data de fechamento no mês atual
        try {
            LocalDate fechamentoEsteMes = LocalDate.of(hoje.getYear(), hoje.getMonthValue(), diaFechamento);
            if (hoje.isBefore(fechamentoEsteMes) || hoje.isEqual(fechamentoEsteMes)) {
                // Ainda não fechou este mês (ou fecha hoje)
                proximoFechamento = fechamentoEsteMes;
            } else {
                // Já fechou este mês, próximo fechamento é no próximo mês
                LocalDate proximoMes = LocalDate.of(hoje.getYear(), hoje.getMonthValue(), 1).plusMonths(1);
                try {
                    proximoFechamento = proximoMes.withDayOfMonth(diaFechamento);
                } catch (java.time.DateTimeException e) {
                    // Se o dia não existe no próximo mês (ex: 31 de fevereiro), usa o último dia do mês
                    proximoFechamento = proximoMes.withDayOfMonth(proximoMes.lengthOfMonth());
                }
            }
        } catch (java.time.DateTimeException e) {
            // Se o dia não existe no mês atual (ex: 31 de fevereiro), usa o último dia do mês
            int ultimoDia = LocalDate.of(hoje.getYear(), hoje.getMonthValue(), 1).lengthOfMonth();
            LocalDate fechamentoEsteMes = LocalDate.of(hoje.getYear(), hoje.getMonthValue(), ultimoDia);
            if (hoje.isBefore(fechamentoEsteMes) || hoje.isEqual(fechamentoEsteMes)) {
                proximoFechamento = fechamentoEsteMes;
            } else {
                // Próximo mês
                LocalDate proximoMes = LocalDate.of(hoje.getYear(), hoje.getMonthValue(), 1).plusMonths(1);
                try {
                    proximoFechamento = proximoMes.withDayOfMonth(diaFechamento);
                } catch (java.time.DateTimeException e2) {
                    proximoFechamento = proximoMes.withDayOfMonth(proximoMes.lengthOfMonth());
                }
            }
        }
        
        // Calcula a próxima data de pagamento
        // Regra: o pagamento sempre acontece APÓS o fechamento
        LocalDate proximoPagamento;
        if (diaPagamento < diaFechamento) {
            // Pagamento é no mês seguinte ao fechamento
            LocalDate mesPagamento = proximoFechamento.plusMonths(1);
            try {
                proximoPagamento = mesPagamento.withDayOfMonth(diaPagamento);
            } catch (java.time.DateTimeException e) {
                // Se o dia não existe no mês (ex: 31 de fevereiro), usa o último dia do mês
                proximoPagamento = mesPagamento.withDayOfMonth(mesPagamento.lengthOfMonth());
            }
        } else {
            // Pagamento é no mesmo mês do fechamento, mas sempre depois do fechamento
            try {
                proximoPagamento = proximoFechamento.withDayOfMonth(diaPagamento);
                // Se o pagamento calculado é antes ou igual ao fechamento, ajusta para o mês seguinte
                if (proximoPagamento.isBefore(proximoFechamento) || proximoPagamento.equals(proximoFechamento)) {
                    LocalDate mesPagamento = proximoFechamento.plusMonths(1);
                    try {
                        proximoPagamento = mesPagamento.withDayOfMonth(diaPagamento);
                    } catch (java.time.DateTimeException e) {
                        proximoPagamento = mesPagamento.withDayOfMonth(mesPagamento.lengthOfMonth());
                    }
                }
            } catch (java.time.DateTimeException e) {
                // Se o dia não existe no mês, vai para o próximo mês
                LocalDate mesPagamento = proximoFechamento.plusMonths(1);
                try {
                    proximoPagamento = mesPagamento.withDayOfMonth(diaPagamento);
                } catch (java.time.DateTimeException e2) {
                    proximoPagamento = mesPagamento.withDayOfMonth(mesPagamento.lengthOfMonth());
                }
            }
        }
        
        // Calcula a data do último fechamento (fechamento anterior)
        LocalDate ultimoFechamento;
        LocalDate mesAnterior = proximoFechamento.minusMonths(1);
        try {
            ultimoFechamento = mesAnterior.withDayOfMonth(diaFechamento);
        } catch (java.time.DateTimeException e) {
            // Se o dia não existe no mês (ex: 31 de fevereiro), usa o último dia do mês
            ultimoFechamento = mesAnterior.withDayOfMonth(mesAnterior.lengthOfMonth());
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
}

