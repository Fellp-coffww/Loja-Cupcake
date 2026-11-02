package loja;

import com.sun.net.httpserver.HttpServer;
import loja.controller.*;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.concurrent.Executors;

/** Ponto de entrada: registra os controllers (C) e inicia o servidor HTTP do JDK. */
public class Main {
    public static void main(String[] args) throws Exception {
        int porta = Integer.parseInt(System.getenv().getOrDefault("PORT", "8080"));
        iniciar(porta);
        System.out.println("Loja de Cupcakes em http://localhost:" + porta);
    }

    /** Cria e inicia o servidor. Com porta 0, o sistema escolhe uma porta livre (usado nos testes). */
    public static HttpServer iniciar(int porta) throws IOException {
        HttpServer servidor = HttpServer.create(new InetSocketAddress(porta), 0);

        AuthController auth = new AuthController();
        servidor.createContext("/api/login", auth);
        servidor.createContext("/api/cadastro", auth);
        servidor.createContext("/api/logout", auth);
        servidor.createContext("/api/me", auth);

        CupcakeController cupcakes = new CupcakeController();
        servidor.createContext("/api/cupcakes", cupcakes);
        servidor.createContext("/api/categorias", cupcakes);

        servidor.createContext("/api/carrinho", new CarrinhoController());
        servidor.createContext("/api/enderecos", new EnderecoController());
        servidor.createContext("/api/pedidos", new PedidoController());
        servidor.createContext("/api/admin", new AdminController());
        servidor.createContext("/", new ArquivosController());      // View estática

        servidor.setExecutor(Executors.newFixedThreadPool(8));
        servidor.start();
        return servidor;
    }
}
