package loja.dao;

import static loja.BancoDeTestes.*;
import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import loja.BancoDeTestes;
import loja.model.Pedido;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PedidoDaoTest {
    private final PedidoDao pedidos = new PedidoDao();
    private final CarrinhoDao carrinho = new CarrinhoDao();
    private static final BigDecimal TAXA = new BigDecimal("5.00");
    private long maria, joao, admin, enderecoMaria;

    @BeforeEach void banco() throws Exception {
        BancoDeTestes.preparar();
        maria = idUsuario(MARIA);
        joao = idUsuario(JOAO);
        admin = idUsuario(ADMIN);
        enderecoMaria = contar("SELECT id FROM endereco WHERE cliente_id = ?", maria);
    }

    /** Carrinho da Maria: 2 Brigadeiro (19,00) + 1 Vegano (11,00) = 30,00 */
    private long pedidoDaMaria() throws Exception {
        carrinho.adicionar(maria, idCupcake(BRIGADEIRO), 2);
        carrinho.adicionar(maria, idCupcake(VEGANO), 1);
        return pedidos.confirmar(maria, enderecoMaria, TAXA);
    }

    private static void assertValor(String esperado, Object real) {
        assertEquals(0, new BigDecimal(esperado).compareTo((BigDecimal) real), "esperado " + esperado + " mas foi " + real);
    }

    // ---------------- US09: confirmar pedido ----------------
    @Test @DisplayName("[US09] Confirmar cria pedido aguardando pagamento com total = itens + taxa")
    void confirmar() throws Exception {
        long id = pedidoDaMaria();
        assertEquals("AGUARDANDO_PAGAMENTO", valor("SELECT status FROM pedido WHERE id = ?", id).toString());
        assertValor("35.00", valor("SELECT valor_total FROM pedido WHERE id = ?", id));
        assertValor("5.00", valor("SELECT taxa_entrega FROM pedido WHERE id = ?", id));
        assertEquals(2, contar("SELECT count(*) FROM item_pedido WHERE pedido_id = ?", id));
    }

    @Test @DisplayName("[US09] Confirmar baixa o estoque dos produtos")
    void baixaEstoque() throws Exception {
        pedidoDaMaria();
        assertEquals(28, contar("SELECT estoque FROM cupcake WHERE nome = ?", BRIGADEIRO));
        assertEquals(19, contar("SELECT estoque FROM cupcake WHERE nome = ?", VEGANO));
        assertEquals(15, contar("SELECT estoque FROM cupcake WHERE nome = ?", MORANGO));   // intacto
    }

    @Test @DisplayName("[US09] Confirmar fecha o carrinho e esvazia a lista")
    void fechaCarrinho() throws Exception {
        pedidoDaMaria();
        assertEquals("CONVERTIDO", valor("SELECT status FROM carrinho WHERE cliente_id = ?", maria).toString());
        assertTrue(carrinho.listar(maria).isEmpty());
    }

    @Test @DisplayName("[US09] Depois de confirmar, o cliente pode montar um novo carrinho")
    void novoCarrinho() throws Exception {
        pedidoDaMaria();
        carrinho.adicionar(maria, idCupcake(BAUNILHA), 1);
        assertEquals(1, carrinho.listar(maria).size());
        assertEquals(2, contar("SELECT count(*) FROM carrinho WHERE cliente_id = ?", maria));
    }

    @Test @DisplayName("[US09] Preço do item fica gravado: mudar o preço depois não altera o pedido")
    void precoCongelado() throws Exception {
        long id = pedidoDaMaria();
        executar("UPDATE cupcake SET preco = 99.00 WHERE nome = ?", BRIGADEIRO);
        assertValor("9.50", valor("SELECT preco_unitario FROM item_pedido WHERE pedido_id = ? AND cupcake_id = ?", id, idCupcake(BRIGADEIRO)));
        assertValor("35.00", valor("SELECT valor_total FROM pedido WHERE id = ?", id));
    }

    @Test @DisplayName("[US09] Endereço de entrega é copiado para o pedido")
    void copiaEndereco() throws Exception {
        long id = pedidoDaMaria();
        executar("UPDATE endereco SET logradouro = 'Rua Nova' WHERE id = ?", enderecoMaria);
        assertEquals("Rua das Flores", valor("SELECT entrega_logradouro FROM pedido WHERE id = ?", id));
    }

    @Test @DisplayName("[US09, RN09] Sem carrinho aberto não é possível confirmar")
    void semCarrinho() {
        SQLException e = assertThrows(SQLException.class, () -> pedidos.confirmar(maria, enderecoMaria, TAXA));
        assertTrue(e.getMessage().contains("carrinho"));
    }

    @Test @DisplayName("[US09, RN09] Carrinho vazio não gera pedido")
    void carrinhoVazio() throws Exception {
        carrinho.adicionar(maria, idCupcake(BRIGADEIRO), 1);
        carrinho.remover(maria, idCupcake(BRIGADEIRO));
        assertThrows(SQLException.class, () -> pedidos.confirmar(maria, enderecoMaria, TAXA));
        assertEquals(0, contar("SELECT count(*) FROM pedido"));
    }

    @Test @DisplayName("[US09, RN09] Estoque insuficiente impede o pedido e nada é alterado")
    void estoqueInsuficiente() throws Exception {
        carrinho.adicionar(maria, idCupcake(LIMAO), 5);          // estoque = 4
        SQLException e = assertThrows(SQLException.class, () -> pedidos.confirmar(maria, enderecoMaria, TAXA));
        assertTrue(e.getMessage().contains("estoque"));
        assertEquals(4, contar("SELECT estoque FROM cupcake WHERE nome = ?", LIMAO));
        assertEquals("ABERTO", valor("SELECT status FROM carrinho WHERE cliente_id = ?", maria).toString());
        assertEquals(0, contar("SELECT count(*) FROM pedido"));
    }

    @Test @DisplayName("[US09, RN09] Produto inativado depois de entrar no carrinho impede o pedido")
    void produtoInativadoNoCarrinho() throws Exception {
        carrinho.adicionar(maria, idCupcake(MORANGO), 1);
        executar("UPDATE cupcake SET ativo = FALSE WHERE nome = ?", MORANGO);
        assertThrows(SQLException.class, () -> pedidos.confirmar(maria, enderecoMaria, TAXA));
    }

    @Test @DisplayName("[US09, RN07] Endereço de outro cliente ou inexistente é recusado")
    void enderecoInvalido() throws Exception {
        carrinho.adicionar(joao, idCupcake(BRIGADEIRO), 1);
        assertThrows(SQLException.class, () -> pedidos.confirmar(joao, enderecoMaria, TAXA));
        assertThrows(SQLException.class, () -> pedidos.confirmar(joao, 99999, TAXA));
        assertEquals(30, contar("SELECT estoque FROM cupcake WHERE nome = ?", BRIGADEIRO));
    }

    @Test @DisplayName("[US11] Pedido nasce com o status inicial registrado no histórico")
    void historicoInicial() throws Exception {
        long id = pedidoDaMaria();
        assertEquals(1, contar("SELECT count(*) FROM historico_status_pedido WHERE pedido_id = ?", id));
        assertEquals("AGUARDANDO_PAGAMENTO",
                valor("SELECT status_novo FROM historico_status_pedido WHERE pedido_id = ?", id).toString());
    }

    // ---------------- US08/US10: pagamento ----------------
    @Test @DisplayName("[US10, RN10] Pagamento aprovado muda o pedido para PAGO")
    void pagamentoAprovado() throws Exception {
        long id = pedidoDaMaria();
        assertEquals("APROVADO", pedidos.pagar(maria, id, "PIX", false));
        assertEquals("PAGO", valor("SELECT status FROM pedido WHERE id = ?", id).toString());
        assertValor("35.00", valor("SELECT valor FROM pagamento WHERE pedido_id = ?", id));
        assertNotNull(valor("SELECT gateway_transacao_id FROM pagamento WHERE pedido_id = ?", id));
    }

    @Test @DisplayName("[US10] Pagamento recusado mantém o pedido aguardando e permite nova tentativa")
    void pagamentoRecusadoDepoisAprovado() throws Exception {
        long id = pedidoDaMaria();
        assertEquals("RECUSADO", pedidos.pagar(maria, id, "CARTAO", true));
        assertEquals("AGUARDANDO_PAGAMENTO", valor("SELECT status FROM pedido WHERE id = ?", id).toString());
        assertEquals("APROVADO", pedidos.pagar(maria, id, "PIX", false));
        assertEquals("PAGO", valor("SELECT status FROM pedido WHERE id = ?", id).toString());
        assertEquals(2, contar("SELECT count(*) FROM pagamento WHERE pedido_id = ?", id));
    }

    @Test @DisplayName("[US10] Não é possível pagar um pedido que já foi pago")
    void pagarDuas() throws Exception {
        long id = pedidoDaMaria();
        pedidos.pagar(maria, id, "PIX", false);
        assertThrows(IllegalArgumentException.class, () -> pedidos.pagar(maria, id, "PIX", false));
        assertEquals(1, contar("SELECT count(*) FROM pagamento WHERE pedido_id = ?", id));
    }

    @Test @DisplayName("[US10] Cliente não consegue pagar o pedido de outro cliente")
    void pagarPedidoAlheio() throws Exception {
        long id = pedidoDaMaria();
        assertThrows(IllegalArgumentException.class, () -> pedidos.pagar(joao, id, "PIX", false));
        assertEquals("AGUARDANDO_PAGAMENTO", valor("SELECT status FROM pedido WHERE id = ?", id).toString());
    }

    @Test @DisplayName("[US08, RN08] Forma de pagamento inválida é rejeitada e nada é gravado")
    void metodoInvalido() throws Exception {
        long id = pedidoDaMaria();
        SQLException e = assertThrows(SQLException.class, () -> pedidos.pagar(maria, id, "BOLETO", false));
        assertEquals("22P02", e.getSQLState());
        assertEquals(0, contar("SELECT count(*) FROM pagamento WHERE pedido_id = ?", id));
    }

    // ---------------- US14: status do pedido ----------------
    @Test @DisplayName("[US14, RN14] Fluxo completo de status: PAGO, EM_PREPARO, ENVIADO e ENTREGUE")
    void fluxoCompleto() throws Exception {
        long id = pedidoDaMaria();
        pedidos.pagar(maria, id, "PIX", false);
        for (String s : new String[] {"EM_PREPARO", "ENVIADO", "ENTREGUE"}) {
            pedidos.atualizarStatus(admin, id, s);
            assertEquals(s, valor("SELECT status FROM pedido WHERE id = ?", id).toString());
        }
    }

    @Test @DisplayName("[US14, RN14] Pular etapa do fluxo é rejeitado")
    void pularEtapa() throws Exception {
        long id = pedidoDaMaria();
        pedidos.pagar(maria, id, "PIX", false);
        SQLException e = assertThrows(SQLException.class, () -> pedidos.atualizarStatus(admin, id, "ENTREGUE"));
        assertTrue(e.getMessage().contains("Transição de status inválida"));
        assertEquals("PAGO", valor("SELECT status FROM pedido WHERE id = ?", id).toString());
    }

    @Test @DisplayName("[US14, RN14] Pedido entregue não pode mais mudar de status")
    void entregueEhFinal() throws Exception {
        long id = pedidoDaMaria();
        pedidos.pagar(maria, id, "PIX", false);
        for (String s : new String[] {"EM_PREPARO", "ENVIADO", "ENTREGUE"}) pedidos.atualizarStatus(admin, id, s);
        assertThrows(SQLException.class, () -> pedidos.atualizarStatus(admin, id, "CANCELADO"));
        assertThrows(SQLException.class, () -> pedidos.atualizarStatus(admin, id, "PAGO"));
    }

    @Test @DisplayName("[US14, RN14] Pedido pode ser cancelado enquanto aguarda pagamento")
    void cancelarAguardando() throws Exception {
        long id = pedidoDaMaria();
        pedidos.atualizarStatus(admin, id, "CANCELADO");
        assertEquals("CANCELADO", valor("SELECT status FROM pedido WHERE id = ?", id).toString());
    }

    @Test @DisplayName("[US14] Histórico guarda quem alterou o status")
    void historicoQuemAlterou() throws Exception {
        long id = pedidoDaMaria();
        pedidos.pagar(maria, id, "PIX", false);
        pedidos.atualizarStatus(admin, id, "EM_PREPARO");
        assertEquals(admin, contar("SELECT alterado_por FROM historico_status_pedido WHERE pedido_id = ? AND status_novo = 'EM_PREPARO'", id));
        assertEquals(3, contar("SELECT count(*) FROM historico_status_pedido WHERE pedido_id = ?", id));
    }

    @Test @DisplayName("[US14, RN11] Status inexistente e pedido inexistente geram erro")
    void statusEPedidoInvalidos() throws Exception {
        long id = pedidoDaMaria();
        SQLException e = assertThrows(SQLException.class, () -> pedidos.atualizarStatus(admin, id, "XYZ"));
        assertEquals("22P02", e.getSQLState());
        assertThrows(IllegalArgumentException.class, () -> pedidos.atualizarStatus(admin, 99999, "PAGO"));
    }

    // ---------------- US11: acompanhar ----------------
    @Test @DisplayName("[US11] Cliente vê apenas os seus pedidos, com status e pagamento")
    void listarPedidosDoCliente() throws Exception {
        long idMaria = pedidoDaMaria();
        carrinho.adicionar(joao, idCupcake(BAUNILHA), 1);
        long enderecoJoao = pedidos.confirmar(joao, inserirEnderecoJoao(), TAXA);
        pedidos.pagar(maria, idMaria, "PIX", false);

        List<Pedido> deMaria = pedidos.listarDoCliente(maria);
        assertEquals(1, deMaria.size());
        assertEquals("PAGO", deMaria.get(0).status());
        assertEquals("APROVADO", deMaria.get(0).statusPagamento());
        assertEquals(1, pedidos.listarDoCliente(joao).size());
        assertNull(pedidos.listarDoCliente(joao).get(0).statusPagamento());
        assertEquals(2, pedidos.listarTodos().size());
        assertTrue(enderecoJoao > 0);
    }

    private long inserirEnderecoJoao() throws Exception {
        return new EnderecoDao().inserir(joao, "Casa", "74110010", "Av. Goiás", "500", null, "Centro", "Goiânia", "GO");
    }

    @Test @DisplayName("[US11] Pedidos aparecem do mais novo para o mais antigo")
    void ordemPedidos() throws Exception {
        long primeiro = pedidoDaMaria();
        carrinho.adicionar(maria, idCupcake(BAUNILHA), 1);
        long segundo = pedidos.confirmar(maria, enderecoMaria, TAXA);
        List<Pedido> lista = pedidos.listarDoCliente(maria);
        assertEquals(segundo, lista.get(0).id());
        assertEquals(primeiro, lista.get(1).id());
    }

    // ---------------- US15: dashboard ----------------
    @SuppressWarnings("unchecked")
    @Test @DisplayName("[US15, RN15] Vendas só contam pedidos já pagos e refletem o dado atual")
    void dashboardVendas() throws Exception {
        long id = pedidoDaMaria();
        assertTrue(((List<?>) pedidos.dashboard().get("vendasDiarias")).isEmpty());   // ainda não pago
        pedidos.pagar(maria, id, "PIX", false);
        List<Map<String, Object>> vendas = (List<Map<String, Object>>) pedidos.dashboard().get("vendasDiarias");
        assertEquals(1, vendas.size());
        assertEquals(1L, ((Number) vendas.get(0).get("pedidos")).longValue());
        assertValor("35.00", vendas.get(0).get("faturamento"));
    }

    @SuppressWarnings("unchecked")
    @Test @DisplayName("[US15] Pedido cancelado não entra nas vendas")
    void dashboardIgnoraCancelados() throws Exception {
        long id = pedidoDaMaria();
        pedidos.pagar(maria, id, "PIX", false);
        pedidos.atualizarStatus(admin, id, "CANCELADO");
        assertTrue(((List<?>) pedidos.dashboard().get("vendasDiarias")).isEmpty());
    }

    @SuppressWarnings("unchecked")
    @Test @DisplayName("[US15] Mais vendidos vem ordenado por unidades")
    void dashboardMaisVendidos() throws Exception {
        long id = pedidoDaMaria();
        pedidos.pagar(maria, id, "PIX", false);
        List<Map<String, Object>> top = (List<Map<String, Object>>) pedidos.dashboard().get("maisVendidos");
        assertEquals(BRIGADEIRO, top.get(0).get("nome"));
        assertEquals(2L, ((Number) top.get(0).get("unidades")).longValue());
        assertEquals(VEGANO, top.get(1).get("nome"));
    }

    @SuppressWarnings("unchecked")
    @Test @DisplayName("[US15] Relatório de estoque classifica BAIXO, OK e ESGOTADO")
    void dashboardEstoque() throws Exception {
        executar("UPDATE cupcake SET estoque = 0 WHERE nome = ?", MORANGO);
        List<Map<String, Object>> est = (List<Map<String, Object>>) pedidos.dashboard().get("estoque");
        Map<String, Object> porNome = new java.util.HashMap<>();
        for (Map<String, Object> l : est) porNome.put((String) l.get("nome"), l.get("situacao"));
        assertEquals("BAIXO", porNome.get(LIMAO));       // 4 unidades
        assertEquals("ESGOTADO", porNome.get(MORANGO));  // 0 unidades
        assertEquals("OK", porNome.get(BAUNILHA));       // 40 unidades
    }
}
