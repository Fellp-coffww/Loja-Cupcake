package loja.controller;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/** Serve a View (HTML/CSS/JS) da pasta "public". */
public class ArquivosController implements HttpHandler {
    private static final Map<String, String> TIPOS = Map.of(
        "html", "text/html; charset=utf-8", "css", "text/css; charset=utf-8",
        "js", "application/javascript; charset=utf-8", "png", "image/png", "jpg", "image/jpeg", "svg", "image/svg+xml");
    private final Path raiz = Path.of("public").toAbsolutePath().normalize();

    @Override
    public void handle(HttpExchange ex) throws IOException {
        String caminho = ex.getRequestURI().getPath();
        if (caminho.equals("/")) caminho = "/index.html";
        Path arquivo = raiz.resolve(caminho.substring(1)).normalize();
        if (!arquivo.startsWith(raiz) || !Files.isRegularFile(arquivo)) {
            byte[] msg = "Não encontrado".getBytes();
            ex.sendResponseHeaders(404, msg.length);
            try (OutputStream os = ex.getResponseBody()) { os.write(msg); }
            return;
        }
        String nome = arquivo.getFileName().toString();
        String ext = nome.substring(nome.lastIndexOf('.') + 1);
        byte[] bytes = Files.readAllBytes(arquivo);
        ex.getResponseHeaders().set("Content-Type", TIPOS.getOrDefault(ext, "application/octet-stream"));
        ex.sendResponseHeaders(200, bytes.length);
        try (OutputStream os = ex.getResponseBody()) { os.write(bytes); }
    }
}
