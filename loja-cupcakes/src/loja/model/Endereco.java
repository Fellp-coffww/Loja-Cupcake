package loja.model;

import java.util.LinkedHashMap;
import java.util.Map;

public record Endereco(long id, String apelido, String cep, String logradouro, String numero,
                       String complemento, String bairro, String cidade, String uf) {
    public Map<String, Object> toMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", id); m.put("apelido", apelido); m.put("cep", cep); m.put("logradouro", logradouro);
        m.put("numero", numero); m.put("complemento", complemento); m.put("bairro", bairro);
        m.put("cidade", cidade); m.put("uf", uf);
        return m;
    }
}
