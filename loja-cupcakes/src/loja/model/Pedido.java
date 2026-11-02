package loja.model;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

public record Pedido(long id, String cliente, String status, String dataPedido, BigDecimal valorTotal,
                     String statusPagamento) {
    public Map<String, Object> toMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", id); m.put("cliente", cliente); m.put("status", status); m.put("dataPedido", dataPedido);
        m.put("valorTotal", valorTotal); m.put("statusPagamento", statusPagamento);
        return m;
    }
}
