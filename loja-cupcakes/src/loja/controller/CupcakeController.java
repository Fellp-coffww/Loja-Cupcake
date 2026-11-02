package loja.controller;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import loja.dao.CupcakeDao;
import loja.model.Cupcake;
import loja.util.Http;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/** /api/cupcakes (vitrine, filtro, detalhes) e /api/categorias */
public class CupcakeController implements HttpHandler {
    private final CupcakeDao dao = new CupcakeDao();

    @Override
    public void handle(HttpExchange ex) throws IOException {
        String[] p = Http.caminho(ex);
        try {
            if (!ex.getRequestMethod().equals("GET")) { Http.erro(ex, 405, "Método não permitido"); return; }
            if (p[1].equals("categorias")) {
                Http.json(ex, 200, dao.listarCategorias());
            } else if (p.length == 2) {                       // US01 / US02
                String cat = Http.parametro(ex, "categoria");
                Long categoriaId = null;
                if (cat != null) {
                    try { categoriaId = Long.parseLong(cat); }
                    catch (NumberFormatException e) { throw new IllegalArgumentException("Categoria inválida"); }  // RN02
                }
                List<Object> saida = new ArrayList<>();
                for (Cupcake c : dao.listarAtivos(categoriaId)) saida.add(c.toMap());
                Http.json(ex, 200, saida);
            } else {                                            // US03
                Cupcake c = dao.buscarPorId(Long.parseLong(p[2]));
                if (c == null || !c.ativo()) Http.erro(ex, 404, "Produto não encontrado");
                else Http.json(ex, 200, c.toMap());
            }
        } catch (NumberFormatException e) {
            Http.erro(ex, 400, "Identificador inválido");
        } catch (Exception e) {
            Http.tratar(ex, e);
        }
    }
}
