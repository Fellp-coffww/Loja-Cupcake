package loja.controller;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import loja.dao.CupcakeDao;
import loja.dao.PedidoDao;
import loja.model.Cupcake;
import loja.model.Pedido;
import loja.util.*;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.*;

/**
 * Área do dono (somente ADMINISTRADOR):
 * GET  /api/admin/cupcakes            POST /api/admin/cupcakes (US12)   PUT /api/admin/cupcakes/{id} (US13)
 * GET  /api/admin/pedidos             PUT /api/admin/pedidos/{id}/status (US14)
 * GET  /api/admin/dashboard (US15)
 */
public class AdminController implements HttpHandler {
    private final CupcakeDao cupcakes = new CupcakeDao();
    private final PedidoDao pedidos = new PedidoDao();

    @Override
    public void handle(HttpExchange ex) throws IOException {
        try {
            Sessao.Dados s = Http.exigir(ex, "ADMINISTRADOR");
            if (s == null) return;
            String[] p = Http.caminho(ex);
            String m = ex.getRequestMethod();
            String recurso = p[2];

            if (recurso.equals("cupcakes") && p.length == 3 && m.equals("GET")) {
                List<Object> r = new ArrayList<>();
                for (Cupcake c : cupcakes.listarTodos()) r.add(c.toMap());
                Http.json(ex, 200, r);
            } else if (recurso.equals("cupcakes") && p.length == 3 && m.equals("POST")) {
                Http.json(ex, 201, Map.of("id", salvar(null, Http.corpo(ex))));
            } else if (recurso.equals("cupcakes") && p.length == 4 && m.equals("PUT")) {
                Http.json(ex, 200, Map.of("id", salvar(Long.parseLong(p[3]), Http.corpo(ex))));
            } else if (recurso.equals("pedidos") && p.length == 3 && m.equals("GET")) {
                List<Object> r = new ArrayList<>();
                for (Pedido pd : pedidos.listarTodos()) r.add(pd.toMap());
                Http.json(ex, 200, r);
            } else if (recurso.equals("pedidos") && p.length == 5 && p[4].equals("status") && m.equals("PUT")) {
                String novo = Json.str(Http.corpo(ex), "status");
                if (novo == null) throw new IllegalArgumentException("Informe o novo status");
                pedidos.atualizarStatus(s.usuarioId(), Long.parseLong(p[3]), novo);
                Http.json(ex, 200, Map.of("ok", true));
            } else if (recurso.equals("dashboard") && m.equals("GET")) {
                Http.json(ex, 200, pedidos.dashboard());
            } else {
                Http.erro(ex, 404, "Rota não encontrada");
            }
        } catch (NumberFormatException e) {
            Http.erro(ex, 400, "Valor numérico inválido");
        } catch (Exception e) {
            if (e instanceof java.sql.SQLException sql && "22P02".equals(sql.getSQLState())) {
                Http.erro(ex, 400, "Status inválido");
            } else {
                Http.tratar(ex, e);
            }
        }
    }

    private long salvar(Long id, Map<String, Object> b) throws Exception {
        String nome = Json.str(b, "nome"), descricao = Json.str(b, "descricao");
        if (nome == null || nome.isEmpty() || descricao == null || descricao.isEmpty())
            throw new IllegalArgumentException("Nome e descrição são obrigatórios");                        // RN12
        BigDecimal preco = Json.decimal(b, "preco");
        if (preco.signum() <= 0) throw new IllegalArgumentException("Preço deve ser maior que zero");
        int estoque = b.get("estoque") == null ? 0 : (int) Json.longo(b, "estoque");
        if (estoque < 0) throw new IllegalArgumentException("Estoque não pode ser negativo");
        return cupcakes.salvar(id, nome, descricao, Json.str(b, "ingredientes"), preco, Json.str(b, "imagemUrl"),
                estoque, Json.bool(b, "destaque", false), Json.bool(b, "ativo", true),
                Json.listaIds(Json.str(b, "categorias")));
    }
}
