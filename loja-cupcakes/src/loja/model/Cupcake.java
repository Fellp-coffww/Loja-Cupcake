package loja.model;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

public record Cupcake(long id, String nome, String descricao, String ingredientes, BigDecimal preco,
                      String imagemUrl, int estoque, boolean destaque, boolean ativo, String categorias) {
    public Map<String, Object> toMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", id); m.put("nome", nome); m.put("descricao", descricao);
        m.put("ingredientes", ingredientes); m.put("preco", preco); m.put("imagemUrl", imagemUrl);
        m.put("estoque", estoque); m.put("destaque", destaque); m.put("ativo", ativo);
        m.put("categorias", categorias);
        return m;
    }
}
