package loja.util;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Sessões em memória: token -> usuário logado. */
public final class Sessao {
    public record Dados(long usuarioId, String nome, String perfil) {}

    private static final Map<String, Dados> SESSOES = new ConcurrentHashMap<>();

    private Sessao() {}

    public static String criar(Dados d) {
        String token = UUID.randomUUID().toString();
        SESSOES.put(token, d);
        return token;
    }

    public static Dados buscar(String token) {
        return token == null ? null : SESSOES.get(token);
    }

    public static void encerrar(String token) {
        if (token != null) SESSOES.remove(token);
    }
}
