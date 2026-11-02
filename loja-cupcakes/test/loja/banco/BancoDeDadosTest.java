package loja.banco;

import static loja.BancoDeTestes.*;
import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.sql.*;
import loja.BancoDeTestes;
import loja.dao.CarrinhoDao;
import loja.dao.PedidoDao;
import loja.util.Db;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Testes das regras que ficam no próprio banco (restrições, índices únicos, triggers, views e permissões). */
class BancoDeDadosTest {

    @BeforeEach void banco() throws Exception { BancoDeTestes.preparar(); }

    private static String estadoDoErro(String sql, Object... args) {
        SQLException e = assertThrows(SQLException.class, () -> executar(sql, args));
        return e.getSQLState();
    }

    @Test @DisplayName("[ESQUEMA] Administrador não pode ser cadastrado como cliente")
    void adminNaoEhCliente() throws Exception {
        assertEquals("23514", estadoDoErro("INSERT INTO cliente (usuario_id) VALUES (?)", idUsuario(ADMIN)));
    }

    @Test @DisplayName("[US04, ESQUEMA] Um cliente só pode ter um carrinho aberto")
    void umCarrinhoAberto() throws Exception {
        long maria = idUsuario(MARIA);
        executar("INSERT INTO carrinho (cliente_id) VALUES (?)", maria);
        assertEquals("23505", estadoDoErro("INSERT INTO carrinho (cliente_id) VALUES (?)", maria));
        // depois de convertido, um novo carrinho aberto é permitido
        executar("UPDATE carrinho SET status = 'CONVERTIDO' WHERE cliente_id = ?", maria);
        assertDoesNotThrow(() -> executar("INSERT INTO carrinho (cliente_id) VALUES (?)", maria));
    }

    @Test @DisplayName("[US07, ESQUEMA] Só um endereço principal ativo por cliente")
    void umEnderecoPrincipal() throws Exception {
        long maria = idUsuario(MARIA);   // já tem um endereço principal nos dados iniciais
        assertEquals("23505", estadoDoErro(
            "INSERT INTO endereco (cliente_id, cep, logradouro, numero, bairro, cidade, uf, principal) " +
            "VALUES (?, '74000000', 'Rua B', '2', 'Centro', 'Goiânia', 'GO', TRUE)", maria));
    }

    @Test @DisplayName("[US10, ESQUEMA] Um pedido não pode ter dois pagamentos aprovados")
    void umPagamentoAprovado() throws Exception {
        long maria = idUsuario(MARIA);
        new CarrinhoDao().adicionar(maria, idCupcake(BRIGADEIRO), 1);
        long pedido = new PedidoDao().confirmar(maria, contar("SELECT id FROM endereco WHERE cliente_id = ?", maria), BigDecimal.ZERO);
        String ins = "INSERT INTO pagamento (pedido_id, metodo, status, valor, processado_em) VALUES (?, 'PIX', 'APROVADO', 9.50, now())";
        executar(ins, pedido);
        assertEquals("23505", estadoDoErro(ins, pedido));
    }

    @Test @DisplayName("[US10, RN10] Pagamento processado precisa ter data de processamento")
    void pagamentoProcessadoComData() throws Exception {
        long maria = idUsuario(MARIA);
        new CarrinhoDao().adicionar(maria, idCupcake(BRIGADEIRO), 1);
        long pedido = new PedidoDao().confirmar(maria, contar("SELECT id FROM endereco WHERE cliente_id = ?", maria), BigDecimal.ZERO);
        assertEquals("23514", estadoDoErro(
            "INSERT INTO pagamento (pedido_id, metodo, status, valor) VALUES (?, 'PIX', 'RECUSADO', 9.50)", pedido));
    }

    @Test @DisplayName("[ESQUEMA] Valores monetários negativos são rejeitados")
    void semDinheiroNegativo() throws Exception {
        assertEquals("23514", estadoDoErro("UPDATE cupcake SET preco = -1 WHERE nome = ?", BRIGADEIRO));
        assertEquals("23514", estadoDoErro("UPDATE cupcake SET estoque = -1 WHERE nome = ?", BRIGADEIRO));
    }

    @Test @DisplayName("[ESQUEMA] Nome de categoria é único")
    void categoriaUnica() throws Exception {
        assertEquals("23505", estadoDoErro("INSERT INTO categoria (nome) VALUES ('Vegano')"));
    }

    @Test @DisplayName("[ESQUEMA] Produto com pedidos não pode ser apagado")
    void produtoComPedidoNaoApaga() throws Exception {
        long maria = idUsuario(MARIA);
        new CarrinhoDao().adicionar(maria, idCupcake(BRIGADEIRO), 1);
        new PedidoDao().confirmar(maria, contar("SELECT id FROM endereco WHERE cliente_id = ?", maria), BigDecimal.ZERO);
        assertEquals("23503", estadoDoErro("DELETE FROM cupcake WHERE nome = ?", BRIGADEIRO));
    }

    @Test @DisplayName("[ESQUEMA] Subtotal do item do pedido é calculado pelo banco")
    void subtotalGerado() throws Exception {
        long maria = idUsuario(MARIA);
        new CarrinhoDao().adicionar(maria, idCupcake(BRIGADEIRO), 3);
        long pedido = new PedidoDao().confirmar(maria, contar("SELECT id FROM endereco WHERE cliente_id = ?", maria), BigDecimal.ZERO);
        BigDecimal subtotal = (BigDecimal) valor("SELECT subtotal FROM item_pedido WHERE pedido_id = ?", pedido);
        assertEquals(0, new BigDecimal("28.50").compareTo(subtotal));
        // a coluna é gerada: gravar um valor direto deve ser recusado
        assertEquals("428C9", estadoDoErro("UPDATE item_pedido SET subtotal = 1 WHERE pedido_id = ?", pedido));
    }

    @Test @DisplayName("[RN14] Tabela de transições tem as 7 regras do fluxo")
    void transicoes() throws Exception {
        assertEquals(7, contar("SELECT count(*) FROM transicao_status_pedido"));
        assertEquals(0, contar("SELECT count(*) FROM transicao_status_pedido WHERE de = 'ENTREGUE'"));
        assertEquals(0, contar("SELECT count(*) FROM transicao_status_pedido WHERE de = 'CANCELADO'"));
    }

    @Test @DisplayName("[US01, US15] Views de vitrine e estoque respondem com os dados iniciais")
    void views() throws Exception {
        assertEquals(5, contar("SELECT count(*) FROM vw_vitrine"));
        assertEquals(5, contar("SELECT count(*) FROM vw_estoque"));
        assertEquals(0, contar("SELECT count(*) FROM vw_vendas_diarias"));
    }

    @Test @DisplayName("[RNF08] Papel da aplicação não pode apagar pedidos nem pagamentos")
    void permissoes() throws Exception {
        try (Connection c = Db.conectar(); Statement st = c.createStatement()) {
            st.execute("SET ROLE app_loja");
            for (String tabela : new String[] {"pedido", "item_pedido", "pagamento", "historico_status_pedido"}) {
                SQLException e = assertThrows(SQLException.class, () -> st.executeUpdate("DELETE FROM " + tabela),
                        "DELETE deveria ser negado em " + tabela);
                assertEquals("42501", e.getSQLState());
            }
            assertDoesNotThrow(() -> st.executeQuery("SELECT * FROM pedido").close());   // leitura continua permitida
        }
    }

    @Test @DisplayName("[ACESSO] Senhas de demonstração estão guardadas como hash")
    void senhasComoHash() throws Exception {
        assertEquals(0, contar("SELECT count(*) FROM usuario WHERE length(senha_hash) <> 64"));
    }
}
