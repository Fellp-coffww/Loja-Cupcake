package loja.controller;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import loja.dao.CarrinhoDao;
import loja.model.ItemCarrinho;
import loja.util.*;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.*;

/** GET /api/carrinho | POST /api/carrinho/itens | DELETE /api/carrinho/itens/{cupcakeId} */
public class CarrinhoController implements HttpHandler {
    private final CarrinhoDao dao = new CarrinhoDao();

    @Override
    public void handle(HttpExchange ex) throws IOException {
        try {
            Sessao.Dados s = Http.exigir(ex, "CLIENTE");
            if (s == null) return;
            String[] p = Http.caminho(ex);
            String m = ex.getRequestMethod();
            if (p.length == 2 && m.equals("GET")) {
                listar(ex, s);
            } else if (p.length == 3 && m.equals("POST")) {
                Map<String, Object> b = Http.corpo(ex);
                long cupcakeId = Json.longo(b, "cupcakeId");
                long qtd = b.get("quantidade") == null ? 1 : Json.longo(b, "quantidade");
                if (qtd < 1 || qtd > 99) throw new IllegalArgumentException("Quantidade deve ser de 1 a 99");   // RN04
                dao.adicionar(s.usuarioId(), cupcakeId, (int) qtd);
                listar(ex, s);
            } else if (p.length == 4 && m.equals("DELETE")) {
                dao.remover(s.usuarioId(), Long.parseLong(p[3]));
                listar(ex, s);
            } else {
                Http.erro(ex, 404, "Rota não encontrada");
            }
        } catch (NumberFormatException e) {
            Http.erro(ex, 400, "Valor numérico inválido");
        } catch (Exception e) {
            Http.tratar(ex, e);
        }
    }

    private void listar(HttpExchange ex, Sessao.Dados s) throws Exception {
        List<Object> itens = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;
        for (ItemCarrinho i : dao.listar(s.usuarioId())) {
            itens.add(i.toMap());
            total = total.add(i.subtotal());
        }
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("itens", itens); r.put("total", total);
        Http.json(ex, 200, r);
    }
}
