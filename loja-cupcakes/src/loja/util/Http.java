package loja.util;

import com.sun.net.httpserver.HttpExchange;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.Map;

/** Funções de apoio para ler requisições e responder em JSON. */
public final class Http {
    private Http() {}

    public static void json(HttpExchange ex, int status, Object corpo) throws IOException {
        byte[] bytes = Json.toJson(corpo).getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        ex.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = ex.getResponseBody()) { os.write(bytes); }
    }

    public static void erro(HttpExchange ex, int status, String mensagem) throws IOException {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("erro", mensagem);
        json(ex, status, m);
    }

    public static Map<String, Object> corpo(HttpExchange ex) throws IOException {
        String texto = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        return Json.lerObjeto(texto);
    }

    /** Divide o caminho: /api/carrinho/itens/3 -> ["api","carrinho","itens","3"] */
    public static String[] caminho(HttpExchange ex) {
        String p = ex.getRequestURI().getPath();
        return p.replaceAll("^/+|/+$", "").split("/");
    }

    /** Lê parâmetro de query string (?categoria=2). */
    public static String parametro(HttpExchange ex, String nome) {
        String q = ex.getRequestURI().getRawQuery();
        if (q == null) return null;
        for (String par : q.split("&")) {
            String[] kv = par.split("=", 2);
            if (kv[0].equals(nome) && kv.length == 2 && !kv[1].isEmpty()) {
                return java.net.URLDecoder.decode(kv[1], StandardCharsets.UTF_8);
            }
        }
        return null;
    }

    public static String token(HttpExchange ex) {
        String h = ex.getRequestHeaders().getFirst("Authorization");
        return (h != null && h.startsWith("Bearer ")) ? h.substring(7) : null;
    }

    /**
     * Exige login. Se perfil != null, exige também esse perfil.
     * Retorna null (e já responde 401/403) quando não autorizado.
     */
    public static Sessao.Dados exigir(HttpExchange ex, String perfil) throws IOException {
        Sessao.Dados s = Sessao.buscar(token(ex));
        if (s == null) { erro(ex, 401, "Faça login para continuar"); return null; }
        if (perfil != null && !perfil.equals(s.perfil())) { erro(ex, 403, "Acesso negado"); return null; }
        return s;
    }

    /** Traduz exceções do banco/validação em respostas HTTP. */
    public static void tratar(HttpExchange ex, Exception e) throws IOException {
        if (e instanceof IllegalArgumentException) {
            erro(ex, 400, e.getMessage());
        } else if (e instanceof SQLException sql) {
            String estado = sql.getSQLState() == null ? "" : sql.getSQLState();
            if (estado.startsWith("23") || estado.equals("P0001")) {
                erro(ex, 400, mensagemLimpa(sql));   // violação de regra de negócio
            } else {
                e.printStackTrace();
                erro(ex, 500, "Erro no banco de dados");
            }
        } else {
            e.printStackTrace();
            erro(ex, 500, "Erro interno");
        }
    }

    private static String mensagemLimpa(SQLException e) {
        String m = e.getMessage() == null ? "Dados inválidos" : e.getMessage();
        int i = m.indexOf('\n');
        m = i > 0 ? m.substring(0, i) : m;
        m = m.replaceFirst("^ERROR: ", "");
        if (m.contains("duplicate key")) return "Já existe um registro com este valor (nome ou e-mail repetido)";
        if (m.contains("violates check constraint")) return "Valor inválido em um dos campos";
        if (m.contains("violates foreign key")) return "Registro relacionado não encontrado";
        if (m.contains("violates not-null")) return "Preencha todos os campos obrigatórios";
        return m;
    }
}
