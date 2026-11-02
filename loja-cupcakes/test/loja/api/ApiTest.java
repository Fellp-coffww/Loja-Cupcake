package loja.api;

import static loja.BancoDeTestes.*;
import static org.junit.jupiter.api.Assertions.*;

import com.sun.net.httpserver.HttpServer;
import loja.BancoDeTestes;
import loja.Main;
import loja.api.ClienteHttp.Resposta;
import org.junit.jupiter.api.*;

/**
 * Testes de ponta a ponta da API: sobem o servidor de verdade (porta livre) e fazem requisições HTTP,
 * exercitando Controller, DAO e banco juntos, como o navegador faria.
 */
class ApiTest {
    private static HttpServer servidor;
    private static ClienteHttp http;

    @BeforeAll static void subirServidor() throws Exception {
        BancoDeTestes.preparar();
        servidor = Main.iniciar(0);
        http = new ClienteHttp(servidor.getAddress().getPort());
    }

    @AfterAll static void derrubarServidor() { servidor.stop(0); }

    @BeforeEach void banco() throws Exception { BancoDeTestes.preparar(); }

    private String maria() { return http.login(MARIA, "123456"); }
    private String admin() { return http.login(ADMIN, "123456"); }

    private static String cupcakeJson(String nome, String preco) {
        return "{\"nome\":\"" + nome + "\",\"descricao\":\"Descrição\",\"preco\":" + preco + ",\"estoque\":10,\"categorias\":\"1,5\"}";
    }

    /** Coloca 2 brigadeiros + 1 vegano no carrinho e confirma o pedido. Devolve o id do pedido. */
    private long fazerPedido(String token) throws Exception {
        http.post("/api/carrinho/itens", "{\"cupcakeId\":" + idCupcake(BRIGADEIRO) + ",\"quantidade\":2}", token);
        http.post("/api/carrinho/itens", "{\"cupcakeId\":" + idCupcake(VEGANO) + ",\"quantidade\":1}", token);
        long endereco = contar("SELECT id FROM endereco LIMIT 1");
        Resposta r = http.post("/api/pedidos", "{\"enderecoId\":" + endereco + "}", token);
        assertEquals(201, r.status(), r.corpo());
        return Long.parseLong(r.json().get("pedidoId").toString());
    }

    // ---------------- Vitrine (públicas) ----------------
    @Test @DisplayName("[US01] Vitrine é pública e lista os 5 cupcakes ativos")
    void vitrine() {
        Resposta r = http.get("/api/cupcakes", null);
        assertEquals(200, r.status());
        assertEquals(5, r.contar("\"nome\":"));
        assertTrue(r.corpo().contains("Brigadeiro Gourmet"));
    }

    @Test @DisplayName("[US01, RN01] Produto inativo não aparece na vitrine da API")
    void vitrineSemInativos() throws Exception {
        executar("UPDATE cupcake SET ativo = FALSE WHERE nome = ?", MORANGO);
        Resposta r = http.get("/api/cupcakes", null);
        assertEquals(4, r.contar("\"nome\":"));
        assertFalse(r.corpo().contains(MORANGO));
    }

    @Test @DisplayName("[US02] Filtro por categoria retorna só os produtos dela")
    void filtro() throws Exception {
        long vegano = contar("SELECT id FROM categoria WHERE nome = 'Vegano'");
        Resposta r = http.get("/api/cupcakes?categoria=" + vegano, null);
        assertEquals(200, r.status());
        assertEquals(1, r.contar("\"nome\":"));
        assertTrue(r.corpo().contains(VEGANO));
        assertEquals(5, http.get("/api/categorias", null).contar("\"nome\":"));
    }

    @Test @DisplayName("[US02, RN02] Categoria inválida gera erro 400")
    void filtroInvalido() {
        Resposta r = http.get("/api/cupcakes?categoria=abc", null);
        assertEquals(400, r.status());
        assertEquals("Categoria inválida", r.texto("erro"));
    }

    @Test @DisplayName("[US03] Detalhes do produto: 200 se existe e ativo, 404 se não")
    void detalhes() throws Exception {
        Resposta ok = http.get("/api/cupcakes/" + idCupcake(VEGANO), null);
        assertEquals(200, ok.status());
        assertEquals(VEGANO, ok.texto("nome"));
        assertEquals(404, http.get("/api/cupcakes/99999", null).status());
        executar("UPDATE cupcake SET ativo = FALSE WHERE nome = ?", VEGANO);
        assertEquals(404, http.get("/api/cupcakes/" + idCupcake(VEGANO), null).status());
        assertEquals(400, http.get("/api/cupcakes/abc", null).status());
    }

    // ---------------- Acesso e segurança ----------------
    @Test @DisplayName("[ACESSO] Login válido devolve token, nome e perfil")
    void loginValido() {
        Resposta r = http.post("/api/login", "{\"email\":\"" + MARIA + "\",\"senha\":\"123456\"}", null);
        assertEquals(200, r.status());
        assertEquals("CLIENTE", r.texto("perfil"));
        assertEquals("Maria Silva", r.texto("nome"));
        assertFalse(r.texto("token").isBlank());
    }

    @Test @DisplayName("[ACESSO] Login inválido devolve 401; campos ausentes devolvem 400")
    void loginInvalido() {
        assertEquals(401, http.post("/api/login", "{\"email\":\"" + MARIA + "\",\"senha\":\"errada\"}", null).status());
        assertEquals(400, http.post("/api/login", "{\"email\":\"" + MARIA + "\"}", null).status());
        assertEquals(400, http.post("/api/login", "", null).status());
    }

    @Test @DisplayName("[ACESSO] Rotas protegidas exigem login (401) e rejeitam token inventado")
    void exigeLogin() {
        assertEquals(401, http.get("/api/carrinho", null).status());
        assertEquals(401, http.get("/api/pedidos", null).status());
        assertEquals(401, http.get("/api/enderecos", null).status());
        assertEquals(401, http.get("/api/admin/dashboard", null).status());
        assertEquals(401, http.get("/api/carrinho", "token-inventado").status());
    }

    @Test @DisplayName("[ACESSO] Cliente não acessa o painel do dono (403) e o dono não usa o carrinho (403)")
    void perfis() {
        String cliente = maria();
        assertEquals(403, http.get("/api/admin/dashboard", cliente).status());
        assertEquals(403, http.get("/api/admin/pedidos", cliente).status());
        assertEquals(403, http.post("/api/admin/cupcakes", cupcakeJson("X", "5"), cliente).status());
        assertEquals(403, http.get("/api/carrinho", admin()).status());
    }

    @Test @DisplayName("[ACESSO] Logout invalida o token")
    void logout() {
        String t = maria();
        assertEquals(200, http.get("/api/me", t).status());
        assertEquals(200, http.post("/api/logout", "{}", t).status());
        assertEquals(401, http.get("/api/me", t).status());
    }

    @Test @DisplayName("[ACESSO] Cadastro de cliente novo e login em seguida")
    void cadastro() {
        Resposta r = http.post("/api/cadastro", "{\"nome\":\"Ana Lima\",\"email\":\"ana@email.com\",\"senha\":\"segredo1\",\"telefone\":\"62999998888\"}", null);
        assertEquals(201, r.status());
        assertDoesNotThrow(() -> http.login("ana@email.com", "segredo1"));
    }

    @Test @DisplayName("[ACESSO] Cadastro rejeita e-mail repetido, senha curta e telefone inválido")
    void cadastroInvalido() {
        Resposta dup = http.post("/api/cadastro", "{\"nome\":\"Outra\",\"email\":\"" + MARIA + "\",\"senha\":\"123456\"}", null);
        assertEquals(400, dup.status());
        assertEquals("E-mail já cadastrado", dup.texto("erro"));
        assertEquals(400, http.post("/api/cadastro", "{\"nome\":\"A\",\"email\":\"a@a.com\",\"senha\":\"123\"}", null).status());
        assertEquals(400, http.post("/api/cadastro", "{\"nome\":\"Ana\",\"email\":\"a@a.com\",\"senha\":\"123456\",\"telefone\":\"abc\"}", null).status());
        assertEquals(400, http.post("/api/cadastro", "{\"email\":\"a@a.com\"}", null).status());
    }

    // ---------------- Carrinho ----------------
    @Test @DisplayName("[US04, US06] Adicionar ao carrinho devolve itens e total atualizados")
    void carrinho() throws Exception {
        String t = maria();
        Resposta r = http.post("/api/carrinho/itens", "{\"cupcakeId\":" + idCupcake(BRIGADEIRO) + ",\"quantidade\":2}", t);
        assertEquals(200, r.status());
        assertEquals(1, r.contar("\"cupcakeId\":"));
        r = http.post("/api/carrinho/itens", "{\"cupcakeId\":" + idCupcake(VEGANO) + "}", t);   // quantidade padrão = 1
        assertEquals(200, r.status());
        assertEquals(2, r.contar("\"cupcakeId\":"));
        assertEquals(0, new java.math.BigDecimal("30.00").compareTo(r.numero("total")));
        assertEquals(200, http.get("/api/carrinho", t).status());
    }

    @Test @DisplayName("[US04, RN04] Quantidade fora de 1 a 99 é recusada com 400")
    void quantidadeInvalida() throws Exception {
        String t = maria();
        long id = idCupcake(BRIGADEIRO);
        assertEquals(400, http.post("/api/carrinho/itens", "{\"cupcakeId\":" + id + ",\"quantidade\":0}", t).status());
        assertEquals(400, http.post("/api/carrinho/itens", "{\"cupcakeId\":" + id + ",\"quantidade\":-2}", t).status());
        assertEquals(400, http.post("/api/carrinho/itens", "{\"cupcakeId\":" + id + ",\"quantidade\":100}", t).status());
        assertEquals(400, http.post("/api/carrinho/itens", "{\"quantidade\":1}", t).status());
        assertEquals(400, http.post("/api/carrinho/itens", "{\"cupcakeId\":99999,\"quantidade\":1}", t).status());
        assertEquals(400, http.post("/api/carrinho/itens", "{\"cupcakeId\":" + id + ",\"quantidade\":1.5}", t).status());   // decimal
        assertEquals(400, http.post("/api/carrinho/itens", "{\"cupcakeId\":" + id + ",\"quantidade\":\"muitos\"}", t).status());
        assertEquals(0, http.get("/api/carrinho", t).contar("\"cupcakeId\":"));
    }

    @Test @DisplayName("[US05] Remover item do carrinho atualiza o total")
    void removerItem() throws Exception {
        String t = maria();
        http.post("/api/carrinho/itens", "{\"cupcakeId\":" + idCupcake(BRIGADEIRO) + ",\"quantidade\":2}", t);
        http.post("/api/carrinho/itens", "{\"cupcakeId\":" + idCupcake(VEGANO) + ",\"quantidade\":1}", t);
        Resposta r = http.delete("/api/carrinho/itens/" + idCupcake(BRIGADEIRO), t);
        assertEquals(200, r.status());
        assertEquals(1, r.contar("\"cupcakeId\":"));
        assertEquals(0, new java.math.BigDecimal("11.00").compareTo(r.numero("total")));
    }

    // ---------------- Endereço ----------------
    @Test @DisplayName("[US07] Cadastrar endereço válido e listá-lo")
    void enderecoValido() {
        String t = http.login(JOAO, "123456");
        Resposta r = http.post("/api/enderecos",
            "{\"cep\":\"74110-010\",\"logradouro\":\"Av. Goiás\",\"numero\":\"500\",\"bairro\":\"Centro\",\"cidade\":\"Goiânia\",\"uf\":\"go\"}", t);
        assertEquals(201, r.status());
        Resposta lista = http.get("/api/enderecos", t);
        assertEquals(1, lista.contar("\"logradouro\":"));
        assertTrue(lista.corpo().contains("74110010"));   // hífen removido
        assertTrue(lista.corpo().contains("\"uf\":\"GO\""));   // UF em maiúsculas
    }

    @Test @DisplayName("[US07, RN07] Endereço com CEP, UF ou campos obrigatórios inválidos é recusado")
    void enderecoInvalido() {
        String t = maria();
        String ok = "\"logradouro\":\"Rua\",\"numero\":\"1\",\"bairro\":\"B\",\"cidade\":\"C\"";
        assertEquals(400, http.post("/api/enderecos", "{\"cep\":\"123\"," + ok + ",\"uf\":\"GO\"}", t).status());
        assertEquals(400, http.post("/api/enderecos", "{\"cep\":\"74000000\"," + ok + ",\"uf\":\"GOI\"}", t).status());
        assertEquals(400, http.post("/api/enderecos", "{\"cep\":\"74000000\",\"numero\":\"1\",\"bairro\":\"B\",\"cidade\":\"C\",\"uf\":\"GO\"}", t).status());
        assertEquals(1, http.get("/api/enderecos", t).contar("\"logradouro\":"));   // só o da carga inicial
    }

    // ---------------- Pedido e pagamento ----------------
    @Test @DisplayName("[US09] Confirmar pedido: 201, estoque baixa e carrinho esvazia")
    void confirmarPedido() throws Exception {
        String t = maria();
        long id = fazerPedido(t);
        assertTrue(id > 0);
        assertEquals(28, contar("SELECT estoque FROM cupcake WHERE nome = ?", BRIGADEIRO));
        assertEquals(0, http.get("/api/carrinho", t).contar("\"cupcakeId\":"));
        Resposta lista = http.get("/api/pedidos", t);
        assertEquals(1, lista.contar("\"status\":\"AGUARDANDO_PAGAMENTO\""));
    }

    @Test @DisplayName("[US09, RN09] Confirmar com carrinho vazio ou sem endereço devolve 400")
    void confirmarInvalido() throws Exception {
        String t = maria();
        long endereco = contar("SELECT id FROM endereco LIMIT 1");
        assertEquals(400, http.post("/api/pedidos", "{\"enderecoId\":" + endereco + "}", t).status());
        assertEquals(400, http.post("/api/pedidos", "{}", t).status());
    }

    @Test @DisplayName("[US08, RN08] Forma de pagamento inválida devolve 400")
    void metodoInvalido() throws Exception {
        String t = maria();
        long id = fazerPedido(t);
        Resposta r = http.post("/api/pedidos/" + id + "/pagamento", "{\"metodo\":\"BOLETO\"}", t);
        assertEquals(400, r.status());
        assertEquals(400, http.post("/api/pedidos/" + id + "/pagamento", "{}", t).status());
    }

    @Test @DisplayName("[US10, RN10] Pagamento recusado e depois aprovado leva o pedido a PAGO")
    void pagamento() throws Exception {
        String t = maria();
        long id = fazerPedido(t);
        Resposta recusado = http.post("/api/pedidos/" + id + "/pagamento", "{\"metodo\":\"CARTAO\",\"recusar\":true}", t);
        assertEquals(200, recusado.status());
        assertEquals("RECUSADO", recusado.texto("status"));
        assertTrue(http.get("/api/pedidos", t).corpo().contains("\"status\":\"AGUARDANDO_PAGAMENTO\""));

        Resposta aprovado = http.post("/api/pedidos/" + id + "/pagamento", "{\"metodo\":\"PIX\"}", t);
        assertEquals("APROVADO", aprovado.texto("status"));
        Resposta lista = http.get("/api/pedidos", t);
        assertTrue(lista.corpo().contains("\"status\":\"PAGO\""));
        assertTrue(lista.corpo().contains("\"statusPagamento\":\"APROVADO\""));
    }

    @Test @DisplayName("[US10] Pedido já pago não pode ser pago de novo; pedido alheio também não")
    void pagamentoRepetidoOuAlheio() throws Exception {
        String t = maria();
        long id = fazerPedido(t);
        assertEquals(400, http.post("/api/pedidos/" + id + "/pagamento", "{\"metodo\":\"PIX\"}", http.login(JOAO, "123456")).status());
        assertEquals(200, http.post("/api/pedidos/" + id + "/pagamento", "{\"metodo\":\"PIX\"}", t).status());
        assertEquals(400, http.post("/api/pedidos/" + id + "/pagamento", "{\"metodo\":\"PIX\"}", t).status());
    }

    @Test @DisplayName("[US11] Cliente só vê os próprios pedidos")
    void meusPedidos() throws Exception {
        String t = maria();
        fazerPedido(t);
        assertEquals(1, http.get("/api/pedidos", t).contar("\"id\":"));
        assertEquals(0, http.get("/api/pedidos", http.login(JOAO, "123456")).contar("\"id\":"));
    }

    // ---------------- Painel do dono ----------------
    @Test @DisplayName("[US12] Dono cadastra produto novo e ele aparece na vitrine")
    void cadastrarProduto() {
        String a = admin();
        Resposta r = http.post("/api/admin/cupcakes", cupcakeJson("Red Velvet", "12.5"), a);
        assertEquals(201, r.status());
        assertEquals(6, http.get("/api/cupcakes", null).contar("\"nome\":"));
        assertEquals(6, http.get("/api/admin/cupcakes", a).contar("\"nome\":"));
    }

    @Test @DisplayName("[US12, RN12] Cadastro recusa campos obrigatórios vazios, preço zero e nome repetido")
    void cadastrarInvalido() {
        String a = admin();
        assertEquals(400, http.post("/api/admin/cupcakes", cupcakeJson("Zero", "0"), a).status());
        assertEquals(400, http.post("/api/admin/cupcakes", cupcakeJson("Negativo", "-3"), a).status());
        assertEquals(400, http.post("/api/admin/cupcakes", "{\"nome\":\"Sem descrição\",\"preco\":5}", a).status());
        assertEquals(400, http.post("/api/admin/cupcakes", "{\"descricao\":\"x\",\"preco\":5}", a).status());
        assertEquals(400, http.post("/api/admin/cupcakes", "{\"nome\":\"X\",\"descricao\":\"x\",\"preco\":5,\"estoque\":-1}", a).status());
        Resposta dup = http.post("/api/admin/cupcakes", cupcakeJson(BRIGADEIRO, "5"), a);
        assertEquals(400, dup.status());
        assertFalse(dup.texto("erro").contains("duplicate key"), "mensagem deve estar em português: " + dup.texto("erro"));
        assertEquals(5, http.get("/api/admin/cupcakes", a).contar("\"nome\":"));
    }

    @Test @DisplayName("[US13, RN03] Dono edita preço e estoque (detalhes mostram o dado novo); desativar tira da vitrine")
    void editarProduto() throws Exception {
        String a = admin();
        long id = idCupcake(BAUNILHA);
        Resposta r = http.put("/api/admin/cupcakes/" + id, cupcakeJson(BAUNILHA, "8.9").replace("\"estoque\":10", "\"estoque\":77"), a);
        assertEquals(200, r.status());
        assertEquals(77, contar("SELECT estoque FROM cupcake WHERE id = ?", id));
        assertTrue(http.get("/api/cupcakes/" + id, null).corpo().contains("8.90"));

        http.put("/api/admin/cupcakes/" + id, cupcakeJson(BAUNILHA, "8.9").replace("}", ",\"ativo\":false}"), a);
        assertEquals(404, http.get("/api/cupcakes/" + id, null).status());
        assertEquals(4, http.get("/api/cupcakes", null).contar("\"nome\":"));
        assertEquals(5, http.get("/api/admin/cupcakes", a).contar("\"nome\":"));
    }

    @Test @DisplayName("[US13, RN13] Editar produto inexistente devolve 400")
    void editarInexistente() {
        assertEquals(400, http.put("/api/admin/cupcakes/99999", cupcakeJson("Fantasma", "5"), admin()).status());
    }

    @Test @DisplayName("[US14, RN14] Dono muda o status seguindo o fluxo e é barrado ao pular etapas")
    void statusPedido() throws Exception {
        String c = maria(), a = admin();
        long id = fazerPedido(c);
        http.post("/api/pedidos/" + id + "/pagamento", "{\"metodo\":\"PIX\"}", c);

        Resposta pular = http.put("/api/admin/pedidos/" + id + "/status", "{\"status\":\"ENTREGUE\"}", a);
        assertEquals(400, pular.status());
        assertTrue(pular.texto("erro").contains("Transição de status inválida"));

        assertEquals(200, http.put("/api/admin/pedidos/" + id + "/status", "{\"status\":\"EM_PREPARO\"}", a).status());
        assertEquals(200, http.put("/api/admin/pedidos/" + id + "/status", "{\"status\":\"ENVIADO\"}", a).status());
        assertTrue(http.get("/api/pedidos", c).corpo().contains("\"status\":\"ENVIADO\""));
        assertTrue(http.get("/api/admin/pedidos", a).corpo().contains("Maria Silva"));
    }

    @Test @DisplayName("[US14, RN11] Status inexistente, ausente ou pedido inexistente devolvem 400")
    void statusInvalido() throws Exception {
        String a = admin();
        long id = fazerPedido(maria());
        assertEquals(400, http.put("/api/admin/pedidos/" + id + "/status", "{\"status\":\"XYZ\"}", a).status());
        assertEquals(400, http.put("/api/admin/pedidos/" + id + "/status", "{}", a).status());
        assertEquals(400, http.put("/api/admin/pedidos/99999/status", "{\"status\":\"PAGO\"}", a).status());
    }

    @Test @DisplayName("[US15] Dashboard devolve vendas, mais vendidos e estoque")
    void dashboard() throws Exception {
        String c = maria();
        long id = fazerPedido(c);
        http.post("/api/pedidos/" + id + "/pagamento", "{\"metodo\":\"PIX\"}", c);
        Resposta r = http.get("/api/admin/dashboard", admin());
        assertEquals(200, r.status());
        assertTrue(r.corpo().contains("\"vendasDiarias\":[{"));
        assertTrue(r.corpo().contains("\"faturamento\":35.00"));
        assertTrue(r.corpo().contains("\"maisVendidos\":[{"));
        assertTrue(r.corpo().contains("BAIXO"));
    }

    // ---------------- Desempenho ----------------
    @Test @DisplayName("[RNF01, RNF02] Vitrine e filtro respondem em menos de 1 segundo")
    void desempenho() {
        http.get("/api/cupcakes", null);                       // aquecimento (primeira chamada abre a conexão)
        long ini = System.nanoTime();
        assertEquals(200, http.get("/api/cupcakes", null).status());
        long vitrineMs = (System.nanoTime() - ini) / 1_000_000;
        ini = System.nanoTime();
        assertEquals(200, http.get("/api/cupcakes?categoria=2", null).status());
        long filtroMs = (System.nanoTime() - ini) / 1_000_000;
        assertTrue(vitrineMs < 1000, "vitrine levou " + vitrineMs + " ms");
        assertTrue(filtroMs < 1000, "filtro levou " + filtroMs + " ms");
    }

    // ---------------- Robustez ----------------
    @Test @DisplayName("[ROBUSTEZ] JSON malformado devolve 400 (e não erro 500)")
    void jsonMalformado() {
        for (String ruim : new String[] {"{\"email\":", "{\"email\" \"x\"}", "isto não é json", "{\"email\":\"x"}) {
            Resposta r = http.post("/api/login", ruim, null);
            assertEquals(400, r.status(), "corpo: " + ruim + " -> " + r.corpo());
        }
    }

    @Test @DisplayName("[ROBUSTEZ] Método HTTP não permitido devolve 405; rota inexistente devolve 404")
    void metodosERotas() {
        assertEquals(405, http.post("/api/cupcakes", "{}", null).status());
        assertEquals(405, http.delete("/api/cupcakes/1", null).status());
        assertEquals(404, http.get("/api/rota-que-nao-existe", maria()).status());
        assertEquals(404, http.get("/api/admin/outracoisa", admin()).status());
    }

    @Test @DisplayName("[ROBUSTEZ] Texto com aspas e acentos no nome do produto volta íntegro no JSON")
    void textoEspecial() throws Exception {
        String a = admin();
        String nome = "Cupcake \\\"Coração\\\" <b>";
        assertEquals(201, http.post("/api/admin/cupcakes", cupcakeJson(nome, "7"), a).status());
        Resposta r = http.get("/api/cupcakes", null);
        assertEquals(200, r.status());
        assertTrue(r.corpo().contains("Coração"));
        assertEquals(1, contar("SELECT count(*) FROM cupcake WHERE nome = ?", "Cupcake \"Coração\" <b>"));
    }

    @Test @DisplayName("[VIEW] Páginas estáticas são servidas; fora da pasta public dá 404")
    void arquivosEstaticos() {
        Resposta home = http.get("/", null);
        assertEquals(200, home.status());
        assertTrue(home.corpo().contains("Loja de Cupcakes"));
        assertEquals(200, http.get("/admin.html", null).status());
        assertEquals(200, http.get("/app.js", null).status());
        assertEquals(404, http.get("/nao-existe.html", null).status());
        assertEquals(404, http.get("/%2e%2e/sql/schema_loja_cupcakes.sql", null).status());
        assertEquals(404, http.get("/%2e%2e/src/loja/Main.java", null).status());
    }
}
