package loja.controller;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import loja.dao.PedidoDao;
import loja.model.Pedido;
import loja.util.*;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.*;

/**
 * GET  /api/pedidos                   -> meus pedidos (US11)
 * POST /api/pedidos                   -> confirmar pedido a partir do carrinho (US09)
 * POST /api/pedidos/{id}/pagamento    -> pagar (US08 / US10)
 */
public class PedidoController implements HttpHandler {
    private static final BigDecimal TAXA_ENTREGA = new BigDecimal("5.00");
    private final PedidoDao dao = new PedidoDao();

    @Override
    public void handle(HttpExchange ex) throws IOException {
        try {
            Sessao.Dados s = Http.exigir(ex, "CLIENTE");
            if (s == null) return;
            String[] p = Http.caminho(ex);
            String m = ex.getRequestMethod();
            if (p.length == 2 && m.equals("GET")) {
                List<Object> r = new ArrayList<>();
                for (Pedido pd : dao.listarDoCliente(s.usuarioId())) r.add(pd.toMap());
                Http.json(ex, 200, r);
            } else if (p.length == 2 && m.equals("POST")) {
                long enderecoId = Json.longo(Http.corpo(ex), "enderecoId");
                long id = dao.confirmar(s.usuarioId(), enderecoId, TAXA_ENTREGA);
                Http.json(ex, 201, Map.of("pedidoId", id));
            } else if (p.length == 4 && p[3].equals("pagamento") && m.equals("POST")) {
                Map<String, Object> b = Http.corpo(ex);
                String metodo = Json.str(b, "metodo");
                if (metodo == null || !(metodo.equals("PIX") || metodo.equals("CARTAO")))       // RN08
                    throw new IllegalArgumentException("Forma de pagamento inválida (PIX ou CARTAO)");
                String status = dao.pagar(s.usuarioId(), Long.parseLong(p[2]), metodo, Json.bool(b, "recusar", false));
                Http.json(ex, 200, Map.of("status", status));
            } else {
                Http.erro(ex, 404, "Rota não encontrada");
            }
        } catch (NumberFormatException e) {
            Http.erro(ex, 400, "Valor numérico inválido");
        } catch (Exception e) {
            Http.tratar(ex, e);
        }
    }
}
