package loja.controller;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import loja.dao.EnderecoDao;
import loja.model.Endereco;
import loja.util.*;
import java.io.IOException;
import java.util.*;

/** GET/POST /api/enderecos (US07) */
public class EnderecoController implements HttpHandler {
    private final EnderecoDao dao = new EnderecoDao();

    @Override
    public void handle(HttpExchange ex) throws IOException {
        try {
            Sessao.Dados s = Http.exigir(ex, "CLIENTE");
            if (s == null) return;
            if (ex.getRequestMethod().equals("GET")) {
                List<Object> r = new ArrayList<>();
                for (Endereco e : dao.listar(s.usuarioId())) r.add(e.toMap());
                Http.json(ex, 200, r);
            } else if (ex.getRequestMethod().equals("POST")) {
                Map<String, Object> b = Http.corpo(ex);
                String cep = Json.str(b, "cep"), rua = Json.str(b, "logradouro"), num = Json.str(b, "numero"),
                       bairro = Json.str(b, "bairro"), cidade = Json.str(b, "cidade"), uf = Json.str(b, "uf");
                if (cep != null) cep = cep.replaceAll("\\D", "");
                if (cep == null || !cep.matches("[0-9]{8}")) throw new IllegalArgumentException("CEP deve ter 8 dígitos");
                if (rua == null || rua.isEmpty() || num == null || num.isEmpty() || bairro == null || bairro.isEmpty()
                        || cidade == null || cidade.isEmpty()) throw new IllegalArgumentException("Preencha rua, número, bairro e cidade");  // RN07
                if (uf == null || !uf.toUpperCase().matches("[A-Z]{2}")) throw new IllegalArgumentException("UF inválida");
                long id = dao.inserir(s.usuarioId(), Json.str(b, "apelido"), cep, rua, num, Json.str(b, "complemento"),
                        bairro, cidade, uf.toUpperCase());
                Http.json(ex, 201, Map.of("id", id));
            } else {
                Http.erro(ex, 405, "Método não permitido");
            }
        } catch (Exception e) {
            Http.tratar(ex, e);
        }
    }
}
