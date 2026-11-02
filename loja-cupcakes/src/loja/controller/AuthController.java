package loja.controller;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import loja.dao.UsuarioDao;
import loja.model.Usuario;
import loja.util.*;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

/** /api/login, /api/cadastro, /api/logout, /api/me */
public class AuthController implements HttpHandler {
    private final UsuarioDao dao = new UsuarioDao();

    @Override
    public void handle(HttpExchange ex) throws IOException {
        String rota = Http.caminho(ex)[1];
        String metodo = ex.getRequestMethod();
        try {
            if (rota.equals("login") && metodo.equals("POST")) login(ex);
            else if (rota.equals("cadastro") && metodo.equals("POST")) cadastro(ex);
            else if (rota.equals("logout") && metodo.equals("POST")) { Sessao.encerrar(Http.token(ex)); Http.json(ex, 200, Map.of("ok", true)); }
            else if (rota.equals("me") && metodo.equals("GET")) me(ex);
            else Http.erro(ex, 404, "Rota não encontrada");
        } catch (Exception e) {
            Http.tratar(ex, e);
        }
    }

    private void login(HttpExchange ex) throws Exception {
        Map<String, Object> b = Http.corpo(ex);
        String email = Json.str(b, "email"), senha = Json.str(b, "senha");
        if (email == null || senha == null) throw new IllegalArgumentException("Informe e-mail e senha");
        Usuario u = dao.autenticar(email, Senha.hash(senha));
        if (u == null) { Http.erro(ex, 401, "E-mail ou senha inválidos"); return; }
        String token = Sessao.criar(new Sessao.Dados(u.id(), u.nome(), u.perfil()));
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("token", token); r.put("nome", u.nome()); r.put("perfil", u.perfil());
        Http.json(ex, 200, r);
    }

    private void cadastro(HttpExchange ex) throws Exception {
        Map<String, Object> b = Http.corpo(ex);
        String nome = Json.str(b, "nome"), email = Json.str(b, "email"), senha = Json.str(b, "senha");
        if (nome == null || email == null || senha == null) throw new IllegalArgumentException("Preencha nome, e-mail e senha");
        if (senha.length() < 6) throw new IllegalArgumentException("A senha deve ter ao menos 6 caracteres");
        String telefone = Json.str(b, "telefone");
        if (telefone != null && !telefone.isEmpty() && !telefone.matches("[0-9]{10,15}"))
            throw new IllegalArgumentException("Telefone deve ter de 10 a 15 dígitos");
        try {
            dao.cadastrarCliente(nome, email, Senha.hash(senha), telefone);
        } catch (java.sql.SQLException e) {
            if ("23505".equals(e.getSQLState())) throw new IllegalArgumentException("E-mail já cadastrado");
            throw e;
        }
        Http.json(ex, 201, Map.of("ok", true));
    }

    private void me(HttpExchange ex) throws Exception {
        Sessao.Dados s = Http.exigir(ex, null);
        if (s == null) return;
        Http.json(ex, 200, Map.of("nome", s.nome(), "perfil", s.perfil()));
    }
}
