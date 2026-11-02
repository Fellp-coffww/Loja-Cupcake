package loja.model;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

public record ItemCarrinho(long cupcakeId, String nome, int quantidade, BigDecimal preco, BigDecimal subtotal) {
    public Map<String, Object> toMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("cupcakeId", cupcakeId); m.put("nome", nome); m.put("quantidade", quantidade);
        m.put("preco", preco); m.put("subtotal", subtotal);
        return m;
    }
}
