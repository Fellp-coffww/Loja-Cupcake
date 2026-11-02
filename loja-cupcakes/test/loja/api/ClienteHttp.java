package loja.api;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import loja.util.Json;

/** Pequeno cliente HTTP usado pelos testes de API (simula o que o navegador faz). */
final class ClienteHttp {
    record Resposta(int status, String corpo) {
        Map<String, Object> json() { return Json.lerObjeto(corpo); }
        String texto(String campo) { return Json.str(json(), campo); }
        /** Lê um número do JSON (funciona também quando há listas, que o leitor simples não entende). */
        java.math.BigDecimal numero(String campo) {
            java.util.regex.Matcher m = java.util.regex.Pattern.compile("\"" + campo + "\":(-?[0-9.]+)").matcher(corpo);
            if (!m.find()) throw new IllegalStateException("campo não encontrado: " + campo);
            return new java.math.BigDecimal(m.group(1));
        }
        int contar(String trecho) {
            int n = 0, i = 0;
            while ((i = corpo.indexOf(trecho, i)) >= 0) { n++; i += trecho.length(); }
            return n;
        }
    }

    private final HttpClient http = HttpClient.newHttpClient();
    private final String base;

    ClienteHttp(int porta) { this.base = "http://localhost:" + porta; }

    Resposta get(String caminho, String token) { return enviar("GET", caminho, null, token); }
    Resposta post(String caminho, String json, String token) { return enviar("POST", caminho, json, token); }
    Resposta put(String caminho, String json, String token) { return enviar("PUT", caminho, json, token); }
    Resposta delete(String caminho, String token) { return enviar("DELETE", caminho, null, token); }

    Resposta enviar(String metodo, String caminho, String json, String token) {
        try {
            HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(base + caminho))
                    .method(metodo, json == null ? HttpRequest.BodyPublishers.noBody()
                                                  : HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
                    .header("Content-Type", "application/json");
            if (token != null) b.header("Authorization", "Bearer " + token);
            HttpResponse<String> r = http.send(b.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            return new Resposta(r.statusCode(), r.body());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    String login(String email, String senha) {
        Resposta r = post("/api/login", "{\"email\":\"" + email + "\",\"senha\":\"" + senha + "\"}", null);
        if (r.status() != 200) throw new IllegalStateException("login falhou: " + r.corpo());
        return r.texto("token");
    }
}
